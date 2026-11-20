# Hướng dẫn khởi động KMA IdP — Backend, Frontend và Java Card Simulator

## Khởi động local đã kiểm chứng — 20/11/2026

BE đã khởi động thành công và được kiểm tra qua HTTP với MySQL thật. Cổng 8080 đang bận, vì vậy dùng 18080 cho lần demo này. BE do agent bật đã được dừng sau test theo yêu cầu; MySQL vẫn cần chạy khi bạn tự khởi động.

PowerShell 1, tại root workspace:

```powershell
cd D:\ATBM-SYSTEM
.\backend\run.ps1 -UseCachedRuntime -Port 18080
```

Chờ `Started IdpApplication`. PowerShell 2:

```powershell
cd D:\ATBM-SYSTEM
.\frontend\run.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

Nếu FE đang mở, nhập Backend `http://127.0.0.1:18080` rồi bấm Kết nối. Đã quan sát FE báo `Backend: UP` với BE chạy thật. Khi muốn dừng BE, Ctrl+C ở cửa sổ BE. Không cần dừng MySQL hoặc xóa container.

Kết quả thực thi:

- 173 file main biên dịch PASS trong output riêng tránh class lỗi do IDE.
- MySQL 8.4 kết nối bằng user ứng dụng, 4 migration Flyway validate PASS, JPA khởi tạo thành công.
- 8/8 HTTP smoke PASS: status, readiness DB, chặn anonymous, requestId/no-store, public JWKS, validation 400, bearer sai 401, đăng nhập admin bước đầu 200.
- 4 kiểm tra WebAuthn startup/CBOR và 3 kiểm tra readiness khi DB UP/DOWN/exception PASS.
- Không ghi password/token vào báo cáo. Báo cáo: `backend/docs/test-evidence/local-startup-latest.json`, `local-http-smoke-latest.json`, `webauthn-startup-latest.json`, `readiness-checks-latest.json` (trong repo backend bỏ tiền tố backend).

Chạy lại smoke (chỉ khi BE đang chạy): `python backend/scripts/smoke_local_http.py --port 18080 --check-admin`. Script hỏi mật khẩu bằng secure input và chỉ ghi kết quả an toàn.

Bản cache dùng thư viện tương thích cũ: Flyway cảnh báo MySQL 8.4 ngoài bản đã kiểm thử của thư viện; chưa thay thế build Maven đã pin. Bản cache không có Actuator health, readiness DB dùng `/api/v1/health`. Toàn bộ regression nghiệp vụ, Oracle Simulator và thẻ thật chưa được xác minh ở lượt startup này. Các báo cáo 241 BE/28 tích hợp là lịch sử.


<!-- CURRENT_VERIFICATION_START -->
## Cập nhật rà soát ngày 20/11/2026

- FE có **11 tab, 57 định nghĩa kịch bản**. Mỗi tab hiển thị mã case, thao tác và kết quả kỳ vọng; không tự gắn PASS cho hướng dẫn.
- FE: **25 kiểm tra API**, **37 kiểm tra thông báo lỗi/PIN**, **30 kiểm tra tab/case/phân quyền nút**, **46 kiểm tra luồng Swing/API/APDU giả lập** và smoke giao diện đều PASS.
- BE: startup MySQL thật và **8 HTTP smoke PASS**; **173 file main** biên dịch PASS. 56 kiểm tra exception trước đó là bằng chứng nguồn cũ, chưa chạy lại ở lượt startup này.
- Rà soát UI mới nhất: `frontend/docs/UI_REGRESSION_REVIEW.md`; đã sửa dữ liệu cũ sau logout/đổi BE, kết quả request muộn, gán nhầm thẻ và tải lại trạng thái sau thu hồi phiên.
- Các lỗi có `code` và `requestId`; FE hiển thị thông báo ngắn, chỉ rõ trường cần sửa khi có thể, không đưa raw exception/SQL/token vào dialog hoặc activity log.
- Java Card CAP/installer đã build thành công (converter 26.0: 0 lỗi, 0 cảnh báo); native simulator/thẻ thật chưa được xác minh.
- Người dùng đã cho phép đích telemetry, BE startup và HTTP smoke hiện tại PASS. Full regression BE nghiệp vụ và FE–BE chưa chạy lại; các kết quả 241 BE / 28 tích hợp trước đây vẫn là lịch sử.

Chi tiết theo tab: `frontend/docs/DEMO_CASES.md`. Danh mục chờ chạy: `backend/docs/PENDING_VERIFICATION_CASES.md`. Bằng chứng JSON: `frontend/docs/test-evidence/` và `backend/docs/test-evidence/`.
<!-- CURRENT_VERIFICATION_END -->


## 1. Mục tiêu và các thành phần

Project nằm tại `D:\ATBM-SYSTEM`. Frontend là **ứng dụng desktop Java**, không phải website.

| Thành phần | Vai trò | Cổng / vị trí |
|---|---|---|
| MySQL | Lưu người dùng, credential, session, audit và cấu hình IdP | `127.0.0.1:3306`, schema `kma_idp` |
| Backend | API, kiểm tra chữ ký, trạng thái tài khoản, phân quyền | `http://127.0.0.1:8080` |
| Oracle Simulator | Giả lập thẻ chứa khóa riêng và PIN | `localhost:9025` |
| Frontend | Giao diện quản trị và thử đăng nhập bằng thẻ | Ứng dụng desktop |
| Applet | Nhận APDU, kiểm tra PIN, ký challenge | AID `F04B4D4149445001` |

Ba file Markdown gốc được giữ nguyên. Schema gồm 41 bảng nghiệp vụ; không phải tất cả use case OIDC/MFA trong tài liệu đã được triển khai. Xem `backend/docs/REQUIREMENT_COVERAGE.md` để biết phần chạy được và phần còn khung dữ liệu.

## 2. Kiểm tra môi trường

Mở PowerShell thông thường trên laptop, sau đó:

```powershell
Set-Location D:\ATBM-SYSTEM
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:JC_HOME_TOOLS = 'D:\SmartCard\tools'
$env:JC_HOME_SIMULATOR = 'D:\SmartCard\simulator'

& "$env:JAVA_HOME\bin\java.exe" -version
mvn.cmd -version
```

Java phải báo 21. Maven phải dùng đúng JDK ở `JAVA_HOME`.

Kiểm tra file SDK:

```powershell
Test-Path "$env:JC_HOME_TOOLS\lib\api_classic-3.2.0.jar"
Test-Path "$env:JC_HOME_SIMULATOR\runtime\bin\jcsw.exe"
Test-Path "$env:JC_HOME_SIMULATOR\client\COMService\socketprovider.jar"
Test-Path "$env:JC_HOME_SIMULATOR\client\AMService\amservice.jar"
```

Tất cả cần trả `True`. Nếu SDK của bạn ở chỗ khác, sửa hai biến môi trường tương ứng. Các script có đường dẫn mặc định như trên, nhưng ưu tiên biến môi trường bạn cung cấp.

## 3. Build và chạy kiểm thử

### 3.1. Cách chuẩn khi Maven tải được thư viện

Tại thư mục `D:\ATBM-SYSTEM`:

```powershell
.\backend\build.ps1
.\frontend\build.ps1
```

Script lần lượt chạy:

1. Backend `mvn verify`: biên dịch và chạy kiểm thử tích hợp.
2. Frontend `mvn verify`: biên dịch và đóng gói, chép thư viện runtime.
3. Biên dịch applet và chuyển thành CAP, biên dịch công cụ cài applet.

Nếu có bước thất bại, script dừng ngay. Không tiếp tục chạy BE/FE từ một build lỗi.

Kết quả chính:

```text
backend/target/idp-backend-0.1.0.jar
frontend/target/classes/
frontend/target/dependency/
frontend/card-applet/build/cap/kma/idp/card/javacard/card.cap
frontend/card-applet/build/tools/InstallApplet.class
```

### 3.2. Khi Maven Central không truy cập được

Máy hiện có cache thư viện từ các project Java khác. Có thể chạy kiểm tra tương thích:

```powershell
python .\backend\scripts\check_architecture.py
python .\backend\scripts\offline_verify.py
python .\frontend\scripts\offline_verify.py
.\frontend\card-applet\build.ps1
```

Điều kiện thành công:

- Kiểm tra kiến trúc báo giữ đủ 32 bảng và toàn bộ cột ERD gốc, có 41 entity.
- Cuối bài test có `INTEGRATION CHECKS PASSED`.
- Kiểm tra desktop có `DESKTOP SMOKE CHECKS PASSED`.
- Applet converter báo `0 errors`.

Script offline dùng Spring Boot và dependency đã có trong cache, có thể khác phiên bản được pin trong POM. Đây là chế độ **lab/tương thích**, không phải build phát hành production. Nó tạo class BE/FE và thư viện FE, nhưng không tạo Spring Boot executable JAR.

Log kiểm thử chi tiết: `backend/target/offline-verification.log`. Test dùng H2 trong RAM, không xóa hay ghi dữ liệu vào MySQL của bạn.

## 4. Thiết lập MySQL

### 4.1. Profile trong MySQL Workbench

Profile `KMA IdP Local` đã được thêm trong Workbench với:

| Mục | Giá trị |
|---|---|
| Connection Method | Standard (TCP/IP) |
| Host | `127.0.0.1` |
| Port | `3306` |
| Username quản trị | `root` |
| Default Schema | `kma_idp` |

Profile chỉ là thông tin kết nối để quản trị database. Việc nhìn thấy profile không đồng nghĩa schema đã được tạo hoặc mật khẩu đã được kiểm tra. Nếu mở profile trước khi tạo schema, Workbench có thể báo `Unknown database 'kma_idp'`; thực hiện bước tiếp theo trước.

### 4.2. Tạo schema và tài khoản riêng cho BE

Đảm bảo dịch vụ MySQL đang chạy, phiên bản từ **8.0.16** trở lên để enforce CHECK constraint.

```powershell
Set-Location D:\ATBM-SYSTEM
.\backend\setup-database.ps1
```

Nhập mật khẩu MySQL của `root` trong lời nhắc trên máy. Không ghi mật khẩu vào source và không gửi qua chat.

Script sẽ:

1. Kết nối MySQL local bằng root cho bước thiết lập.
2. Tạo schema `kma_idp` nếu chưa có, dùng `utf8mb4`.
3. Tạo user ngẫu nhiên dạng `kma_idp_<mã>` với mật khẩu ngẫu nhiên, chỉ cấp quyền trong schema này.
4. Lưu credential ứng dụng bằng Windows DPAPI tại `backend/.local/database.credential.xml`.
5. Những lần chạy tiếp theo giữ lại credential đã có; không tự đổi mật khẩu tài khoản đang dùng.

Đây là tài khoản chạy ứng dụng; BE không cần dùng root. File DPAPI chỉ được giải mã bởi tài khoản Windows đã tạo nó; đừng chép file này sang máy khác để dùng như cấu hình portable.

Nếu schema cùng tên đã có bảng không thuộc Flyway, script sẽ dừng thay vì dùng nhầm database. Không tự xóa schema để xử lý lỗi này.

### 4.3. Khi đã có cấu hình kết nối riêng

Có thể bỏ qua setup script bằng cách cung cấp môi trường:

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/kma_idp?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:DB_USERNAME = Read-Host 'MySQL app username'
$dbSecret = Read-Host 'MySQL app password' -AsSecureString
$env:DB_PASSWORD = [PSCredential]::new('database', $dbSecret).GetNetworkCredential().Password
```

Schema và user phải được quản trị viên tạo trước nếu chọn cách này. Không đặt `createDatabaseIfNotExist=true` rồi dùng root làm tài khoản runtime.

## 5. Khởi động Backend

### 5.1. Sau build Maven chuẩn

Mở PowerShell số 1:

```powershell
Set-Location D:\ATBM-SYSTEM
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
.\backend\run.ps1
```

### 5.2. Sau kiểm tra/build tương thích offline

```powershell
Set-Location D:\ATBM-SYSTEM
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
.\backend\run.ps1 -UseCachedRuntime
```

Chế độ này chỉ dành cho local lab, đọc classpath do `offline_verify.py` tạo. Không dùng để triển khai production.

### 5.3. Mật khẩu admin và khởi tạo bảng

Script nạp credential database đã bảo vệ bằng DPAPI, hoặc dùng `DB_*` bạn đã đặt. Sau đó script hỏi mật khẩu bootstrap admin:

- Username: `admin`.
- Mật khẩu: do bạn đặt, 14–72 ký tự.
- Chỉ được dùng để tạo tài khoản admin khi database còn trống.
- Nếu database đã có tài khoản, nhập mật khẩu bootstrap khác sẽ **không đổi** mật khẩu admin đang tồn tại.

Flyway tạo schema trong lần chạy đầu, Hibernate validate mapping entity. BE sẽ dừng nếu migration hoặc mapping không hợp lệ.

Giữ cửa sổ PowerShell này mở khi demo. Backend local bind `127.0.0.1:8080`.

### 5.4. Kiểm tra BE

Mở PowerShell khác:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/status
```

Cần có `status = UP`, `service = KMA Identity`. `/api/v1/me` khi chưa đăng nhập phải bị từ chối; không dùng endpoint cần quyền để kiểm tra health.

Trong Workbench, mở profile `KMA IdP Local`, nhập mật khẩu root nếu được yêu cầu, Refresh Schemas. Có 41 bảng nghiệp vụ và bảng quản lý migration `flyway_schema_history`.

## 6. Khởi động Java Card Simulator

Mở PowerShell số 2:

```powershell
$env:JC_HOME_SIMULATOR = 'D:\SmartCard\simulator'
Set-Location "$env:JC_HOME_SIMULATOR\runtime\bin"
.\jcsw.exe -p=9025 -log_level=info
```

Đợi log cho biết simulator sẵn sàng ở cổng `9025`. Giữ cửa sổ này mở. Cổng mặc định và tham số trên được mô tả trong [tài liệu chính thức Oracle Simulator](https://docs.oracle.com/en/java/javacard/3.2/jcdksu/java-card-development-kit-simulator-command-line.html).

Không cần cài PC/SC driver của simulator cho luồng hiện tại: FE sử dụng socket provider của Oracle khi chọn Simulator. Chế độ PC/SC được giữ để tích hợp đầu đọc thật sau này.

Nếu simulator khởi động lại mà không khôi phục card image, danh tính thẻ có thể được tạo lại. Oracle có tùy chọn `-i=<image>` và `-o=<image>` để nạp/lưu bộ nhớ không mất điện; việc lưu image phụ thuộc quy trình dừng simulator của SDK. Với bản lab đầu tiên, giữ nguyên tiến trình simulator trong suốt buổi demo để tránh đổi khóa giữa chừng.

## 7. Build và cài applet vào thẻ ảo

Mở PowerShell số 3:

```powershell
Set-Location D:\ATBM-SYSTEM
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:JC_HOME_TOOLS = 'D:\SmartCard\tools'
$env:JC_HOME_SIMULATOR = 'D:\SmartCard\simulator'

.\frontend\card-applet\build.ps1
.\frontend\card-applet\install-simulator.ps1
```

Kết quả cần thấy: applet được cài, hoặc applet đã tồn tại và danh tính được giữ nguyên. Installer không tự uninstall/reset applet.

Thông số applet lab:

- AID: `F04B4D4149445001`.
- Khóa RSA-2048 được tạo bên trong applet.
- PIN ban đầu: `123456`.
- PIN mới phải có 6–12 chữ số.
- 5 lần nhập sai liên tiếp khóa PIN.

PIN lab được ghi rõ trong applet để phục vụ demo; không sử dụng giá trị mặc định này cho thẻ thật hoặc môi trường vận hành.

## 8. Khởi động Frontend

Trong PowerShell số 3 sau khi cài applet:

```powershell
Set-Location D:\ATBM-SYSTEM
.\frontend\run.ps1
```

Ứng dụng có 11 tab; mỗi tab có khung case riêng ở phía dưới:

| Tab | Case | Thao tác chính |
|---|---|---|
| Kết nối | NET-01 → NET-04 | Kết nối BE, đăng nhập, dashboard |
| Thẻ & PIN | CARD-01 → CARD-06 | Đọc/gán thẻ, ký challenge, PIN |
| Tài khoản | ACC-01 → ACC-05 | Tạo, khóa/mở khóa, force logout |
| MFA | MFA-01 → MFA-05 | OTP, TOTP, hộp thư lab |
| Khôi phục | REC-01 → REC-05 | Invite, recovery code, reset MFA |
| Credential | CRED-01 → CRED-05 | Xem/thu hồi, thay hoặc tắt TOTP |
| Phiên | SES-01 → SES-05 | Phiên cá nhân, logout, refresh |
| Phân quyền | RBAC-01 → RBAC-05 | Role, permission, assignment |
| Khóa ký | KEY-01 → KEY-04 | JWKS, NEXT, rotate, revoke |
| Dữ liệu | DATA-01 → DATA-04 | Catalog, cấu hình, validation, lỗi API |
| Chờ bổ sung | PLAN-01 → PLAN-03 | Các nghiệp vụ chưa hoàn thiện |

Chọn dòng case để xem thao tác và kỳ vọng. “Thao tác FE”, “Test tự động”, “Kiểm tra API”, “Chưa triển khai” thể hiện cách kiểm tra; không phải trạng thái PASS. Danh mục 57 kịch bản: `frontend/docs/DEMO_CASES.md`.

Ô Backend mặc định `http://127.0.0.1:8080`. Bấm **Kết nối** để kiểm tra status. Nếu thay địa chỉ, session FE hiện tại sẽ bị xóa để không gửi token của máy chủ cũ sang máy chủ mới.

## 9. Thử luồng nghiệp vụ đầu tiên

### 9.1. Tạo người dùng

1. Kết nối → Đăng nhập quản trị.
2. Nhập `admin` và mật khẩu bạn đã đặt.
3. Tài khoản → Tạo người dùng.
4. Nhập `student01`, họ tên và email.
5. Tải lại, kiểm tra trạng thái `PENDING_ACTIVATION`.

### 9.2. Gán thẻ

1. Smart Card → chọn `Oracle Simulator · localhost:9025`.
2. Bấm Đọc định danh & public key.
3. Đọc card ID và fingerprint SHA-256 ở nhật ký.
4. Tải tài khoản, chọn `student01`.
5. Xác minh người nhận thẻ/fingerprint rồi bấm Xác nhận gán thẻ.
6. Tài khoản chuyển `ACTIVE`.

Nếu báo trùng credential/card ID, không cố gán cùng thẻ cho tài khoản khác. Dùng tài khoản đã gán hoặc chuẩn bị một thẻ ảo mới theo quy trình lab.

### 9.3. Đăng nhập bằng thẻ

1. Đăng xuất admin, hoặc mở cửa sổ frontend thứ hai bằng một PowerShell khác.
2. Smart Card → nhập username `student01`.
3. Nhập PIN `123456` nếu chưa đổi.
4. Bấm Đăng nhập bằng thẻ.
5. Phiên → Tải phiên.

Luồng thực hiện: BE sinh challenge 60 giây → FE gửi PIN đến thẻ → applet ký challenge → BE kiểm tra chữ ký và consume challenge → cấp access/refresh token. PIN không gửi tới backend, private key không rời applet.

### 9.4. Kiểm tra an toàn

Giữ một cửa sổ admin và một cửa sổ người dùng:

- Admin khóa `student01`; yêu cầu API tiếp theo từ cửa sổ user phải bị từ chối.
- Admin mở khóa; user phải đăng nhập lại, phiên cũ không tự hoạt động trở lại.
- Admin thu hồi credential ở Dữ liệu → `credential`; thẻ đó không đăng nhập được nữa.
- Thử sai PIN một lần để xem số lần thử còn lại; tránh sai 5 lần nếu chưa muốn khóa thẻ.
- Admin xem `audit_log`, `authentication_event`, `idp_session` để đối chiếu sự kiện.

Các case replay, chữ ký sai, refresh reuse, ownership và phân quyền được kiểm thử tự động. Danh sách đầy đủ xem `docs/DEMO.md`.

## 10. Những lần chạy sau

Nếu chưa sửa code và các tiến trình đã dừng:

1. Đảm bảo MySQL chạy.
2. Chạy `backend/run.ps1` hoặc thêm `-UseCachedRuntime` nếu dùng bản offline.
3. Chạy simulator ở cổng 9025.
4. Chạy `install-simulator.ps1` nếu applet chưa tồn tại trong card image hiện tại.
5. Chạy `frontend/run.ps1`.

Sau khi sửa code, build/test lại trước khi chạy. Không cần chạy setup MySQL lại mỗi lần.

## 11. Dừng ứng dụng

1. Đăng xuất và đóng frontend.
2. Trong PowerShell chạy backend, nhấn `Ctrl+C`.
3. Dừng simulator theo hướng dẫn SDK; nếu cần giữ danh tính, sử dụng chế độ image của SDK.
4. Không cần dừng hoặc xóa MySQL database.

Không xóa schema để khắc phục lỗi đăng nhập. Việc xóa schema sẽ làm mất tài khoản, mapping public key và audit.

## 12. Xử lý lỗi thường gặp

| Lỗi | Kiểm tra / xử lý |
|---|---|
| Không tìm thấy Java / JAVA_HOME không tồn tại | Đặt `JAVA_HOME` tới JDK 21 như bước 2; không trỏ tới thư mục `bin` |
| Maven báo `No such host` / không tải được parent POM | Kiểm tra DNS, mạng/proxy của laptop; dùng đường offline cho lab nếu cache sẵn; không sửa phiên bản POM tùy tiện |
| Maven không ghi được repository | Script chuẩn dùng cache trong workspace; kiểm tra quyền ghi `D:\ATBM-SYSTEM` |
| Không tìm thấy executable JAR | Build Maven chuẩn, hoặc dùng `run.ps1 -UseCachedRuntime` sau khi offline checks đã qua |
| MySQL `Communications link failure` | Kiểm tra dịch vụ, host/port 3306 và kết nối Workbench |
| MySQL `Access denied` | Kiểm tra mật khẩu root ở setup, hoặc app credential đang dùng; không in mật khẩu ra log |
| `Unknown database 'kma_idp'` | Chạy `backend/setup-database.ps1` trước khi mở profile có default schema này |
| DPAPI credential không giải mã được | Dùng đúng tài khoản Windows đã tạo; máy khác cần thiết lập connection mới |
| Mật khẩu bootstrap không hợp lệ | Dùng 14–72 ký tự; database đã có dữ liệu thì mật khẩu mới không reset admin cũ |
| Flyway checksum mismatch | Không sửa migration đã áp dụng; tạo migration V2+ cho thay đổi tiếp theo |
| Entity schema-validation lỗi | Dừng BE, kiểm tra schema/migration đúng phiên bản; không chuyển `ddl-auto` thành `update` để che lỗi |
| Không tìm thấy `SocketCardTerminalProvider` | Kiểm tra `JC_HOME_SIMULATOR` và chạy bằng `frontend/run.ps1` |
| Simulator không kết nối | Đảm bảo server đang chạy ở 9025, không để hai tiến trình tranh cổng |
| `SW=6A82` | Chưa cài applet hoặc AID khác; chạy installer đúng simulator |
| `SW=6982` | Chưa VERIFY PIN thành công trước khi ký/đổi PIN |
| `SW=63Cx` | PIN sai, còn x lần; dừng đoán PIN trước khi khóa thẻ |
| Đăng nhập thẻ trả 401 | Kiểm tra username, card ID/public key đã gán, credential còn ACTIVE và thẻ chưa bị reset danh tính |
| API trả 423 | Tài khoản bị khóa hoặc chưa ở trạng thái được phép xác thực |
| Challenge trả 410 | Đã hết 60 giây hoặc đã sử dụng; bắt đầu lần xác thực mới |
| API trả 403 | Thiếu quyền; đăng nhập đúng tài khoản, không chỉ sửa nút FE |
| Token cũ mất hiệu lực sau khi restart BE local | Khóa ký local là tạm; đăng nhập lại. Production cần keystore lâu dài |
| Windows báo `Illegal System DLL Relocation` khi chạy simulator | Thử trực tiếp trong PowerShell thông thường ngoài môi trường hạn chế; kiểm tra đúng bản SDK/Windows, không tắt các bảo vệ Windows để xử lý |

## 13. Giới hạn đã xác định khi phát triển

Môi trường tự động hiện chặn socket localhost và không khởi động được native simulator. Vì vậy, bộ kiểm tra dùng MockMvc + H2 để kiểm tra pipeline controller/security/service/repository và Hibernate mapping, không kết nối MySQL của laptop. Applet được compile và convert CAP; chưa có kết quả chạy xuyên thẻ Oracle Simulator trong môi trường đó.

Khi chạy trên laptop bằng các bước trên, vẫn cần xác nhận lần đầu: kết nối MySQL, migration MySQL thực tế, khởi động simulator, cài applet và đăng nhập qua FE. Không coi kết quả test offline là bằng chứng các kết nối thật này đã thành công.

<!-- TEST_RESULTS_START -->
## Kết quả kiểm thử tự động gần nhất

**KẾT QUẢ LỊCH SỬ:** mã hiện tại đã khác dấu vân tay của lần test này. Không dùng các PASS bên dưới để kết luận phiên bản hiện tại đã qua test. Xem `backend/docs/test-evidence/backend-current-status.json`.

Thời điểm UTC: `2026-11-19T17:04:01.551729200Z`. Chế độ: `cached-compatibility`. Tổng kiểm tra: **241**; số request được ghi riêng: **237**.

Môi trường: MockMvc + real Spring Security filters; H2 in memory. Đây là kết quả của danh mục đã viết, không có nghĩa mọi case có thể tồn tại đều đã kiểm tra.

| Case | Request | Expected | Actual | Kết quả |
|---|---|---|---|---|
| TC001 | `GET /api/v1/status` | 200 | 200 | PASS |
| TC002 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC003 | `POST /api/v1/auth/password` | 200 | 200 | PASS |
| TC004 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC005 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC006 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC007 | `GET /api/v1/management/catalog` | 200 | 200 | PASS |
| TC008 | `GET /api/v1/management/resources/organization` | 200 | 200 | PASS |
| TC009 | `GET /api/v1/management/resources/person` | 200 | 200 | PASS |
| TC010 | `GET /api/v1/management/resources/identity` | 200 | 200 | PASS |
| TC011 | `GET /api/v1/management/resources/org_membership` | 200 | 200 | PASS |
| TC012 | `GET /api/v1/management/resources/account` | 200 | 200 | PASS |
| TC013 | `GET /api/v1/management/resources/external_identity` | 200 | 200 | PASS |
| TC014 | `GET /api/v1/management/resources/credential` | 200 | 200 | PASS |
| TC015 | `GET /api/v1/management/resources/webauthn_credential` | 200 | 200 | PASS |
| TC016 | `GET /api/v1/management/resources/webauthn_challenge` | 200 | 200 | PASS |
| TC017 | `GET /api/v1/management/resources/totp_credential` | 200 | 200 | PASS |
| TC018 | `GET /api/v1/management/resources/recovery_code` | 200 | 200 | PASS |
| TC019 | `GET /api/v1/management/resources/account_activation_token` | 200 | 200 | PASS |
| TC020 | `GET /api/v1/management/resources/org_role` | 200 | 200 | PASS |
| TC021 | `GET /api/v1/management/resources/org_role_assignment` | 200 | 200 | PASS |
| TC022 | `GET /api/v1/management/resources/role` | 200 | 200 | PASS |
| TC023 | `GET /api/v1/management/resources/permission` | 200 | 200 | PASS |
| TC024 | `GET /api/v1/management/resources/role_permission` | 200 | 200 | PASS |
| TC025 | `GET /api/v1/management/resources/account_role_assignment` | 200 | 200 | PASS |
| TC026 | `GET /api/v1/management/resources/application` | 200 | 200 | PASS |
| TC027 | `GET /api/v1/management/resources/client_redirect_uri` | 200 | 200 | PASS |
| TC028 | `GET /api/v1/management/resources/client_logout_uri` | 200 | 200 | PASS |
| TC029 | `GET /api/v1/management/resources/client_credential` | 200 | 200 | PASS |
| TC030 | `GET /api/v1/management/resources/oidc_scope` | 200 | 200 | PASS |
| TC031 | `GET /api/v1/management/resources/application_scope` | 200 | 200 | PASS |
| TC032 | `GET /api/v1/management/resources/client_grant_type` | 200 | 200 | PASS |
| TC033 | `GET /api/v1/management/resources/signing_key` | 200 | 200 | PASS |
| TC034 | `GET /api/v1/management/resources/idp_session` | 200 | 200 | PASS |
| TC035 | `GET /api/v1/management/resources/session_client` | 200 | 200 | PASS |
| TC036 | `GET /api/v1/management/resources/authentication_event` | 200 | 200 | PASS |
| TC037 | `GET /api/v1/management/resources/authorization_code` | 200 | 200 | PASS |
| TC038 | `GET /api/v1/management/resources/authorization_code_scope` | 200 | 200 | PASS |
| TC039 | `GET /api/v1/management/resources/refresh_token` | 200 | 200 | PASS |
| TC040 | `GET /api/v1/management/resources/refresh_token_scope` | 200 | 200 | PASS |
| TC041 | `GET /api/v1/management/resources/user_consent` | 200 | 200 | PASS |
| TC042 | `GET /api/v1/management/resources/user_consent_scope` | 200 | 200 | PASS |
| TC043 | `GET /api/v1/management/resources/security_policy` | 200 | 200 | PASS |
| TC044 | `GET /api/v1/management/resources/audit_log` | 200 | 200 | PASS |
| TC045 | `GET /api/v1/management/resources/smart_card_credential` | 200 | 200 | PASS |
| TC046 | `GET /api/v1/management/resources/smart_card_challenge` | 200 | 200 | PASS |
| TC047 | `GET /api/v1/management/resources/authentication_transaction` | 200 | 200 | PASS |
| TC048 | `GET /api/v1/management/resources/outbox_event` | 200 | 200 | PASS |
| TC049 | `GET /api/v1/management/resources/credential` | 200 | 200 | PASS |
| TC050 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC051 | `POST /api/v1/accounts/{id}/lock` | 409 | 409 | PASS |
| TC052 | `POST /api/v1/accounts` | 200 | 200 | PASS |
| TC053 | `POST /api/v1/accounts/{id}/smart-cards` | 200 | 200 | PASS |
| TC054 | `POST /api/v1/auth/smart-card/challenge` | 200 | 200 | PASS |
| TC055 | `POST /api/v1/auth/smart-card/verify` | 401 | 401 | PASS |
| TC056 | `POST /api/v1/auth/smart-card/verify` | 200 | 200 | PASS |
| TC057 | `POST /api/v1/auth/smart-card/verify` | 410 | 410 | PASS |
| TC058 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC059 | `GET /api/v1/management/overview` | 403 | 403 | PASS |
| TC060 | `DELETE /api/v1/me/sessions/{id}` | 404 | 404 | PASS |
| TC061 | `POST /api/v1/auth/refresh` | 200 | 200 | PASS |
| TC062 | `POST /api/v1/auth/refresh` | 401 | 401 | PASS |
| TC063 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC064 | `POST /api/v1/auth/smart-card/challenge` | 200 | 200 | PASS |
| TC065 | `POST /api/v1/auth/smart-card/verify` | 410 | 410 | PASS |
| TC066 | `POST /api/v1/accounts/{id}/lock` | 200 | 200 | PASS |
| TC067 | `POST /api/v1/auth/smart-card/challenge` | 423 | 423 | PASS |
| TC068 | `POST /api/v1/accounts/{id}/unlock` | 200 | 200 | PASS |
| TC069 | `POST /api/v1/auth/smart-card/challenge` | 200 | 200 | PASS |
| TC070 | `POST /api/v1/auth/smart-card/verify` | 401 | 401 | PASS |
| TC071 | `POST /api/v1/auth/smart-card/challenge` | 401 | 401 | PASS |
| TC072 | `DELETE /api/v1/accounts/credentials/{id}` | 200 | 200 | PASS |
| TC073 | `POST /api/v1/auth/smart-card/challenge` | 401 | 401 | PASS |
| TC074 | `POST /api/v1/auth/password` | 200 | 200 | PASS |
| TC075 | `POST /api/v1/auth/password` | 401 | 401 | PASS |
| TC076 | `POST /api/v1/auth/password` | 401 | 401 | PASS |
| TC077 | `POST /api/v1/auth/password` | 401 | 401 | PASS |
| TC078 | `POST /api/v1/auth/password` | 401 | 401 | PASS |
| TC079 | `POST /api/v1/auth/password` | 401 | 401 | PASS |
| TC080 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC081 | `POST /api/v1/accounts/{id}/unlock` | 200 | 200 | PASS |
| TC082 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC083 | `PATCH /api/v1/management/resources/permission/{id}` | 409 | 409 | PASS |
| TC084 | `GET /api/v1/management/resources/unknown_table` | 404 | 404 | PASS |
| TC085 | `POST /api/v1/management/resources/credential` | 405 | 405 | PASS |
| TC086 | `POST /api/v1/management/resources/application` | 200 | 200 | PASS |
| TC087 | `PATCH /api/v1/management/resources/application/{id}` | 400 | 400 | PASS |
| TC088 | `POST /api/v1/management/resources/client_redirect_uri` | 400 | 400 | PASS |
| TC089 | `POST /api/v1/management/resources/client_redirect_uri` | 400 | 400 | PASS |
| TC090 | `POST /api/v1/management/resources/security_policy` | 409 | 409 | PASS |
| TC091 | `POST /api/v1/auth/password` | 200 | 200 | PASS |
| TC092 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC093 | `ASSERT Live grant takes effect with existing access token` | True | True | PASS |
| TC094 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC095 | `ASSERT Revoked grant disappears without waiting for JWT expiry` | True | True | PASS |
| TC096 | `ASSERT Organization grant cannot become a global grant` | True | True | PASS |
| TC097 | `ASSERT Organization grant matches its own scope` | True | True | PASS |
| TC098 | `ASSERT Organization grant cannot cross organization` | True | True | PASS |
| TC099 | `ASSERT Expired scoped assignment is denied` | True | True | PASS |
| TC100 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC101 | `POST /api/v1/auth/refresh` | 401 | 401 | PASS |
| TC102 | `POST /api/v1/auth/password` | 200 | 200 | PASS |
| TC103 | `POST /api/v1/auth/refresh` | 401 | 401 | PASS |
| TC104 | `ASSERT Last authenticator cannot be removed` | True | True | PASS |
| TC105 | `ASSERT RFC6238 SHA1 vector 59` | True | True | PASS |
| TC106 | `ASSERT RFC6238 SHA1 vector 1111111109` | True | True | PASS |
| TC107 | `ASSERT RFC6238 SHA1 vector 1111111111` | True | True | PASS |
| TC108 | `ASSERT RFC6238 SHA1 vector 1234567890` | True | True | PASS |
| TC109 | `ASSERT RFC6238 SHA1 vector 2000000000` | True | True | PASS |
| TC110 | `ASSERT RFC6238 SHA1 vector 20000000000` | True | True | PASS |
| TC111 | `ASSERT AES-GCM round trip` | True | True | PASS |
| TC112 | `ASSERT AES-GCM AAD swap rejected` | True | True | PASS |
| TC113 | `POST /api/v1/me/totp/setup` | 403 | 403 | PASS |
| TC114 | `POST /api/v1/accounts/{id}/activation-invite` | 202 | 202 | PASS |
| TC115 | `ASSERT Activation hash only` | True | True | PASS |
| TC116 | `POST /api/v1/auth/activate` | 200 | 200 | PASS |
| TC117 | `POST /api/v1/auth/activate` | 410 | 410 | PASS |
| TC118 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC119 | `POST /api/v1/auth/enrollment/totp/setup` | 200 | 200 | PASS |
| TC120 | `POST /api/v1/auth/enrollment/totp/setup` | 409 | 409 | PASS |
| TC121 | `POST /api/v1/auth/enrollment/totp/verify` | 401 | 401 | PASS |
| TC122 | `POST /api/v1/auth/enrollment/totp/verify` | 200 | 200 | PASS |
| TC123 | `ASSERT Recovery codes shown once at enrollment` | True | True | PASS |
| TC124 | `DELETE /api/v1/me/totp` | 409 | 409 | PASS |
| TC125 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC126 | `POST /api/v1/auth/password` | 200 | 200 | PASS |
| TC127 | `ASSERT Primary factor alone grants no access token` | True | True | PASS |
| TC128 | `POST /api/v1/auth/mfa/totp/verify` | 401 | 401 | PASS |
| TC129 | `POST /api/v1/auth/mfa/totp/verify` | 200 | 200 | PASS |
| TC130 | `POST /api/v1/auth/mfa/totp/verify` | 410 | 410 | PASS |
| TC131 | `ASSERT MFA session AAL2` | True | True | PASS |
| TC132 | `ASSERT TOTP secret encrypted` | True | True | PASS |
| TC133 | `POST /api/v1/auth/otp/request` | 200 | 200 | PASS |
| TC134 | `GET /api/v1/local-mailbox/{id}` | 200 | 200 | PASS |
| TC135 | `ASSERT Outbox payload encrypted` | True | True | PASS |
| TC136 | `POST /api/v1/auth/otp/verify` | 401 | 401 | PASS |
| TC137 | `POST /api/v1/auth/otp/verify` | 200 | 200 | PASS |
| TC138 | `POST /api/v1/auth/otp/verify` | 410 | 410 | PASS |
| TC139 | `POST /api/v1/auth/otp/request` | 429 | 429 | PASS |
| TC140 | `POST /api/v1/auth/otp/request` | 200 | 200 | PASS |
| TC141 | `POST /api/v1/me/recovery-codes/regenerate` | 200 | 200 | PASS |
| TC142 | `POST /api/v1/auth/recovery/verify` | 401 | 401 | PASS |
| TC143 | `POST /api/v1/auth/recovery/verify` | 200 | 200 | PASS |
| TC144 | `POST /api/v1/auth/recovery/verify` | 401 | 401 | PASS |
| TC145 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC146 | `POST /api/v1/auth/enrollment/totp/setup` | 200 | 200 | PASS |
| TC147 | `POST /api/v1/auth/enrollment/totp/verify` | 200 | 200 | PASS |
| TC148 | `POST /api/v1/auth/enrollment/totp/verify` | 410 | 410 | PASS |
| TC149 | `DELETE /api/v1/me/totp` | 409 | 409 | PASS |
| TC150 | `POST /api/v1/me/webauthn/registration/options` | 200 | 200 | PASS |
| TC151 | `POST /api/v1/me/webauthn/registration/verify` | 401 | 401 | PASS |
| TC152 | `POST /api/v1/me/webauthn/registration/verify` | 410 | 410 | PASS |
| TC153 | `POST /api/v1/me/webauthn/registration/options` | 200 | 200 | PASS |
| TC154 | `POST /api/v1/me/webauthn/registration/verify` | 401 | 401 | PASS |
| TC155 | `POST /api/v1/me/webauthn/registration/options` | 200 | 200 | PASS |
| TC156 | `POST /api/v1/me/webauthn/registration/verify` | 200 | 200 | PASS |
| TC157 | `POST /api/v1/me/webauthn/registration/verify` | 410 | 410 | PASS |
| TC158 | `ASSERT WebAuthn COSE public key and attested data persisted` | True | True | PASS |
| TC159 | `POST /api/v1/me/webauthn/registration/options` | 200 | 200 | PASS |
| TC160 | `POST /api/v1/me/webauthn/registration/verify` | 409 | 409 | PASS |
| TC161 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC162 | `ASSERT WebAuthn requests required UV` | True | True | PASS |
| TC163 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC164 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC165 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC166 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC167 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC168 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC169 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC170 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC171 | `POST /api/v1/auth/webauthn/verify` | 200 | 200 | PASS |
| TC172 | `ASSERT WebAuthn UV session has AAL2` | True | True | PASS |
| TC173 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC174 | `POST /api/v1/auth/webauthn/verify` | 410 | 410 | PASS |
| TC175 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC176 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC177 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC178 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC179 | `POST /api/v1/auth/webauthn/options` | 401 | 401 | PASS |
| TC180 | `POST /api/v1/auth/webauthn/options` | 401 | 401 | PASS |
| TC181 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC182 | `POST /api/v1/auth/webauthn/verify` | 410 | 410 | PASS |
| TC183 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC184 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC185 | `POST /api/v1/auth/webauthn/verify` | 410 | 410 | PASS |
| TC186 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC187 | `ASSERT Concurrent WebAuthn proof yields exactly one session and one consumed challenge` | True | True | PASS |
| TC188 | `POST /api/v1/auth/webauthn/options` | 200 | 200 | PASS |
| TC189 | `POST /api/v1/auth/webauthn/verify` | 401 | 401 | PASS |
| TC190 | `POST /api/v1/auth/webauthn/options` | 401 | 401 | PASS |
| TC191 | `POST /api/v1/auth/webauthn/options` | 429 | 429 | PASS |
| TC192 | `ASSERT Outbox successful delivery is not dispatched twice` | True | True | PASS |
| TC193 | `ASSERT Outbox failure schedules bounded retry` | True | True | PASS |
| TC194 | `ASSERT Outbox cannot claim before backoff elapsed` | True | True | PASS |
| TC195 | `ASSERT Outbox becomes DEAD after five delivery failures` | True | True | PASS |
| TC196 | `ASSERT DEAD outbox item cannot be claimed` | True | True | PASS |
| TC197 | `ASSERT Only one worker claims an outbox item` | True | True | PASS |
| TC198 | `ASSERT Expired worker lease can be recovered` | True | True | PASS |
| TC199 | `ASSERT Expired final lease does not cause unlimited retries` | True | True | PASS |
| TC200 | `ASSERT Outbox stores encrypted notification bodies only` | True | True | PASS |
| TC201 | `GET /api/v1/me/credentials` | 200 | 200 | PASS |
| TC202 | `ASSERT Own credential API excludes all secrets and public-key payloads` | True | True | PASS |
| TC203 | `DELETE /api/v1/me/credentials/{id}` | 404 | 404 | PASS |
| TC204 | `DELETE /api/v1/me/credentials/{id}` | 403 | 403 | PASS |
| TC205 | `DELETE /api/v1/me/credentials/{id}` | 200 | 200 | PASS |
| TC206 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC207 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC208 | `ASSERT Self revoke updates credential state` | True | True | PASS |
| TC209 | `DELETE /api/v1/me/credentials/{id}` | 409 | 409 | PASS |
| TC210 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC211 | `POST /api/v1/logout` | 200 | 200 | PASS |
| TC212 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC213 | `GET /api/v1/signing-keys` | 200 | 200 | PASS |
| TC214 | `ASSERT Signing key metadata excludes private references` | True | True | PASS |
| TC215 | `GET /api/v1/signing-keys` | 403 | 403 | PASS |
| TC216 | `POST /api/v1/signing-keys/prepare` | 403 | 403 | PASS |
| TC217 | `POST /api/v1/signing-keys/prepare` | 400 | 400 | PASS |
| TC218 | `POST /api/v1/signing-keys/prepare` | 201 | 201 | PASS |
| TC219 | `GET /oauth2/jwks` | 200 | 200 | PASS |
| TC220 | `ASSERT NEXT key pre-published alongside active key` | True | True | PASS |
| TC221 | `ASSERT JWKS contains public parameters only` | True | True | PASS |
| TC222 | `POST /api/v1/signing-keys/rotate` | 400 | 400 | PASS |
| TC223 | `POST /api/v1/signing-keys/rotate` | 201 | 201 | PASS |
| TC224 | `ASSERT Rotation changes active kid` | True | True | PASS |
| TC225 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC226 | `ASSERT New tokens use rotated signing key` | True | True | PASS |
| TC227 | `POST /api/v1/signing-keys/rotate` | 409 | 409 | PASS |
| TC228 | `DELETE /api/v1/signing-keys/{id}` | 409 | 409 | PASS |
| TC229 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC230 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC231 | `GET /oauth2/jwks` | 200 | 200 | PASS |
| TC232 | `ASSERT Retired key disappears after overlap expiry` | True | True | PASS |
| TC233 | `DELETE /api/v1/signing-keys/{id}` | 200 | 200 | PASS |
| TC234 | `GET /api/v1/me` | 401 | 401 | PASS |
| TC235 | `ASSERT Emergency retired-key revocation revokes every active session` | True | True | PASS |
| TC236 | `GET /api/v1/me` | 200 | 200 | PASS |
| TC237 | `POST /api/v1/logout` | 200 | 200 | PASS |

Chưa được đánh dấu PASS bằng kết quả này: MySQL thực, SMTP/TLS/SIEM thực, Oracle Simulator chạy native, thẻ/đầu đọc thật, OIDC conformance và tải/HA/DR. Những phần này cần kết quả chạy độc lập.

Dấu vân tay nguồn: `74137c1c40f1fcd24dfc59cf9258e28ef65f7f4bdebdbd847c284d7c0bce42f7`. Báo cáo máy đọc: `backend/docs/test-evidence/backend-latest.json`; log: `backend/docs/test-evidence/backend-latest.log`.
<!-- TEST_RESULTS_END -->

<!-- FE_TEST_RESULTS_START -->
## Bằng chứng kiểm thử FE gần nhất

Các kết quả dưới đây chỉ áp dụng cho danh mục đã thực thi. MockMvc không kiểm chứng TCP/TLS; APDU fixture không thay thế Oracle Simulator hoặc thẻ thật.

### Build và desktop smoke

Thời điểm UTC: `2026-11-20T14:23:48.234695+00:00`.

Dấu vân tay nguồn: `04e913ea8de258124845f03110ebbe0bdf23ebd804dd9ba079d4abe1bfa80d15`.

Desktop smoke: `PASS`; mode: `cached-compatibility`.

### API client: refresh, đồng thời và lỗi kết nối

Thời điểm UTC: `2026-11-20T14:23:41.248266400Z`.

Dấu vân tay nguồn: `04e913ea8de258124845f03110ebbe0bdf23ebd804dd9ba079d4abe1bfa80d15`.

| Case | Kết quả |
|---|---|
| Refresh request omits expired Authorization header | PASS |
| Expired access refreshes and retries successfully | PASS |
| Exactly one refresh and one retry | PASS |
| Business 401 is not replayed or refreshed | PASS |
| Timed-out mutation is never automatically resubmitted | PASS |
| Malformed server errors reveal no response body | PASS |
| Non-local HTTP is rejected | PASS |
| Credentials embedded in base URL are rejected | PASS |
| Authority-changing paths are rejected | PASS |
| Concurrent expired requests both recover | PASS |
| Concurrent requests rotate refresh only once | PASS |
| Sign-out during refresh cannot restore the old session | PASS |
| Session switch prevents mutation retry | PASS |
| Oversized response is cancelled while receiving | PASS |
| UTF-8 response survives split byte buffers | PASS |
| Refresh with malformed JSON clears uncertain session without replay | PASS |
| Refresh with missing token pair clears uncertain session without replay | PASS |
| Refresh with duplicate JSON field clears uncertain session without replay | PASS |
| Refresh with trailing JSON value clears uncertain session without replay | PASS |
| Lost refresh response clears tokens and never replays mutation | PASS |
| Bearer rejection after refresh clears session with one bounded retry | PASS |
| Failure from an old refresh cannot clear a newer user's session | PASS |
| Successful response arriving after logout is discarded | PASS |
| Server 503 never replays a mutation or clears valid tokens | PASS |
| Public authentication requests omit bearer from an existing session | PASS |

### Thông báo lỗi và xử lý PIN

Thời điểm UTC: `2026-11-20T14:23:41.600325200Z`.

Dấu vân tay nguồn: `04e913ea8de258124845f03110ebbe0bdf23ebd804dd9ba079d4abe1bfa80d15`.

| Case | Kết quả |
|---|---|
| API preserves safe status, code and request ID | PASS |
| Dialog shows concise instruction and correlation ID | PASS |
| Server detail never appears in dialog or activity log | PASS |
| HTTP 400 has short fallback without raw server text | PASS |
| HTTP 401 has short fallback without raw server text | PASS |
| HTTP 403 has short fallback without raw server text | PASS |
| HTTP 404 has short fallback without raw server text | PASS |
| HTTP 405 has short fallback without raw server text | PASS |
| HTTP 406 has short fallback without raw server text | PASS |
| HTTP 409 has short fallback without raw server text | PASS |
| HTTP 410 has short fallback without raw server text | PASS |
| HTTP 415 has short fallback without raw server text | PASS |
| HTTP 423 has short fallback without raw server text | PASS |
| HTTP 429 has short fallback without raw server text | PASS |
| HTTP 500 has short fallback without raw server text | PASS |
| HTTP 503 has short fallback without raw server text | PASS |
| Validation names useful fields without exposing unknown server strings | PASS |
| Read-only table error gives the business action rather than a protocol warning | PASS |
| Unknown exception text is never displayed or logged | PASS |
| Invalid base URL hides embedded credentials | PASS |
| Timeout has retry-safe instruction | PASS |
| TLS failure omits certificate/internal error detail | PASS |
| DNS failure has specific user instruction | PASS |
| Connection refused has specific user instruction | PASS |
| Cancelled action does not display a Java exception | PASS |
| Invalid request ID is suppressed | PASS |
| Fixed input instruction remains visible | PASS |
| APDU 6982 maps to PIN_REQUIRED | PASS |
| APDU 6983 maps to PIN_BLOCKED | PASS |
| APDU 63c0 maps to PIN_BLOCKED | PASS |
| APDU 63c2 maps to PIN_INVALID | PASS |
| APDU 6a82 maps to APPLET_NOT_FOUND | PASS |
| APDU 6d00 maps to CARD_PROTOCOL_ERROR | PASS |
| PIN buffer cleared when challenge validation fails | PASS |
| Both PIN buffers cleared when replacement validation fails | PASS |
| PIN buffer cleared when PIN format is invalid | PASS |
| Malformed card identity produces safe protocol error | PASS |

### Tab và danh mục kịch bản

Thời điểm UTC: `2026-11-20T14:23:42.241564300Z`.

Dấu vân tay nguồn: `04e913ea8de258124845f03110ebbe0bdf23ebd804dd9ba079d4abe1bfa80d15`.

| Case | Kết quả |
|---|---|
| Every scenario has a unique ID, steps and expected outcome | PASS |
| Main tabs follow the case catalog | PASS |
| Tab Kết nối is bound to NET | PASS |
| Case instructions render correctly in Kết nối | PASS |
| Tab Thẻ & PIN is bound to CARD | PASS |
| Case instructions render correctly in Thẻ & PIN | PASS |
| Tab Tài khoản is bound to ACC | PASS |
| Case instructions render correctly in Tài khoản | PASS |
| Tab MFA is bound to MFA | PASS |
| Case instructions render correctly in MFA | PASS |
| Tab Khôi phục is bound to REC | PASS |
| Case instructions render correctly in Khôi phục | PASS |
| Tab Credential is bound to CRED | PASS |
| Case instructions render correctly in Credential | PASS |
| Tab Phiên is bound to SES | PASS |
| Case instructions render correctly in Phiên | PASS |
| Tab Phân quyền is bound to RBAC | PASS |
| Case instructions render correctly in Phân quyền | PASS |
| Tab Khóa ký is bound to KEY | PASS |
| Case instructions render correctly in Khóa ký | PASS |
| Tab Dữ liệu is bound to DATA | PASS |
| Case instructions render correctly in Dữ liệu | PASS |
| Tab Chờ bổ sung is bound to PLAN | PASS |
| Case instructions render correctly in Chờ bổ sung | PASS |
| Unimplemented scenarios are explicitly marked | PASS |
| Permission buttons are disabled before login | PASS |
| Delegated role.read enables only permitted controls | PASS |
| Delegated member cannot use root-only data controls | PASS |
| Root can access all role/key controls | PASS |
| Logout disables all capability controls | PASS |

### Luồng Swing: đăng nhập, hủy MFA, dữ liệu cũ, logout và request muộn

Thời điểm UTC: `2026-11-20T14:23:47.019816300Z`.

Real Swing controllers with in-process API responses, software APDU RSA-2048 signatures, injected card failures and scripted dialogs; no network, real backend, MySQL or Oracle Simulator.

Dấu vân tay nguồn: `04e913ea8de258124845f03110ebbe0bdf23ebd804dd9ba079d4abe1bfa80d15`.

| Case | Kết quả |
|---|---|
| Password login loads admin profile and overview | PASS |
| Password input array is erased | PASS |
| Failed login clears previous identity, tables, editor and card | PASS |
| Failed login shows one short safe error | PASS |
| Cancelling MFA grants no session and sends no verification | PASS |
| Rejected MFA response never enables privileged actions | PASS |
| MFA confirmation loads the profile and permissions | PASS |
| MFA code is absent from activity log | PASS |
| Cancelling activation before submit preserves current session | PASS |
| Cancelling replacement QR preserves current session and skips verification | PASS |
| Successful TOTP replacement clears session and privileged views | PASS |
| Read-only key permission exposes only the Close option | PASS |
| Closing credential dialog sends no DELETE | PASS |
| Credential revoke requires a selected row | PASS |
| Cancelling revoke confirmation preserves credential session | PASS |
| Credential revoke success clears local token and views | PASS |
| Cancelling role creation sends no mutation | PASS |
| Sessions screen loads active session metadata | PASS |
| Cancelling session revocation sends no DELETE | PASS |
| Revoking another session reloads its status on screen | PASS |
| Revoking current session handles 401 refresh rejection and clears UI | PASS |
| Sorted table selection resolves the correct model ID | PASS |
| Changing resource clears previous rows, editor and loaded-resource marker | PASS |
| JSON array is rejected locally before any request | PASS |
| Malformed JSON is rejected locally without raw payload in dialog | PASS |
| Busy state displays the interaction overlay | PASS |
| Second action is refused while a worker is running | PASS |
| Button keyboard/action event is ignored while busy | PASS |
| Late response after logout cannot repopulate UI | PASS |
| Failed reread invalidates the cached card identity | PASS |
| Card connection failure clears PIN and grants no session | PASS |
| APDU read displays card identity and RSA fingerprint | PASS |
| Swapped card is detected before credential enrollment request | PASS |
| Matching card is reread and enrolled once | PASS |
| Incorrect card PIN never submits signature or grants session | PASS |
| Card login signs RSA challenge and loads admin profile | PASS |
| PIN remains inside APDU boundary and absent from API payloads | PASS |
| Truncated signature is rejected before verification request | PASS |
| Logout clears local state even when backend times out | PASS |
| Timeout detail is not exposed in dialog or activity | PASS |
| Backend switch clears old token and every private screen | PASS |
| Admin downgrade removes privileged data and root actions | PASS |
| Downgrade retains only explicitly granted controls | PASS |
| 403 clears cached capabilities until profile reload | PASS |
| Stale authentication cannot restore a cleared session | PASS |
| Closing window erases tokens and sensitive UI values | PASS |

### Tích hợp mã FE–BE qua HTTP/APDU giả lập

Thời điểm UTC: `2026-11-19T16:55:49.239084900Z`.

**KẾT QUẢ LỊCH SỬ:** nguồn đã thay đổi từ lần test này; cần chạy lại trước khi đánh dấu phiên bản hiện tại PASS.

HTTP transport via MockMvc; software APDU card fixture; NOT Oracle Simulator or physical card.

| Case | Kết quả |
|---|---|
| FE password login reaches real BE and creates admin session | PASS |
| FE enrollment obtains real AAL2 admin token | PASS |
| FE account creation executes BE transaction | PASS |
| Production CardClient reads simulated APDU public key and identity | PASS |
| Wrong card PIN prevents FE from receiving a token | PASS |
| PIN char array is cleared after a failed card attempt | PASS |
| FE APDU signature is verified by real BE | PASS |
| PIN char array is cleared after successful authentication | PASS |
| PIN field never travels across the HTTP API | PASS |
| Swing controller completes card login against real BE | PASS |
| Swing member view disables every admin action | PASS |
| Swing clears the PIN input after submit | PASS |
| Swing task runner releases busy overlay after card login | PASS |
| Swing dashboard renders real BE counts | PASS |
| Swing admin view enables its management controls | PASS |
| Swing account table renders real BE account rows | PASS |
| Swing activity contains neither PIN nor bearer tokens | PASS |
| Real FE refresh recovers from BE bearer rejection | PASS |
| Refresh request carried no expired bearer header | PASS |
| Real BE rejects management access from member FE | PASS |
| FE card service preserves MFA challenge rather than treating it as a login | PASS |
| FE completes card plus TOTP against real BE | PASS |
| Lock invalidates the real FE session and refresh state | PASS |
| Unlock allows a fresh card challenge | PASS |
| APDU private key was never exposed to an API | PASS |
| FE reads active plus NEXT public JWK | PASS |
| FE stays authenticated through signing-key overlap | PASS |
| FE receives domain rejection when revoking active signing key | PASS |

Báo cáo JSON được lưu trong `frontend/docs/test-evidence/` để giữ bằng chứng khi tách repository.

Chưa được kiểm chứng bằng các báo cáo này: kết nối MySQL thật, Oracle Simulator native, đầu đọc/thẻ thật, SMTP thật, TLS và kiểm thử tải.

Chạy lại: `python scripts/offline_verify.py`; kiểm tra tích hợp tùy chọn: `python scripts/integration_with_backend.py --backend D:\ATBM-SYSTEM\backend`. Hai repository vẫn build độc lập.
<!-- FE_TEST_RESULTS_END -->

<!-- EXCEPTION_RESULTS_START -->
## Exception tests độc lập

Thời điểm UTC: `2026-11-20T13:29:45.287806400Z`. 56 kiểm tra PASS. Scope: Standalone MockMvc/servlet only; no Spring Boot, database, SMTP, or sockets.

| Case | Kết quả |
|---|---|
| POST /probe/body returns 400 | PASS |
| POST /probe/body does not expose exception details | PASS |
| POST /probe/body returns stable code and request ID | PASS |
| Validation identifies field names without rejected values | PASS |
| POST /probe/body returns 400 | PASS |
| POST /probe/body does not expose exception details | PASS |
| POST /probe/body returns stable code and request ID | PASS |
| POST /probe/body returns 400 | PASS |
| POST /probe/body does not expose exception details | PASS |
| POST /probe/body returns stable code and request ID | PASS |
| POST /probe/body returns 200 | PASS |
| POST /probe/body does not expose exception details | PASS |
| GET /probe/integer returns 400 | PASS |
| GET /probe/integer does not expose exception details | PASS |
| GET /probe/integer returns stable code and request ID | PASS |
| GET /probe/integer?value=not-a-number returns 400 | PASS |
| GET /probe/integer?value=not-a-number does not expose exception details | PASS |
| GET /probe/integer?value=not-a-number returns stable code and request ID | PASS |
| GET /probe/header returns 400 | PASS |
| GET /probe/header does not expose exception details | PASS |
| GET /probe/header returns stable code and request ID | PASS |
| POST /probe/body returns 415 | PASS |
| POST /probe/body does not expose exception details | PASS |
| POST /probe/body returns stable code and request ID | PASS |
| DELETE /probe/body returns 405 | PASS |
| DELETE /probe/body does not expose exception details | PASS |
| DELETE /probe/body returns stable code and request ID | PASS |
| 405 retains the allowed HTTP method | PASS |
| GET /probe/known returns 423 | PASS |
| GET /probe/known does not expose exception details | PASS |
| GET /probe/known returns stable code and request ID | PASS |
| GET /probe/conflict returns 409 | PASS |
| GET /probe/conflict does not expose exception details | PASS |
| GET /probe/conflict returns stable code and request ID | PASS |
| GET /probe/concurrent returns 409 | PASS |
| GET /probe/concurrent does not expose exception details | PASS |
| GET /probe/concurrent returns stable code and request ID | PASS |
| GET /probe/unavailable returns 503 | PASS |
| GET /probe/unavailable does not expose exception details | PASS |
| GET /probe/unavailable returns stable code and request ID | PASS |
| GET /probe/server returns 500 | PASS |
| GET /probe/server does not expose exception details | PASS |
| GET /probe/server returns stable code and request ID | PASS |
| GET /probe/denied returns 403 | PASS |
| GET /probe/denied does not expose exception details | PASS |
| GET /probe/denied returns stable code and request ID | PASS |
| GET /probe/missing returns 404 | PASS |
| GET /probe/missing does not expose exception details | PASS |
| GET /probe/missing returns stable code and request ID | PASS |
| Unsupported response format returns structured 406 | PASS |
| Unexpected filter failure is sanitized JSON 500 | PASS |
| Invalid error status and code cannot expose arbitrary text | PASS |
| Filter error 401 has the same code/requestId schema | PASS |
| Filter error 403 has the same code/requestId schema | PASS |
| Filter error 429 has the same code/requestId schema | PASS |
| Rate limit returns structured 429 with Retry-After | PASS |

Dấu vân tay nguồn: `20f5a1f8516cee495fa5c3a878a1dfb630a8426678216ccfe6dc09c009be572a`. JSON: `backend/docs/test-evidence/backend-exceptions-latest.json`. Regression nghiệp vụ tổng vẫn cần kết quả riêng.
<!-- EXCEPTION_RESULTS_END -->

## MySQL chưa chạy trên laptop

Chẩn đoán mới nhất: Docker Engine đang chạy nhưng không có container MySQL; các connection Workbench chỉ lưu địa chỉ. Script riêng KMA: `backend/start-mysql.ps1` (chạy từ root workspace). Container `atbm-mysql`, volume `atbm_mysql_data`; giữ nguyên volume project cũ. Script đã qua kiểm tra cú pháp, chưa được thực thi do giới hạn quyền Docker của agent. Xem `backend/docs/MYSQL_LOCAL_TROUBLESHOOTING.md` để chạy từ PowerShell của bạn và đọc giới hạn kiểm chứng.

### Sửa quyền MySQL qua Docker

Đã sửa grant host từ `localhost` sang host client thực tế (quan sát `172.17.0.1`), và xác minh đăng nhập MySQL thật bằng tài khoản ứng dụng PASS. Chạy `.\backend\setup-database.ps1 -RepairConnection` nếu cần sửa lại. Giữ nguyên schema/credential, không chạy BE bằng root. 15 kiểm tra validation setup PASS. Kết quả DB không thay thế kiểm tra khởi động BE; lần chạy BE hiện bị duyệt tự động chặn do external telemetry. Xem `backend/docs/MYSQL_LOCAL_TROUBLESHOOTING.md`.

## Lỗi WebAuthnVerifier / Unresolved compilation problems khi chạy cache

Ngày 20/11/2026 đã đọc được lỗi trong terminal VS Code: `com.fasterxml.jackson.dataformat.cbor cannot be resolved` và `ObjectMapper(Object) is undefined`, phát sinh khi khởi tạo WebAuthnVerifier. Log cho thấy class được nạp từ output biên dịch của IDE có lỗi `Unresolved compilation problems`; launcher cũ dùng chung `target/classes` với IDE.

Đã khai báo trực tiếp `jackson-dataformat-cbor` trong pom vì mã BE import CBORFactory, và tách runtime cache khỏi output của IDE:

- `run.ps1 -UseCachedRuntime` tự gọi `scripts/prepare_cached_runtime.py` trước khi hỏi mật khẩu/chạy Java.
- Script kiểm tra dependency CBOR/WebAuthn và biên dịch bằng javac vào `target/cached-runtime/classes`, không nạp `target/classes` của IDE.
- Biên dịch lỗi thì dừng trước khi khởi động; không chạy lại class cũ.
- Biên dịch 171 file main PASS; 4 kiểm tra constructor WebAuthn/CBOR/vị trí class/origin PASS bằng classpath nhỏ, không khởi động Spring Boot, DB hoặc HTTP server.

Chạy lại từ root workspace:

```powershell
cd D:\ATBM-SYSTEM
.\backend\run.ps1 -UseCachedRuntime
```

Bằng chứng: `backend/docs/test-evidence/cached-runtime-compilation-latest.json` và `backend/docs/test-evidence/webauthn-startup-latest.json` (trong repo backend bỏ tiền tố backend). Kiểm tra riêng: `python backend/scripts/prepare_cached_runtime.py`, rồi `python backend/scripts/verify_webauthn_startup.py`.

Đây chưa phải PASS khởi động BE/Flyway/JPA/API đầy đủ. Agent chưa được phép chạy lại toàn ứng dụng vì duyệt tự động đã chặn external telemetry tới dc.services.visualstudio.com. Không dùng kiểm tra constructor riêng này để thay thế bộ test đã bị chặn.

# Khởi động Frontend và thẻ ảo độc lập

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
- Rà soát UI mới nhất: `docs/UI_REGRESSION_REVIEW.md`; đã sửa dữ liệu cũ sau logout/đổi BE, kết quả request muộn, gán nhầm thẻ và tải lại trạng thái sau thu hồi phiên.
- Các lỗi có `code` và `requestId`; FE hiển thị thông báo ngắn, chỉ rõ trường cần sửa khi có thể, không đưa raw exception/SQL/token vào dialog hoặc activity log.
- Java Card CAP/installer đã build thành công (converter 26.0: 0 lỗi, 0 cảnh báo); native simulator/thẻ thật chưa được xác minh.
- Người dùng đã cho phép đích telemetry, BE startup và HTTP smoke hiện tại PASS. Full regression BE nghiệp vụ và FE–BE chưa chạy lại; các kết quả 241 BE / 28 tích hợp trước đây vẫn là lịch sử.

Chi tiết theo tab: `docs/DEMO_CASES.md`. Danh mục chờ chạy: `backend/docs/PENDING_VERIFICATION_CASES.md`. Bằng chứng JSON: `docs/test-evidence/` và `backend/docs/test-evidence/`.
<!-- CURRENT_VERIFICATION_END -->


## 1. Chuẩn bị

JDK 21, Maven, backend đang chạy ở địa chỉ bạn có thể truy cập. Với simulator cần Oracle Java Card Tools/Simulator đã cài trên máy.

Trong PowerShell tại root repo frontend:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:JC_HOME_TOOLS = 'D:\SmartCard\tools'
$env:JC_HOME_SIMULATOR = 'D:\SmartCard\simulator'
$env:IDP_API_BASE_URL = 'http://127.0.0.1:8080'
.\build.ps1
```

Nếu thiếu mạng nhưng có cache:

```powershell
python .\scripts\offline_verify.py
.\card-applet\build.ps1
```

Smoke test kiểm tra khởi tạo màn hình và trạng thái nút trước login, không tự chứng minh kết nối backend hay simulator đã thành công.

## 2. Chạy simulator

Mở cửa sổ PowerShell khác:

```powershell
Set-Location "$env:JC_HOME_SIMULATOR\runtime\bin"
.\jcsw.exe -p=9025 -log_level=info
```

Giữ cửa sổ này mở. Trong PowerShell tại repo frontend:

```powershell
.\card-applet\install-simulator.ps1
```

Applet AID `F04B4D4149445001`, PIN lab ban đầu `123456`. Đổi PIN khi cần; sai 5 lần sẽ khóa. Không tự uninstall/reset thẻ đang chứa credential cần giữ.

## 3. Chạy ứng dụng

```powershell
.\run.ps1
```

Bấm Kết nối để kiểm tra backend. Ứng dụng có 11 tab: Kết nối, Thẻ & PIN, Tài khoản, MFA, Khôi phục, Credential, Phiên, Phân quyền, Khóa ký, Dữ liệu, Chờ bổ sung. Mỗi tab có khung case riêng ở phía dưới; chọn dòng để xem thao tác và kỳ vọng.

## 4. Demo Smart Card

1. Login admin bằng mật khẩu đã tạo bên backend.
2. Tạo tài khoản ở tab Tài khoản.
3. Chọn Simulator, đọc card ID/public key.
4. Tải và chọn tài khoản, đối chiếu fingerprint rồi gán thẻ.
5. Đăng nhập bằng username/PIN. Nếu đã bật TOTP, UI sẽ hỏi yếu tố thứ hai.
6. Xem phiên/audit; dùng cửa sổ admin thứ hai để thử khóa, mở khóa và thu hồi phiên.

PIN chỉ gửi APDU tới thẻ; private key không rời applet; token FE chỉ giữ trong RAM.

## 5. Demo MFA, OTP và recovery

- Chọn tài khoản ở tab Tài khoản rồi dùng Gửi lời mời ở Khôi phục.
- Ở local, activation token hiển thị một lần; dùng nút Kích hoạt/đăng ký TOTP.
- Quét QR hoặc nhập secret vào Authenticator, nhập mã để xác nhận, lưu recovery codes riêng.
- Các lần login tiếp theo có TOTP sẽ hỏi thêm mã, không cấp token sau yếu tố đầu tiên.
- OTP email dùng delivery adapter của BE. Local có Hộp thư test yêu cầu admin để quan sát code; không ghi OTP vào activity log.
- Recovery code dùng một lần, thu hồi credential/phiên cũ và yêu cầu đăng ký MFA mới. Sau khôi phục cần đăng ký lại phương thức chính, ví dụ thẻ hoặc passkey; không giả định mật khẩu cũ còn hiệu lực.

Nếu cần đọc hộp thư test trong lúc user nhập OTP, dùng cửa sổ admin thứ hai. Hộp thư test không có ở production.

## 6. Kết nối và lỗi

- Backend không chạy/mất mạng: UI báo lỗi; không bấm lặp thao tác tạo/gán khi chưa biết kết quả lần trước.
- Token hết hạn: API client thử refresh; phiên bị thu hồi thì phải login lại.
- `SocketCardTerminalProvider` thiếu: kiểm tra SDK, chạy bằng run.ps1 để thêm module provider.
- `SW=6A82`: chưa cài applet đúng AID.
- `63Cx`: sai PIN, còn x lần.
- `410`: challenge/transaction hết hạn hoặc đã dùng, bắt đầu luồng mới.
- `STEP_UP_REQUIRED`: login lại đủ các yếu tố cho thao tác nhạy cảm.
- Đổi simulator/PCSC cần đọc lại thẻ; reset simulator có thể đổi card ID/khóa và phải gán lại ở BE.

Đóng cửa sổ để dừng FE. Database và secret backend không nằm trong repo này.

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

Báo cáo JSON được lưu trong `docs/test-evidence/` để giữ bằng chứng khi tách repository.

Chưa được kiểm chứng bằng các báo cáo này: kết nối MySQL thật, Oracle Simulator native, đầu đọc/thẻ thật, SMTP thật, TLS và kiểm thử tải.

Chạy lại: `python scripts/offline_verify.py`; kiểm tra tích hợp tùy chọn: `python scripts/integration_with_backend.py --backend D:\ATBM-SYSTEM\backend`. Hai repository vẫn build độc lập.
<!-- FE_TEST_RESULTS_END -->

## Tra cứu case theo tab

| Tab | Mã case | Nội dung |
|---|---|---|
| Kết nối | NET-01 → NET-04 | 4 kịch bản |
| Thẻ & PIN | CARD-01 → CARD-06 | 6 kịch bản |
| Tài khoản | ACC-01 → ACC-05 | 5 kịch bản |
| MFA | MFA-01 → MFA-05 | 5 kịch bản |
| Khôi phục | REC-01 → REC-05 | 5 kịch bản |
| Credential | CRED-01 → CRED-05 | 5 kịch bản |
| Phiên | SES-01 → SES-05 | 5 kịch bản |
| Phân quyền | RBAC-01 → RBAC-05 | 5 kịch bản |
| Khóa ký | KEY-01 → KEY-04 | 4 kịch bản |
| Dữ liệu | DATA-01 → DATA-04 | 4 kịch bản |
| Chờ bổ sung | PLAN-01 → PLAN-03 | 3 kịch bản |

“Thao tác FE” là luồng có nút thực hiện; “Test tự động” dùng fixture; “Kiểm tra API” cần bộ test/API; “Chưa triển khai” chưa có luồng đầy đủ. Xem `docs/DEMO_CASES.md` cho từng bước.

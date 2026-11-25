# Demo SmartCard trên UI

Giữ ba chế độ: **SOFTWARE** (thẻ phần mềm), **SIMULATOR** (Oracle Java Card), **PCSC** (thẻ thật qua đầu đọc). Database lưu đúng chế độ đã chọn; không đổi thẻ phần mềm thành thẻ thật.

## Khởi động

Backend dùng profile local, có `app.allow-simulator=true`; dùng MySQL theo hướng dẫn khởi động của backend. Ví dụ BE cổng 18080:

```powershell
cd D:\ATBM-SYSTEM\backend
.\start-mysql.ps1
.\run.ps1 -UseCachedRuntime -Port 18080
```

Frontend:

```powershell
cd D:\ATBM-SYSTEM\frontend
python scripts/offline_verify.py
npm test
npm run build
.\run.ps1 -ApiBaseUrl http://127.0.0.1:18080 -DemoCard
```

Mở http://127.0.0.1:8787. `-DemoCard` bật công cụ demo cục bộ. Khi không bật, các API tạo/nạp thẻ phần mềm bị từ chối. Không thay đổi cơ chế phân quyền backend.

## Luồng chính: thẻ phần mềm

1. Đăng nhập tài khoản quản trị hiện có. Nếu cần thay đổi phương thức xác thực hoặc quản trị đặc quyền, hoàn tất MFA theo yêu cầu máy chủ.
2. Tạo tài khoản demo ở **Tài khoản**; dùng tên đăng nhập 3–64 ký tự chữ/số/dấu chấm/gạch dưới/gạch ngang.
3. Vào **Thẻ thông minh**, chọn **Thẻ phần mềm** → **Tạo thẻ trắng mới** → **Khởi tạo applet mô phỏng**. RSA 2048 được tạo trong tiến trình agent; PIN ban đầu 123456.
4. **Đọc thông tin thẻ**, đối chiếu fingerprint → **Gán thẻ cho tài khoản**. Backend lưu credential, Card ID, public key, thuật toán và transport SOFTWARE trong database, đồng thời kích hoạt tài khoản.
5. Chọn tài khoản trong **Thẻ đã lưu và lịch sử trên database**, bấm **Tải từ database** để đọc lại trạng thái.
6. Đổi PIN bằng PIN hiện tại + PIN mới + nhập lại PIN. PIN mới phải có 6–12 chữ số.
7. Đăng xuất, chọn **Thẻ thông minh**, đúng chế độ **Thẻ phần mềm**, nhập username và PIN mới. Agent ký challenge; backend xác minh rồi cấp phiên/token hoặc yêu cầu MFA nếu tài khoản đã bật MFA.
8. Quay lại Thẻ thông minh để xem sự kiện CARD_PROOF / LOGIN và thử thu hồi thẻ. Thu hồi vô hiệu hóa credential và các phiên liên quan.

Thẻ phần mềm và private key chỉ ở bộ nhớ agent, mất khi agent dừng; bản đăng ký trên MySQL vẫn còn. Tạo lại thẻ sẽ có định danh/khóa mới và phải đăng ký lại. Chế độ này mô phỏng giao thức APDU, **không chạy file CAP hay cung cấp bảo vệ private key bằng chip**.

## Nạp code thật vào Oracle Simulator

1. Cài đầy đủ Java Card Tools/Simulator và đặt JC_HOME_TOOLS / JC_HOME_SIMULATOR nếu khác đường dẫn mặc định.
2. Build applet bằng `frontend/card-applet/build.ps1` để tạo CAP và installer từ IdentityApplet.java.
3. Khởi động `runtime/bin/jcsw.exe` của Oracle Simulator ở cổng 9025.
4. Trên UI chọn **Oracle Java Card Simulator** → **Nạp applet CAP vào Oracle Simulator**.
5. Đọc/gán thẻ và đăng nhập như luồng trên. Database ghi transport SIMULATOR. Nạp lại không tự gỡ applet, không đổi khóa/PIN.

UI chỉ nạp file CAP của dự án bằng installer cố định, không nhận lệnh shell, đường dẫn hay CAP tùy ý từ trình duyệt. Nếu thiếu CAP/SDK/installer hoặc simulator không hoạt động, UI thông báo lỗi và không giả lập thành công.

Tại thời điểm triển khai, D:\SmartCard\simulator thiếu runtime/bin/jcsw.exe; chưa thể xác nhận nạp CAP thực tế trong môi trường này.

## Các tình huống demo lỗi

- Thẻ trắng: đọc thẻ báo APPLET_NOT_FOUND; nạp applet rồi đọc lại.
- Rút thẻ: các thao tác tiếp theo báo CARD_NOT_AVAILABLE; chọn thẻ và đưa vào lại.
- PIN sai: mỗi lần sai giảm bộ đếm; lần thứ 5 khóa PIN. PIN đúng cũng không mở được sau khi đã khóa. Tạo thẻ mới để tiếp tục demo; không có nút bypass mở khóa.
- Đổi PIN: PIN cũ không dùng được, PIN mới ký được. PIN nhập lại không khớp được chặn trên UI.
- Challenge: **Lấy challenge & ký** → **Thử chữ ký sai** bị từ chối; sau đó chữ ký gốc vẫn có thể xác minh.
- Phát lại: xác minh thành công rồi bấm xác minh lần nữa; server trả CHALLENGE_EXPIRED.
- Hết hạn: lấy challenge, chờ quá 60 giây rồi xác minh; server từ chối.
- Thu hồi: lấy challenge trước, thu hồi thẻ bằng phiên admin khác, xác minh challenge cũ; server từ chối.
- Đăng ký trùng, public key sai, dữ liệu thiếu/sai, không đủ quyền: server trả lỗi có cấu trúc. Không chèn credential mồ côi khi đăng ký trùng.

Panel thử chữ ký không thay phiên đăng nhập của người thao tác; nếu backend cấp phiên thử, panel đăng xuất phiên đó ngay. Nếu cần MFA, dùng màn hình đăng nhập để hoàn tất đầy đủ.

Nhật ký APDU phần mềm chỉ chứa header, số byte và SW; không chứa PIN/private key. PIN sai trên thẻ không đi tới backend; lịch sử backend ghi các lần xác minh chữ ký và đăng nhập.

## Kiểm thử

```powershell
cd D:\ATBM-SYSTEM\backend
python scripts/prepare_cached_runtime.py
cd D:\ATBM-SYSTEM\frontend
python scripts/offline_verify.py
npm test
npm run build
python scripts/check_card_demo.py
```

Test tích hợp dùng Spring Security/controller/service/repository thật và H2 riêng, không sửa database MySQL hiện có. Kiểm tra handler agent dùng HttpExchange trong bộ nhớ, không phải kiểm chứng TCP/HTTP hay trình duyệt. Chạy trên môi trường cho phép loopback để kiểm tra thêm `python scripts/check_web_bridge.py` và demo UI thủ công.

## Chuẩn bị Maven cache có quyền ghi

Khi Maven cache người dùng bị chặn ghi, có thể sao chép các artifact hiện có vào workspace và phục hồi POM gốc được nhúng trong JAR:

```powershell
cd D:\ATBM-SYSTEM\frontend
python scripts/prepare_maven_cache.py --destination ../backend/.cache/m2
.\build.ps1 -MavenRepository D:/ATBM-SYSTEM/backend/.cache/m2
```

Công cụ không tải mạng, không sửa cache gốc, không thay phiên bản hoặc tự tạo POM thay thế. Artifact chưa có trên máy vẫn phải tải từ Maven Central khi kết nối được. Lần kiểm tra gần nhất còn thiếu parent POM Surefire 3.5.6 và fastdoubleparser 2.0.1, nên chưa thể đánh dấu Maven release build PASS.

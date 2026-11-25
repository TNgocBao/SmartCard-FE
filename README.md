# KMA Identity Web

Giao diện **React** thay thế hoàn toàn Java Swing. Java còn lại là dịch vụ không giao diện để phục vụ web, chuyển tiếp API và giao tiếp Java Card/PCSC. Không còn cửa sổ desktop hoặc phụ thuộc GUI.

## Chạy nhanh

Yêu cầu: Node.js 22+, JDK 21; Maven cho build đầy đủ. Backend chạy riêng, ví dụ cổng 18080 theo cấu hình lab hiện tại.

```powershell
cd D:\ATBM-SYSTEM\frontend
.\build.ps1 -SkipApplet
.\run.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

Mở **http://127.0.0.1:8787**. Mật khẩu admin là mật khẩu bootstrap bạn đã đặt ở backend, không có mật khẩu mặc định trong FE.

Nếu Maven Central không truy cập được nhưng đã có JAR trong cache:

```powershell
python scripts/offline_verify.py
npm install
npm test
npm run build
.\run.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

`npm install` lần đầu cần kết nối npm registry. Có `package-lock.json` thì dùng `npm ci` cho các lần cài sau. Khi chưa build thành công React, chưa thể mở giao diện web.

## Phát triển giao diện

Terminal 1:

```powershell
.\run.ps1 -Dev -ApiBaseUrl http://127.0.0.1:18080
```

Terminal 2:

```powershell
npm install
npm run dev
```

Mở **http://127.0.0.1:5173**. Vite hỗ trợ cập nhật giao diện ngay khi sửa mã. Mọi API và thao tác thẻ được chuyển tiếp đến dịch vụ cục bộ 8787; không cần mở CORS cho backend.

## Chức năng

- Đăng nhập bằng mật khẩu, OTP email hoặc thẻ + PIN; bước MFA/TOTP; kích hoạt và khôi phục tài khoản.
- Tổng quan, tài khoản, khóa/mở khóa, gửi lời mời, reset MFA, thu hồi phiên và cấp vai trò theo tài khoản được chọn.
- Đọc thẻ Simulator/PCSC, đối chiếu fingerprint, gán thẻ và đổi PIN.
- Phương thức xác thực cá nhân, QR TOTP và mã khôi phục hiển thị một lần.
- Phiên đăng nhập, vai trò/quyền, chuẩn bị/xoay/thu hồi khóa ký và JWKS.
- Dữ liệu hệ thống với biểu mẫu theo trường, phân trang và tìm kiếm trong trang hiện tại.
- Hướng dẫn và hộp thư lab. Chức năng OIDC/SSO/consent và giao diện Passkey chưa hoàn thiện.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `src/web/` | React, giao diện responsive và API client |
| `tests/` | Kiểm thử API client JavaScript |
| `src/main/java/.../WebApplication.java` | Web host, proxy API và cầu nối thẻ, chỉ bind 127.0.0.1 |
| `src/main/java/.../card/` | APDU / PCSC / Oracle Simulator |
| `card-applet/` | Applet và công cụ cài đặt giữ nguyên |
| `integration-tests/` | Contract harness Java độc lập, không phải test giao diện React |
| `docs/history/` | Tài liệu và kết quả giao diện cũ, không chứng minh bản React |

Token chỉ giữ trong bộ nhớ trang, không lưu localStorage/cookie; tải lại trang cần đăng nhập lại. PIN chỉ gửi đến dịch vụ thẻ cục bộ, không chuyển đến backend. Không tự thử lại thao tác thẻ khi lỗi. Backend tiếp tục kiểm tra phân quyền của mọi yêu cầu.

Dịch vụ web hỗ trợ sử dụng cục bộ. Triển khai public cần thiết kế HTTPS, origin và mô hình bridge riêng; không đổi bind address để công khai dịch vụ đọc thẻ.

Hướng dẫn chi tiết: [HUONG_DAN_KHOI_DONG_FE.md](HUONG_DAN_KHOI_DONG_FE.md). Kết quả kiểm tra: [docs/TEST_CASE_RESULTS.md](docs/TEST_CASE_RESULTS.md).

## Demo thẻ trên UI

Chạy `run.ps1 -ApiBaseUrl http://127.0.0.1:18080 -DemoCard`. Có thẻ phần mềm không cần SDK, công cụ nạp CAP vào Oracle Simulator và PC/SC cho thiết bị thật. Xem [luồng demo, kiểm thử và giới hạn](docs/SMART_CARD_DEMO.md).

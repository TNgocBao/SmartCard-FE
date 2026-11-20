# Chạy giao diện React

## 1. Khởi động backend

Tại D:\ATBM-SYSTEM, chạy backend theo tài liệu riêng. Với cấu hình lab đã dùng cổng 18080:

```powershell
.\backend\run.ps1 -UseCachedRuntime -Port 18080
```

MySQL cần đang chạy; backend có thể hỏi mật khẩu database/bootstrap. Kiểm tra http://127.0.0.1:18080/api/v1/status.

## 2. Build frontend

```powershell
cd D:\ATBM-SYSTEM\frontend
.\build.ps1 -SkipApplet
```

Lựa chọn dùng cache Java khi Maven không truy cập được:

```powershell
python scripts/offline_verify.py
npm install
npm test
npm run build
```

Cài thư viện npm lần đầu cần internet. Build bridge thành công không đồng nghĩa React đã build. Cần có `dist/index.html` trước khi chạy bản thông thường.

## 3. Chạy web

```powershell
.\run.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

Mở http://127.0.0.1:8787. Chọn Mật khẩu, Email OTP hoặc Thẻ thông minh. Đăng nhập admin bằng mật khẩu bootstrap của backend. Nếu được yêu cầu MFA, nhập mã từ Authenticator; kích hoạt lần đầu dùng token được quản trị viên gửi.

Khi sửa giao diện, dùng `run.ps1 -Dev -ApiBaseUrl http://127.0.0.1:18080`, mở terminal khác chạy `npm run dev`, rồi truy cập http://127.0.0.1:5173.

## 4. Thẻ thông minh

Giữ nguyên SDK và applet trong `card-applet/`; xem [giao thức APDU](docs/SMART_CARD_PROTOCOL.md). Build/cài applet và khởi động Oracle Simulator theo hướng dẫn card-applet. Đặt `JC_HOME_SIMULATOR` nếu simulator không ở D:\SmartCard\simulator. Script chạy sẽ nạp socketprovider.jar nếu tìm thấy.

Đăng nhập admin → Thẻ thông minh → chọn Simulator hoặc PCSC → Đọc thẻ → đối chiếu fingerprint → Gán thẻ cho tài khoản. Để thử người dùng, mở tab trình duyệt thứ hai và đăng nhập Thẻ thông minh bằng username/PIN. Mỗi tab có phiên riêng trong bộ nhớ.

PIN ban đầu applet lab là 123456, sai 5 lần sẽ bị khóa. Đổi PIN tại trang Thẻ thông minh sau khi đọc thẻ. Không có tính năng mở khóa PIN từ xa.

## 5. Kiểm tra

```powershell
node --test tests/api.test.js
python scripts/offline_verify.py
npm run build
python scripts/check_web_bridge.py
```

HTTP bridge test dùng upstream thử nghiệm, tự khởi động/dừng tiến trình riêng; cổng 8787 phải đang trống. Không thử bằng MySQL hay thẻ thật. Contract Java FE/BE cũ chỉ kiểm tra lớp API/APDU, không xác minh giao diện React.

## Xử lý lỗi

| Hiện tượng | Cách xử lý |
|---|---|
| npm ENOTFOUND | Kiểm tra mạng/DNS đến registry.npmjs.org rồi chạy lại npm install |
| Chưa có dist/index.html | Chạy npm run build sau khi cài thư viện thành công |
| Không kết nối máy chủ | Xác nhận BE chạy đúng cổng; truyền đúng -ApiBaseUrl khi chạy dịch vụ web |
| Cổng 8787/5173 bận | Dừng tiến trình web/dev trước đó bằng Ctrl+C tại terminal đã chạy |
| Chưa kết nối được thẻ | Kiểm tra simulator cổng 9025, applet và JC_HOME_SIMULATOR; hoặc cắm thẻ PCSC |
| Yêu cầu xác thực lại | Đăng nhập lại đủ các yếu tố rồi thực hiện thao tác nhạy cảm |
| Tải lại trang bị đăng xuất | Đây là hành vi dự kiến vì token chỉ nằm trong bộ nhớ |

Các hướng dẫn desktop cũ được lưu trong docs/history/ để đối chiếu; không còn áp dụng cho bản hiện tại.

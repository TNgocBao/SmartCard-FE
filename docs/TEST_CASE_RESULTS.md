# Kiểm chứng bản React — 20/11/2026

| Hạng mục | Kết quả | Giới hạn |
|---|---|---|
| JavaScript API client | 7/7 PASS | Refresh đồng thời, phản hồi muộn sau logout, lỗi write, token bridge, token xoay không hợp lệ và JSON sai; dùng fetch fixture |
| Java API compatibility | 25/25 PASS | Transport fixture; không phải React hoặc kết nối thật |
| Java bridge | Biên dịch PASS trên JDK 21 | Không còn phụ thuộc Swing/GUI trong mã main hoặc kiểm thử |
| React JSX / CSS | esbuild kiểm tra cú pháp và bundling PASS | Các thư viện React/ReactDOM/lucide/qrcode được đánh dấu external; không phải production build |
| npm install / Vite production build | BỊ CHẶN | DNS registry.npmjs.org trả ENOTFOUND trong môi trường thực thi; chưa cài được thư viện |
| HTTP bridge | BỊ CHẶN TRƯỚC KHI CHẠY CASE | Java HttpClient không khởi tạo được vì môi trường từ chối loopback: Permission denied: connect |
| Contract FE/BE | CHƯA CHẠY LẠI | Backend source khác báo cáo xác minh gần nhất, script dừng đúng kiểm tra phiên bản |
| Tương tác trình duyệt / responsive / MFA / thẻ thật | CHƯA KIỂM CHỨNG | Cần cài thư viện và chạy app ngoài môi trường giới hạn mạng trước khi kiểm tra |

Không dùng các kết quả Swing lịch sử làm bằng chứng cho UI React. Không có báo cáo khẳng định toàn bộ chức năng đã PASS end-to-end.

## Chạy lại

```powershell
node --test tests/api.test.js
python scripts/offline_verify.py
npm install
npm run build
python scripts/check_web_bridge.py
```

Sau đó chạy BE + web theo README, thực hiện các bước trong DEMO.md và kiểm tra thêm:

- Đăng nhập sai/đúng; MFA sai/đúng/hủy; không có quyền quản trị trước khi hoàn tất.
- Đăng nhập Email OTP, thẻ + PIN; kích hoạt QR TOTP; khôi phục và lưu mã một lần.
- Hộp thoại lỗi hiển thị ngay trong dialog; Escape/hủy và focus bằng bàn phím.
- Tìm kiếm, phân trang; đổi loại dữ liệu không hiển thị dữ liệu cũ.
- Khóa/mở tài khoản, cấp/thu hồi vai trò, thu hồi phiên/credential, vòng đời khóa.
- Kiểm tra kích thước 1440px, 1024px và 390px; menu di động, bảng cuộn ngang và giảm chuyển động.

Bằng chứng cũ giữ trong history/ và test-evidence/. `web-bridge-latest.json` chỉ mô tả build/API Java của bản hiện tại.

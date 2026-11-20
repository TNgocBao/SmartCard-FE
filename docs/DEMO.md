# Demo React Web

1. Khởi động BE, build React và chạy bridge theo README. Mở http://127.0.0.1:8787.
2. Đăng nhập admin bằng mật khẩu, hoàn tất MFA khi được yêu cầu.
3. Tài khoản → Tạo tài khoản → điền thông tin → Thao tác → Gửi lời mời kích hoạt.
4. Người nhận mở màn hình đăng nhập → Kích hoạt tài khoản → nhập token → quét QR Authenticator → xác nhận mã.
5. Với thẻ: admin mở Thẻ thông minh → chọn đầu đọc → Đọc thẻ → đối chiếu fingerprint → gán đúng tài khoản.
6. Mở tab thứ hai → chọn Thẻ thông minh trên màn hình đăng nhập → nhập username/PIN → xem Phiên đăng nhập.
7. Admin thử khóa/mở khóa tài khoản hoặc thu hồi phiên. Người dùng kiểm tra phiên bị vô hiệu hóa và đăng nhập lại sau khi mở khóa.
8. Bảo mật của tôi → thiết lập TOTP / tạo mã khôi phục / thu hồi phương thức. Đọc xác nhận trước khi tiếp tục.
9. Vai trò & quyền → tạo vai trò / chỉnh quyền; cấp vai trò tại Thao tác của một tài khoản.
10. Khóa ký → chuẩn bị NEXT → kích hoạt → thu hồi khóa cũ. Máy chủ yêu cầu MFA gần đây.

Đây là kịch bản cần chạy kiểm tra thủ công, không phải các bước đã PASS tự động. Không thử sai PIN 5 lần trừ khi muốn khóa thẻ lab.

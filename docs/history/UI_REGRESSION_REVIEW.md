# Rà soát giao diện ngày 20/11/2026

Build tương thích bằng cache JDK 21 đã PASS. Kết quả hiện tại: 25 kiểm tra API, 37 kiểm tra lỗi/PIN, 30 kiểm tra cấu trúc tab/phân quyền nút, **46 kiểm tra luồng Swing** và desktop smoke 11 tab. Danh mục có 57 định nghĩa kịch bản; số định nghĩa không phải số case nghiệp vụ đã PASS.

## Đã sửa và kiểm tra

| Nhóm | Hành vi đã kiểm tra |
|---|---|
| Đăng nhập / MFA | Đúng/sai mật khẩu, backend từ chối MFA, hủy nhập mã, MFA thành công; không cấp quyền sau bước thất bại hoặc hủy. |
| Kích hoạt / TOTP | Hủy nhập activation token giữ phiên hiện có; hủy QR thay TOTP không mất phiên; xác nhận thay TOTP thành công yêu cầu đăng nhập lại. |
| Thẻ | Đọc RSA public key, đối chiếu lại thẻ trước provisioning, chặn đổi thẻ, PIN sai, lỗi kết nối, ký và xác minh RSA-2048, chữ ký bị cắt; PIN không xuất hiện trong API payload. |
| Credential | Đóng cửa sổ, chưa chọn dòng, hủy xác nhận và thu hồi thành công; thành công xóa token và dữ liệu riêng trên FE. |
| Phiên | Tải danh sách, hủy thu hồi, thu hồi phiên khác rồi tải lại trạng thái, thu hồi phiên hiện tại và xử lý refresh bị từ chối. |
| RBAC / khóa | Hủy tạo role không gửi POST; key.read chỉ có lựa chọn đóng; giảm quyền hoặc nhận ACCESS_DENIED vô hiệu hóa nút nhạy cảm. |
| Dữ liệu | Chọn đúng ID sau sắp xếp bảng; đổi bảng xóa editor/dòng cũ; JSON sai hoặc array bị chặn trước HTTP. |
| Trạng thái UI | Chặn thao tác trùng khi busy; bỏ callback cũ sau đổi ngữ cảnh; logout khi timeout vẫn xóa token/PIN/bảng/editor/thẻ; đổi BE hoặc đóng cửa sổ xóa dữ liệu cũ. |

Mỗi kiểm tra riêng cùng thời điểm và SHA-256 nguồn nằm trong `test-evidence/frontend-ui-flow-latest.json`. Toàn bộ bảng kết quả nằm ở `TEST_CASE_RESULTS.md`. Bộ test gọi controller Swing thực trên EDT, dùng phản hồi API trong tiến trình, dialog có đáp án cố định và phần mềm APDU ký RSA thật. Bộ test không mở socket và không khởi động BE.

## Tự chạy lại

Từ `D:\ATBM-SYSTEM`:

```powershell
python .\frontend\scripts\offline_verify.py
.\frontend\run.ps1
```

Lệnh đầu build và chạy test, tự cập nhật báo cáo. Lệnh sau mở FE để test thủ công; cần BE chạy riêng theo hướng dẫn ở root.

## Thứ tự test thủ công với BE

1. Tab **Kết nối**: kết nối BE, đăng nhập admin và hoàn tất MFA.
2. Tab **Tài khoản**: tải danh sách, tạo một tài khoản lab mới.
3. Tab **Thẻ & PIN**: đọc thẻ, tải tài khoản, chọn đúng người nhận và đối chiếu fingerprint trước khi gán. Nếu đổi thẻ giữa chừng, FE phải yêu cầu đọc lại.
4. Đăng xuất admin, đăng nhập bằng username/PIN của tài khoản đã gán; hoàn tất MFA nếu được yêu cầu.
5. Tab **Phiên**: tải phiên, thử hủy xác nhận thu hồi trước; chỉ thu hồi phiên lab cần kiểm tra. Thu hồi phiên hiện tại phải kết thúc đăng nhập.
6. Tab **MFA / Credential**: hủy dialog trước để kiểm tra không phát sinh thao tác; chỉ xác nhận thay TOTP/thu hồi credential khi đã chuẩn bị phương thức khôi phục.
7. Với admin, tab **Dữ liệu**: đọc bảng, đổi bảng sau khi nhập editor; nội dung cũ phải biến mất. JSON sai phải có lỗi ngắn.
8. Sau logout hoặc đổi backend: các bảng, editor, PIN và thông tin thẻ cũ phải trống, nút quản trị bị khóa.

Case chi tiết theo từng tab: `DEMO_CASES.md`. Không thử PIN sai liên tục trên thẻ cần giữ vì applet có giới hạn số lần thử.

## Phạm vi chưa được xác minh ở lượt này

- BE/MySQL thật, Oracle Simulator native, PC/SC/thẻ vật lý, SMTP, TLS và tải đồng thời thực tế.
- Regression nghiệp vụ BE và FE–BE hiện tại vẫn chưa có PASS mới; lần chạy đầy đủ trước bị duyệt tự động chặn do telemetry. Các kết quả lịch sử được đánh dấu riêng trong báo cáo.
- OIDC/SSO, consent/policy và một phần lifecycle định danh/tổ chức còn thiếu nghiệp vụ theo ba tài liệu gốc. Tab **Chờ bổ sung** ghi rõ trạng thái; xem thêm `backend/docs/REQUIREMENT_COVERAGE.md` trong workspace hoặc tài liệu tương ứng của repo backend.

Không dùng kết quả giả lập FE này để kết luận toàn bộ 3 tài liệu đã hoàn thành hoặc môi trường production đã sẵn sàng.

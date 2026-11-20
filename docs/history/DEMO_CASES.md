# Các case theo từng tab FE

11 tab · 57 định nghĩa kịch bản. Đây là hướng dẫn thao tác, không phải 57 kết quả PASS. Kết quả thực thi xem TEST_CASE_RESULTS.md.


## Kết nối

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| NET-01 | BE sẵn sàng | Thao tác FE | Nhập địa chỉ BE rồi bấm Kết nối. | Hiển thị trạng thái UP. |
| NET-02 | BE mất kết nối | Test tự động | Dùng transport giả lập timeout hoặc mất kết nối. | Lỗi ngắn; không tự gửi lại thao tác ghi. |
| NET-03 | Địa chỉ không hợp lệ | Thao tác FE | Nhập URL sai hoặc HTTP ngoài localhost. | Từ chối cấu hình; không lộ thông tin trong URL. |
| NET-04 | Đăng nhập quản trị | Thao tác FE | Bấm Đăng nhập và hoàn tất MFA nếu được yêu cầu. | Hiển thị đúng người dùng và quyền. |
| NET-05 | Đổi backend | Test tự động | Đăng nhập, tải dữ liệu rồi kết nối BE khác. | Xóa token, bảng, PIN và thông tin thẻ của BE trước. |

## Thẻ & PIN

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| CARD-01 | Đọc thẻ | Thao tác FE | Chọn simulator hoặc PC/SC, bấm Đọc thẻ. | Có card ID, public key và fingerprint. |
| CARD-02 | Gán thẻ | Thao tác FE | Admin chọn tài khoản, đối chiếu fingerprint rồi gán thẻ. | Credential ACTIVE; tài khoản được kích hoạt. |
| CARD-03 | PIN đúng | Thao tác FE | Nhập tài khoản và PIN đúng, bấm Đăng nhập bằng thẻ. | BE xác minh chữ ký; tiếp tục MFA nếu cần. |
| CARD-04 | PIN sai một lần | Thao tác FE | Nhập sai PIN đúng một lần trên thẻ lab. | Không cấp token; hiện số lần thử còn lại. |
| CARD-05 | Đổi PIN | Thao tác FE | Nhập PIN cũ và PIN mới gồm 6–12 số. | PIN mới dùng được; PIN cũ bị từ chối. |
| CARD-06 | Lỗi applet / khóa PIN | Test tự động | Giả lập 6A82, 6983, 63Cx và dữ liệu APDU lỗi. | Thông báo đúng lỗi; không lộ hoặc giữ bộ đệm PIN. |
| CARD-07 | Đổi thẻ trước khi gán | Test tự động | Đọc thẻ A, thay thẻ B rồi bấm Gán thẻ. | Đọc lại và từ chối khi card ID hoặc public key thay đổi. |

## Tài khoản

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| ACC-01 | Tạo tài khoản | Thao tác FE | Admin tạo username, họ tên và email. | Tạo Person–Identity–Account; trạng thái chờ kích hoạt. |
| ACC-02 | Username trùng | Thao tác FE | Tạo lại cùng username. | Báo dữ liệu trùng, không tạo dữ liệu dở dang. |
| ACC-03 | Khóa / mở khóa | Thao tác FE | Dùng cửa sổ admin khóa rồi mở khóa thành viên. | Phiên cũ bị thu hồi; mở khóa không khôi phục token cũ. |
| ACC-04 | Thu hồi mọi phiên | Thao tác FE | Admin chọn tài khoản rồi bấm Thu hồi mọi phiên. | Các cửa sổ của tài khoản phải đăng nhập lại. |
| ACC-05 | Bảo vệ admin cuối | Thao tác FE | Thử khóa quản trị viên hoạt động cuối cùng. | 409 LAST_SUPERUSER_PROTECTED. |

## MFA

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| MFA-01 | Kích hoạt TOTP | Thao tác FE | Dùng activation token, quét QR và nhập mã đầu tiên. | Chỉ kích hoạt khi mã đúng; secret hiển thị một lần. |
| MFA-02 | Đăng nhập OTP email | Thao tác FE | Nhập username, lấy OTP từ email hoặc hộp thư lab. | Mã đúng mới qua bước đầu; cần TOTP nếu tài khoản đã bật. |
| MFA-03 | Sai / dùng lại mã | Thao tác FE | Nhập mã sai hoặc dùng lại mã đã xác minh. | Từ chối; không bỏ qua bước MFA. |
| MFA-04 | TOTP ngoài cửa sổ | Test tự động | Giả lập mã hết hạn và time-step đã dùng. | Từ chối mã; không cấp phiên AAL2. |
| MFA-05 | Hộp thư lab | Thao tác FE | Admin nhập delivery ID của yêu cầu OTP. | Chỉ admin local đọc được; production không có hộp thư này. |
| MFA-06 | Hủy bước MFA / QR | Test tự động | Hủy nhập mã sau login hoặc hủy QR thay TOTP. | Không cấp phiên mới; hủy thay TOTP giữ phiên hiện có. |

## Khôi phục

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| REC-01 | Mời kích hoạt | Thao tác FE | Chọn tài khoản ở tab Tài khoản rồi gửi lời mời. | Token có TTL, dùng một lần; production gửi qua outbox. |
| REC-02 | Tạo mã khôi phục | Thao tác FE | Đăng nhập đủ yếu tố rồi tạo lại recovery codes. | Nhận 10 mã một lần; mã cũ mất hiệu lực. |
| REC-03 | Dùng mã khôi phục | Thao tác FE | Nhập username và một recovery code hợp lệ. | Phiên/credential cũ bị thu hồi; thiết lập lại TOTP. |
| REC-04 | Dùng lại mã | Thao tác FE | Thử lại recovery code đã sử dụng. | Bị từ chối; không tạo phiên mới. |
| REC-05 | Admin reset MFA | Thao tác FE | Admin chọn tài khoản, nhập lý do rồi reset. | Thu hồi phiên/credential, phát hành lời mời và ghi audit. |

## Credential

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| CRED-01 | Xem credential | Thao tác FE | Mở Credential của tôi. | Chỉ hiện metadata của bản thân; không hiện secret. |
| CRED-02 | Thu hồi credential | Thao tác FE | Đăng nhập AAL2 gần đây, chọn credential cần thu hồi. | Credential bị thu hồi và các phiên kết thúc. |
| CRED-03 | Phương thức cuối | Thao tác FE | Thử gỡ phương thức xác thực cuối cùng. | 409; cần thêm phương thức khác trước. |
| CRED-04 | Đổi / tắt TOTP | Thao tác FE | Thiết lập TOTP mới hoặc yêu cầu tắt TOTP. | Yêu cầu AAL2; bảo vệ MFA của admin và thu hồi phiên. |
| CRED-05 | Passkey hợp lệ / sai | Kiểm tra API | Dùng fixture ký WebAuthn với origin/RP ID/UV khác nhau. | Chấp nhận proof hợp lệ, từ chối sai/replay/counter giảm. |

## Phiên

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| SES-01 | Danh sách phiên | Thao tác FE | Bấm Tải phiên. | Chỉ thấy phiên của tài khoản đang đăng nhập. |
| SES-02 | Thu hồi một phiên | Thao tác FE | Chọn dòng phiên rồi thu hồi. | Token của phiên đó không gọi API được nữa. |
| SES-03 | Đăng xuất | Thao tác FE | Bấm Đăng xuất. | Token được xóa khỏi bộ nhớ và phiên bị thu hồi. |
| SES-04 | Refresh / reuse | Test tự động | Giả lập access hết hạn, refresh rotate và dùng lại refresh cũ. | Một lần refresh; reuse thu hồi family. |
| SES-05 | Logout khi đang refresh | Test tự động | Giả lập logout hoặc đổi tài khoản khi request đang chạy. | Không phục hồi phiên cũ hoặc áp dụng kết quả của người cũ. |
| SES-06 | Xóa dữ liệu khi logout | Test tự động | Tải dữ liệu rồi logout; giả lập request cũ trả về muộn. | Xóa bảng/editor/PIN; kết quả cũ không hiển thị lại. |

## Phân quyền

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| RBAC-01 | Tạo role | Thao tác FE | Tạo role, chọn permission từ catalog backend. | Role mới không tự có quyền; chỉ nhận quyền đã chọn. |
| RBAC-02 | Cấp / gỡ role | Thao tác FE | Gán role cho account, rồi thu hồi assignment. | Quyền đổi ở request tiếp theo dù JWT còn hạn. |
| RBAC-03 | Cấp vượt quyền | Thao tác FE | Đăng nhập người được ủy quyền rồi thử cấp quyền cao hơn. | 403; không được tự nâng thành idp.manage. |
| RBAC-04 | Scope / thời hạn | Kiểm tra API | Giả lập assignment theo tổ chức/ứng dụng và khoảng hiệu lực. | Không vượt scope; từ chối thời hạn sai hoặc chồng lấp. |
| RBAC-05 | Role hệ thống | Thao tác FE | Thử sửa SUPER_ADMIN hoặc gỡ admin hoạt động cuối. | 409; bảo toàn quyền quản trị tối thiểu. |
| RBAC-06 | Mất quyền quản trị | Test tự động | Tải lại hồ sơ với quyền thấp hơn hoặc nhận 403. | Xóa dữ liệu quản trị và vô hiệu hóa nút không còn quyền. |

## Khóa ký

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| KEY-01 | Xem public key | Thao tác FE | Mở quản lý khóa ký hoặc đọc JWKS. | Có kid/trạng thái; không có private key. |
| KEY-02 | Chuẩn bị NEXT | Thao tác FE | Dùng GENERATE cho lab hoặc alias PKCS12 đã cấu hình. | Khóa NEXT được công bố trong JWKS. |
| KEY-03 | Xoay khóa | Thao tác FE | Chọn NEXT rồi kích hoạt với overlap 600 giây. | Token mới dùng key mới; token cũ còn trong overlap. |
| KEY-04 | Thu hồi khóa | Thao tác FE | Thử thu hồi ACTIVE, sau đó thử RETIRED trong môi trường lab. | ACTIVE bị chặn; thu hồi RETIRED kết thúc các phiên. |

## Dữ liệu

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| DATA-01 | Đọc catalog | Thao tác FE | Admin tải danh mục và đọc các bảng. | Có 41 bảng nghiệp vụ; trường bí mật được ẩn. |
| DATA-02 | Dữ liệu đầu vào sai | Thao tác FE | Gửi JSON sai cú pháp, thiếu trường hoặc sai kiểu. | 400, lỗi ngắn và tên trường an toàn khi có thể xác định. |
| DATA-03 | Bảng chỉ đọc | Thao tác FE | Thử ghi trực tiếp credential, token, challenge hoặc audit. | Bị từ chối; phải đi qua API nghiệp vụ. |
| DATA-04 | Lỗi / quá tải API | Test tự động | Giả lập 404/405/415/429, lỗi DB và lỗi nội bộ. | Có code + requestId; không lộ SQL, token hay stack trace. |
| DATA-05 | Đổi bảng đang sửa | Test tự động | Nhập JSON ở một bảng rồi chọn bảng khác. | Xóa editor và dòng cũ; không cập nhật nhầm bảng. |

## Chờ bổ sung

| Case | Tên | Cách kiểm tra | Thao tác | Kết quả kỳ vọng |
|---|---|---|---|---|
| PLAN-01 | OIDC / SSO | Chưa triển khai | Authorization Code + PKCE, discovery, ID Token và logout RP. | Chưa triển khai đầy đủ; chưa có PASS. |
| PLAN-02 | Consent / policy | Chưa triển khai | Cấp và thu hồi consent, policy precedence, claim mapping. | Chưa triển khai đầy đủ; chưa có PASS. |
| PLAN-03 | Vòng đời định danh | Chưa triển khai | API Person/Identity, cây tổ chức và membership đầy đủ. | Mới có một phần model/onboarding; còn nghiệp vụ phải hoàn thiện. |

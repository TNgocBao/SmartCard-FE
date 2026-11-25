# Kết quả kiểm thử SmartCard / React — 25/11/2026

| Hạng mục | Kết quả | Phạm vi |
|---|---|---|
| React production build | PASS | Vite bundle đầy đủ với dependency đã cài |
| JavaScript API client + validation form | 12/12 PASS | Refresh/logout/race/lỗi response; required, giới hạn độ dài, PIN, email, lựa chọn và PIN nhập lại |
| Java API client | 25/25 PASS | Transport fixture, không phải HTTP thật |
| Thẻ phần mềm / APDU | 19/19 PASS | Thẻ trắng, nạp lặp, RSA, PIN, đổi PIN, khóa PIN, rút thẻ, khóa khác nhau và redaction |
| Agent khi tắt demo | 2/2 PASS | Từ chối tạo/đọc thẻ phần mềm |
| HTTP handler của agent | 52/52 PASS | HttpExchange trong bộ nhớ; JSON sai/trùng/trailing, thiếu/sai kiểu/thừa trường, body limit, PIN/challenge, Origin/Host/content type và thao tác thẻ |
| FE–BE–database | 76/76 PASS | Client/APDU production + Spring Security/controller/service/repository thật + H2; validation, rollback, lưu đúng SOFTWARE, login/MFA, revoke, replay, expiry, concurrent proof, quyền/scope và history |
| Backend tổng | 291 kiểm tra PASS | Cached compatibility dependencies, H2/MockMvc; xem backend/docs/TEST_CASE_RESULTS.md |
| Kiến trúc / model | PASS | 32 bảng ERD gốc, 41 entity, 897 thuộc tính và ranh giới các tầng |
| Maven release build | CHƯA PASS | Project cache thiếu parent Spring Boot 3.5.16 khi chạy build.ps1 -Offline |
| HTTP loopback / trình duyệt | CHƯA KIỂM CHỨNG | Java bị Permission denied khi kết nối loopback; không coi test handler trong bộ nhớ là HTTP/UI end-to-end |
| Oracle Simulator native / NFC thật | CHƯA KIỂM CHỨNG | Thiếu runtime/bin/jcsw.exe; chưa có kiểm tra thiết bị |
| MySQL theo luồng mới | CHƯA KIỂM CHỨNG | H2 kiểm tra logic/persistence; không thay thế kiểm thử MySQL |

Đây là danh mục hữu hạn các tình huống đã chạy, không phải khẳng định bao phủ mọi lỗi có thể xảy ra. Không dùng kết quả giao diện Swing lịch sử làm bằng chứng cho React.

Bằng chứng: [tổng hợp](test-evidence/card-demo-latest.json), [case FE–BE](test-evidence/card-demo-contract-latest.json), [Java bridge](test-evidence/web-bridge-latest.json).

Chạy lại theo [SMART_CARD_DEMO.md](SMART_CARD_DEMO.md). Các báo cáo lưu dấu vân tay nguồn để nhận diện kết quả cũ. PIN/private key không nằm trong response quản trị hoặc báo cáo case.

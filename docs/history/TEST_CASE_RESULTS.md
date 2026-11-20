# Báo cáo FE

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

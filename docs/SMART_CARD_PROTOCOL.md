# APDU protocol v1

AID applet: `F04B4D4149445001`. CLA nghiệp vụ: `80`, P1/P2: `00`. SELECT dùng ISO `00 A4 04 00`.

| INS | Input | Output | Điều kiện |
|---|---|---|---|
| 10 | Không | Card ID ngẫu nhiên 16 byte | Applet selected |
| 20 | Không | RSA modulus 256 byte | Applet selected |
| 21 | Không | RSA exponent | Applet selected |
| 40 | PIN ASCII 6–12 số | SW | Tối đa 5 lần sai |
| 42 | PIN mới ASCII 6–12 số | SW | VERIFY thành công trước đó |
| 30 | Challenge 32–100 byte | Chữ ký RSA 256 byte | VERIFY thành công; quyền PIN bị reset sau ký |

Chữ ký: SHA256withRSA / RSA PKCS#1 v1.5. Public key gửi BE ở dạng SPKI DER Base64. Không hash challenge thêm ở FE: applet thực hiện SHA-256 trong thuật toán ký. Card ID chỉ dùng lookup, không phải bằng chứng xác thực.

SW: `9000` thành công; `63Cx` PIN sai và còn x lần; `6982` chưa xác minh PIN; `6700` sai chiều dài; `6A80` sai dữ liệu; `6A86` P1/P2 sai; `6D00` INS không hỗ trợ; `6E00` CLA không hỗ trợ.

Challenge có domain separator, nonce 256 bit, ràng buộc account/credential phía BE, TTL 60 giây. BE xác minh chữ ký rồi consume bằng conditional update trong transaction; hai proof đồng thời chỉ một proof thành công. Không truyền PIN đến backend và không xuất private key khỏi applet.

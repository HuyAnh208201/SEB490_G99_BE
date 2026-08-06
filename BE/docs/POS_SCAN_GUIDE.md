# Hướng dẫn POS — Quét mã vạch & tích điểm

Tài liệu cho luồng POS thu ngân: quét barcode → tra DB → thêm vào giỏ, và tra cứu / tích điểm khách hàng.

- **BE**: `SEB490_G99_BE/BE` — Spring Boot, cổng **4313**
- **FE**: `SEP490_G99_FE/FE` — React + Vite, cổng **5175**, proxy `/api` → `localhost:4313`

---

## 1. Chạy dự án

### Backend

```bash
cd SEB490_G99_BE/BE
./mvnw.cmd spring-boot:run
```

Mất khoảng **55–65 giây** để boot. Chờ thấy dòng `Started ApiApplication`.

> ⚠️ Mở FE trước khi BE boot xong thì call API đầu tiên bị `ECONNREFUSED`, trang kẹt ở trạng thái lỗi và **không tự gọi lại**. Cứ F5 lại sau khi BE lên là xong.

Swagger: `http://localhost:4313/swagger-ui/index.html`

### Frontend

```bash
cd SEP490_G99_FE/FE
npm run dev                    # chỉ localhost
npm run dev -- --host 0.0.0.0  # mở cả LAN (test bằng điện thoại cùng wifi)
```

Vite mặc định **chỉ bind localhost** — không có `--host` thì máy khác trong LAN không vào được, mở port ở router cũng vô ích.

### Truy cập từ điện thoại

| Cách | URL | Camera quét được? |
|---|---|---|
| Cùng LAN | `http://<IP-LAN>:5175` | ❌ Không |
| Tunnel HTTPS | `https://<random>.trycloudflare.com` | ✅ Có |

Camera (`getUserMedia`) chỉ chạy trên **HTTPS hoặc localhost**. Vào bằng `http://<IP-LAN>` thì trình duyệt chặn camera — vẫn gõ mã bằng tay được bình thường.

Tạo tunnel HTTPS:

```bash
cd SEP490_G99_FE/FE
npx -y cloudflared tunnel --url http://localhost:5175 --no-autoupdate
```

URL in ra trong log. Lưu ý:

- URL **ngẫu nhiên, đổi mỗi lần chạy lại**.
- DNS cần **~20 giây** mới phân giải được; trước đó curl trả `HTTP 000` / `Non-existent domain` là bình thường.
- Tunnel này **công khai trên internet** — ai có link đều vào được app và API. Demo xong nhớ tắt.

---

## 2. Tài khoản demo

### Canonical demos (kept on shared DB)

Staff demos are ensured on every web BE boot by `DemoAccountsSeeder` (runs even when `app.startup.bootstrap-enabled=false`). Any other user whose email contains `demo` is soft-locked (`status = locked`).

| Email | Password | Role | Notes |
|---|---|---|---|
| `demo_cashier@chainstore.vn` | `123456` | CASHIER (branch 1) | Can open POS shift **without** BM published assignment |
| `demo_is@chainstore.vn` | `123456` | INVENTORY_STAFF (branch 1) | Can run inventory count during store hours (07–22) |
| `demo.customer.silver@chainstore.vn` | `Demo@1234` | CUSTOMER | Mobile app — Silver (points 0) |
| `demo.customer.gold@chainstore.vn` | `Demo@1234` | CUSTOMER | Mobile app — Gold (points 2500) |
| `demo.customer.platinum@chainstore.vn` | `Demo@1234` | CUSTOMER | Mobile app — Platinum (points 5500) |

Customer demos are seeded by CSCSMS `DemoCustomerSeeder` (phone `0912345678` / `79` / `80`).

Ô đăng nhập ghi "username" nhưng **phải nhập email** — `UserModel.getUserName()` trả về email, bảng `users` không có cột username.

Thu ngân đăng nhập xong tự chuyển vào `/pos` (xem `lib/postLoginPath.js`).

Role được phép tạo sản phẩm: **ADMIN, DIRECTOR, BRANCH_MANAGER, INVENTORY_STAFF**. Cashier và Warehouse Manager thì không.

---

## 3. Luồng quét mã

### 3.1. Camera — quét xong tự thêm vào giỏ

1. Màn **Product Cart** → bấm **Scan** → cho phép quyền camera.
2. Chĩa vào mã vạch. Đọc được mã là camera tắt (màn hình đen) và hiện *"Đã đọc mã … — đang tra cứu sản phẩm…"*.
3. Tra DB xong → **tự thêm vào giỏ**, hiện *"Đã đọc mã … → đã thêm "<tên sản phẩm>" vào giỏ."*
4. Tra không ra → báo lỗi **kèm mã đọc được**, ví dụ `Mã 8936221630004: This product is out of stock at your branch.`

### 3.2. Gõ tay / máy quét cầm tay — Enter để tìm, Add item để thêm

1. Gõ (hoặc máy quét bắn) mã vào ô tìm kiếm → **Enter**.
2. Chuỗi từ 6 chữ số trở lên được coi là barcode → gọi `POST /api/barcode/scan`.
3. Tìm thấy → hiện *"Đã tìm thấy: <tên> · Số lượng 1. Bấm Add item để thêm vào giỏ."* — **chưa vào giỏ**.
4. Bấm **Add item** → mới thực sự thêm.

Gõ **tên** hoặc **mã SKU** (có chữ) thì hiện danh sách gợi ý; chọn một dòng → popup chọn số lượng → bấm **Add item**.

> Khác biệt có chủ ý: **camera thì tự thêm** (thao tác nhanh khi bán hàng), **gõ tay thì phải xác nhận** (tránh gõ nhầm).

### 3.3. Dùng điện thoại làm máy quét cho máy bán hàng

Quét trên điện thoại, sản phẩm hiện lên giỏ hàng của **máy bán hàng (web)** — không cần lưu đơn.

**Cách dùng**

1. Cả hai máy đăng nhập **cùng một tài khoản thu ngân** (đó chính là cách ghép cặp, không cần quét QR ghép máy).
2. **Trên điện thoại**: tick ô **"Chế độ máy quét"** dưới ô tìm kiếm. Có dòng xanh báo *"Đang ở chế độ máy quét — giỏ hàng trên máy này không dùng đến."*
3. **Trên máy bán hàng**: để ô đó **TẮT**. Máy này sẽ tự hỏi mã mới mỗi 2 giây.
4. Điện thoại quét (hoặc gõ mã + Enter) → máy bán hàng tự thêm vào giỏ, báo *"Từ điện thoại: đã thêm "<tên>" vào giỏ."*

Ô "Chế độ máy quét" được nhớ trong `localStorage` nên điện thoại không phải bật lại mỗi lần mở trang.

**Cách hoạt động**

- Điện thoại gọi `POST /api/pos/scan-events`. Mã được **kiểm tra ngay bằng luồng quét chuẩn** (không thấy / ngừng bán / sai chi nhánh / hết tồn đều bị chặn), nên **mã hỏng không bao giờ vào hàng đợi** và điện thoại biết lỗi liền.
- Máy bán hàng gọi `GET /api/pos/scan-events?afterId=<con trỏ>` mỗi 2 giây. Lần gọi đầu **không truyền `afterId`** để chỉ lấy con trỏ hiện tại — nhờ vậy máy mở muộn không nuốt lại mã cũ.
- Con trỏ là **id tăng dần**, không phải thời gian, nên không sợ lệch đồng hồ giữa 2 máy và **không bao giờ xử lý lặp một mã**.
- Mã cũ hơn **12 giờ** không được trả về nữa.
- Dùng chuỗi `setTimeout` chứ không phải `setInterval`, để 2 nhịp hỏi không chồng lên nhau khi mạng chậm. Mạng lỗi thì bỏ nhịp đó, nhịp sau hỏi lại.

**Giới hạn**

- Giỏ hàng **vẫn nằm trong trình duyệt máy bán hàng** — F5 là mất. Muốn giỏ sống qua F5 và chia sẻ thật sự giữa nhiều máy thì phải làm giỏ trên server (xem mục 7).
- Trễ tối đa ~2 giây (nhịp hỏi).
- Điện thoại đang ở chế độ máy quét thì giỏ hàng của chính nó không dùng đến.

**Bảng liên quan:** `pos_scan_events` — tự tạo lúc khởi động bởi `PosScanEventTableMigration`.

### 3.3. Điều kiện để quét được

BE (`ProductServiceImpl.scanByBarcode`) chặn theo thứ tự:

| Điều kiện | Không thỏa → |
|---|---|
| Barcode tồn tại trong DB | `404 Product not found for this barcode.` |
| Sản phẩm `status = active` | `400` sản phẩm ngừng bán |
| Thuộc phạm vi xem của thu ngân (GLOBAL hoặc đúng chi nhánh) | `403 Access denied.` |
| **Tồn kho chi nhánh > 0** | `400 This product is out of stock at your branch.` |

Lỗi hay gặp nhất là **tồn kho = 0**. Sản phẩm mới tạo luôn có tồn 0 → phải nhập kho trước khi bán được.

### 3.4. Barcode phải hợp lệ mới quét được bằng camera

`BarcodeDetector` **kiểm tra check digit**. Mã sai check digit thì camera từ chối đọc, dù DB có.

- ✅ Mã do BM bấm **"generate barcode"** sinh ra là EAN-13 chuẩn → quét camera được.
- ❌ Mã seed cũ kiểu `893000000001` (12 số, sai check digit) → **chỉ gõ tay được**, camera không đọc.

BE thì chỉ so khớp chuỗi, không validate check digit — nên gõ tay mã nào cũng tra được.

---

## 4. Tra cứu khách & tích điểm

- Tra khách: `GET /api/cashier/customer?phoneOrEmail=...`
- Tích điểm: `POST /api/cashier/add-points` — **10.000đ = 1 điểm**

Chỉ tài khoản role **CUSTOMER** mới tích điểm được. Nhập SĐT của nhân viên sẽ bị chặn: *"Tài khoản này không phải khách hàng, không thể tích điểm."*

---

## 5. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Xử lý |
|---|---|---|
| Login trên điện thoại báo **403** | BE chỉ whitelist origin `localhost` / `127.0.0.1` (`SecurityConfig.corsConfigurationSource`), vào bằng IP LAN thì Origin không khớp → `Invalid CORS request` | Proxy Vite đã bỏ header `Origin` khi forward (`vite.config.js`). Nếu vẫn lỗi, kiểm tra proxy còn nguyên không |
| Tunnel báo **"Blocked request. This host is not allowed"** | Vite chặn host lạ | `server.allowedHosts: ['.trycloudflare.com']` trong `vite.config.js` |
| Camera mở được nhưng **không bao giờ nhận mã** | `barcode-detector` tải `zxing_reader.wasm` từ CDN jsdelivr, tải hụt là hỏng im lặng | Đã tự host tại `FE/public/zxing_reader.wasm`, trỏ bằng `setZXingModuleOverrides`. Kiểm tra `GET /zxing_reader.wasm` trả 200 |
| Bấm Scan báo **"Unable to open the camera"** | Không phải secure context (đang vào bằng `http://` IP LAN) | Dùng tunnel HTTPS, hoặc Chrome Android bật cờ `chrome://flags/#unsafely-treat-insecure-origin-as-secure` |
| Sản phẩm vừa tạo **không hiện trong gợi ý** | Danh sách sản phẩm nạp lúc mở trang | Đã tự nạp lại khi quay lại tab. Bí quá thì F5 |
| Trang trắng / API không gọi được | Mở FE lúc BE chưa boot xong | Chờ `Started ApiApplication` rồi F5 |
| Tên tiếng Việt hiển thị lỗi font | Lỗi charset đọc DB (mojibake, mã hóa 2 lần) | **Chưa sửa** — xem mục 7 |

---

## 6. Dữ liệu test

| Barcode | Sản phẩm | Ghi chú |
|---|---|---|
| `8939000000104` | San pham Demo POS (id 114) | EAN-13 hợp lệ, tồn 50 — quét camera được |
| `8936221630004` | Nước Nha Đam (id 115) | EAN-13 hợp lệ, tồn 50 — quét camera được |
| `893000000001` … `007` | Lavie, Coca, Hảo Hảo… | Sai check digit — **chỉ gõ tay** |

Khách để test tích điểm: SĐT `0911111111` (`customer01@gmail.com`), hoặc demo Silver `0912345678`.

**Canonical demos only** — legacy `pos_demo_*` / `demo.customer@…` are soft-locked on boot. Demo product barcodes above remain for scan testing.

---

## 7. Phần chưa xong

### Còn dùng dữ liệu giả (BE chưa có API)

| Chức năng | Trạng thái |
|---|---|
| Mã giảm giá (`SAVE10`, `MINUS50K`) | Mock — BE không có endpoint validate |
| Thanh toán / tạo đơn hàng | Mock — BE chưa có entity đơn hàng |
| Lịch sử giao dịch | Mock — chưa lưu đơn nào |
| Đổi điểm (redeem) | `deductPointsAtomic` có sẵn trong repository nhưng **chưa có endpoint** |
| Tích điểm khi thanh toán | Chưa tự chạy — vì checkout còn mock |

Đã nối BE thật: danh sách/tìm sản phẩm, quét barcode, tra khách, tích điểm thủ công.

### Đóng ca / mở ca

- **Mở ca: chưa có gì.** Không có endpoint mở ca, enum `ShiftStatus` không có `OPEN`. Ca sang `PUBLISHED` là do BM publish lịch, không phải thu ngân bắt đầu ca.
- **Đóng ca: BE có API** — `PUT /api/shifts/{id}/close` (staff nhập tiền thực đếm), `PUT /{id}/approve` và `PUT /{id}/reject` (BM đối soát). **FE chưa có màn nào gọi mấy API này.**
- **`expectedCash` gần như luôn = 0** vì chưa có doanh thu bán hàng để tính → số chênh lệch hiện tại chưa có ý nghĩa đối soát.
- **Chưa có bàn giao giữa 2 ca**: `openingCash` ca sau không lấy từ `actualCash` ca trước.
- **Chưa tích hợp PayOS.**

### Lỗi đã biết

- **Tiếng Việt bị mojibake**: API trả tên khách/danh mục sai font (`Khách Hàng Một` → `KhÃ¡ch HÃ ng Má»™t`). Lỗi charset tầng đọc DB.
- **Đăng ký khách mới lỗi**: `POST /api/auth/register` chết vì DB thiếu bảng `email_verification_tokens`.
- **Tích điểm chưa chống bấm 2 lần**: bấm 2 lần là khách được cộng điểm 2 lần, và không lưu ai tích / ca nào.
- **Thêm từ gợi ý không kiểm tra tồn kho**: quét mã thì BE chặn hết hàng, nhưng chọn tay từ danh sách gợi ý lại thêm được ở client mà không hỏi BE.

---

## 8. File liên quan

**BE**

- `feature/barcode/controller/BarcodeController.java` — `POST /api/barcode/scan`
- `feature/product/service/impl/ProductServiceImpl.java` — `scanByBarcode`, `resolveVisibility`
- `feature/cashier/` — tra khách + tích điểm
- `feature/posscan/` — relay mã quét từ điện thoại sang máy bán hàng
- `shared/config/PosScanEventTableMigration.java` — tạo bảng `pos_scan_events`
- `feature/shift/` — lịch ca, đóng ca, đối soát
- `shared/security/WebRolePermissions.java` — ma trận quyền theo role
- `shared/config/SecurityConfig.java` — CORS

**FE**

- `pages/pos/PosNewOrderPage.jsx` — màn bán hàng, tìm kiếm, quét mã
- `pages/pos/components/BarcodeScannerModal.jsx` — camera + giải mã
- `pages/pos/posProduct.js` — map dữ liệu sản phẩm BE → POS (dùng chung)
- `pages/pos/InventoryPage.jsx` — tồn kho (đã nối API thật)
- `contexts/PosCartContext.jsx` — giỏ hàng, tra khách
- `api/barcode.js`, `api/products.js`, `api/cashier.js`, `api/posScan.js`
- `pages/pos/data/mockData.js` — phần còn lại chưa nối BE
- `vite.config.js` — proxy `/api`, `allowedHosts`, bỏ header `Origin`

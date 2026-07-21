# Cashier POS Memo — SEP490-G99 / CSCSMS

> Tài liệu ghi nhớ từ thảo luận team + AI. Cập nhật khi chốt thêm nghiệp vụ.  
> **Phạm vi của bạn (Giang):** bán hàng POS + tích điểm + đổi điểm.  
> **Ngoài phạm vi (member khác):** mở ca / đóng ca / đối soát tiền — sẽ pull code sau.

---

## 1. Mục tiêu & phạm vi

| Hạng mục | Quyết định |
|----------|------------|
| Nền tảng | **React web POS** (trong `SEP490_G99_FE`), chính thức — không Flutter |
| UI style | Theo Figma POS đã cap; cùng hệ web với admin nhưng **layout POS riêng** (sidebar New Order / History / Inventory / Settings) |
| Login | Cashier login form web chung → **redirect thẳng `/pos`** (không `/dashboard`) |
| Permission | Cashier chỉ hoạt động trong POS; Customer chỉ login app |
| DB | Dùng schema hiện có; tránh sửa DB nếu đã cover được |
| Thanh toán phase 1 | **Cash trước**; PayOS QR sau |
| Refund / Void | **Tạm bỏ**; thảo luận sau khi xong luồng chính |
| Mở / đóng ca | **Member khác code**; FE/BE bán hàng tạm mock / skip gate ca nếu cần |

---

## 2. Luồng nghiệp vụ chính (đã thống nhất)

```
BM tạo TK Cashier → gửi email TK/MK
→ Cashier login web
→ Redirect POS
→ [Mở ca — member khác] → Bán hàng
→ Thêm SP vào giỏ (PROMO auto gạch giá ngay trên dòng hàng)
→ Nhập campaign discount code (nếu có)
→ Gắn KH: SĐT hoặc quét QR app → kiểm tra tồn tại trong hệ thống
→ Đổi điểm tại quầy (thỏa thuận miệng, nếu member có ≥1 điểm)
→ Thanh toán Cash | PayOS (phase 2)
→ Hoàn tất đơn → Order History
→ Trừ branch_inventory
→ Tích điểm (chỉ khi KH là member hợp lệ)
→ [Đóng ca — member khác]
```

---

## 3. Giảm giá — 3 kiểu (đã chốt)

| # | Kiểu | Ai tạo | Cách áp tại POS |
|---|------|--------|-----------------|
| A | **Giảm theo mặt hàng / campaign product** | Cấp trên (Director/Admin) | Tự động: trùng loại SP → **gạch giá gốc**, hiện giá đã giảm + badge PROMO |
| B | **Campaign discount code** | Cấp trên | Cashier **nhập code** trên panel → validate → giảm bill |
| C | **Đổi điểm tại quầy** | Thỏa thuận miệng thu ngân ↔ khách | Member đã gắn: **1 điểm = 1.000 VND** giảm bill; cần **≥ 1 điểm** trong tài khoản |

**Thứ tự áp trên 1 đơn (đã chốt):** A (auto trên giá dòng) → B (discount code) → gắn KH → C (đổi điểm) → thanh toán.

---

## 4. Tích điểm & đổi điểm (đã chốt)

### Tích điểm
- **Chỉ** khi khách là **thành viên** trong hệ thống.
- Nhận diện: nhập **SĐT** hợp lệ **hoặc** quét **QR App** (thông tin user trên mobile).
- Sau khi nhận diện: hiển thị thông tin tài khoản đầy đủ; mới được tích điểm.
- Khách lẻ (không SĐT / không trong DB) → `customer_id = null`, **không** tích điểm (hiển thị "Retail").

### Đổi điểm
- **Thỏa thuận miệng** giữa thu ngân và khách tại quầy (không bắt buộc qua voucher catalog).
- Điều kiện: `customers.points >= 1`.
- Quy đổi: **1 điểm = 1.000 VND** giảm trên hóa đơn.
- Trừ điểm + ghi `point_transactions` khi checkout thành công.

### Công thức tích điểm (đã chốt)
- Chỉ member đã gắn vào đơn.
- Tính trên **số tiền thực trả** (sau mọi giảm giá, sau đổi điểm).
- **10.000 VND thực trả → +1 điểm** (làm tròn xuống theo quy tắc nghiệp vụ khi code).

---

## 5. Figma POS (đã cap)

| Màn | Trạng thái |
|-----|------------|
| New Order (giỏ, barcode, PROMO, discount code, phone) | Có |
| Payment modal (Cash / PayOS) | Có |
| Cash Payment (received, quick amount, change, print) | Có |
| PayOS + Customer Display | Có (phase 2) |
| Order History (list, detail, reprint; Refund tạm bỏ) | Có |
| Inventory Products (read-only + stock) | Có |
| Settings | Có menu, chưa ảnh chi tiết |
| Mở ca / Đóng ca | **Chưa** — member khác |

Invoice hiển thị kiểu `INV-2026-050`: **không thêm cột DB** nếu chưa có — generate từ `orders.id` (ví dụ `INV-{year}-{id padded}`).

---

## 6. Shift status (đề xuất — member ca sẽ chốt)

Bạn đề xuất (có thể cover):

| Status | Ý nghĩa |
|--------|---------|
| `DRAFT` / `CREATED` | BM tạo lịch, chưa công bố / chưa mở |
| `PUBLISHED` | BM đã công bố lịch |
| `OPEN` | Cashier đã mở ca trên POS, đang bán |
| `CLOSING` | Đang kiểm đếm / đối soát |
| `CLOSED` | Đã chốt ca |

> **Lưu ý code hiện tại:** Java enum shift đang là `DRAFT | PUBLISHED | CANCELLED`. Member làm ca sẽ mở rộng enum / map DB. **Phần bán hàng** chỉ cần `shift_id` (nullable tạm nếu chưa có API mở ca).

Câu hỏi ca (để sau với member ca):
- Cashier xác nhận hay tự nhập `opening_cash`?
- Công thức `expected_cash`?
- Chênh lệch: BM duyệt trên web admin?

---

## 7. Trả lời 8 câu hỏi cũ (đã cập nhật)

1. **React web POS** = chính thức; UI theo Figma (cùng hệ web, layout POS).
2. **Invoice ID:** làm theo DB — **không thêm cột** nếu chưa có; generate từ `id`.
3. **Giảm giá phase:** 3 kiểu A (auto PROMO mặt hàng) + B (discount code) + C (đổi điểm).
4. **Tích điểm:** chỉ member (SĐT hoặc QR app); không phải member → không tích.
5. **Tạo ca / check-in:** tạm ngoài scope; thảo luận thêm với nhóm.
6. **Shift lifecycle:** DRAFT → PUBLISHED → OPEN → CLOSING → CLOSED (đề xuất; member ca cover).
7. **Refund:** tạm bỏ.
8. **Module đầu tiên của mình:** **bán hàng + tích điểm + đổi điểm** (không làm mở/đóng ca).

---

## 8. Hiện trạng code (khi viết memo)

| Phần | Trạng thái |
|------|------------|
| Login redirect `/pos` | Chưa |
| `WebPermission` CASHIER | Rỗng (0) |
| BM tạo cashier + email | Có |
| Shift BM (create/assign) | Có; cashier open/close **chưa** |
| Entity/API `orders`, POS | **Chưa** |
| FE `pages/pos/` | **Chưa** |
| Campaign BE (Director) | Có phần lớn |
| `BarcodeInput`, `MoneyInput` | Có sẵn FE |

**Chạy local:**
- BE: `SEB490_G99_BE/BE` → `.\mvnw.cmd spring-boot:run` → `:4313`
- FE: `SEP490_G99_FE/FE` → `npm run dev` → `:5175` (proxy `/api` → 4313)
- Swagger: `http://localhost:4313/swagger-ui.html`

---

## 9. DB cover cho phạm vi bán hàng / điểm

### Đã có sẵn (đủ để cover luồng chính)

| Bảng | Dùng cho |
|------|----------|
| `products`, `categories`, `branch_inventory` | Catalog + tồn chi nhánh |
| `campaigns`, `campaign_branches`, `order_discounts` | PROMO auto + gắn giảm giá đơn |
| `vouchers`, `voucher_catalog` | Discount code / voucher đổi điểm |
| `customers`, `membership_tiers`, `loyalty_configs` | Member, SĐT, điểm, công thức tích |
| `point_transactions` | Log earn / redeem |
| `orders`, `order_items`, `payments` | Đơn + dòng hàng + cash/PayOS |
| `shifts` | `orders.shift_id` (nullable tạm nếu chưa mở ca) |

### Có thể cần chỉnh nhẹ sau (không bắt buộc ngay)

| Gap | Ghi chú |
|-----|---------|
| `orders` không có `invoice_code` | Generate từ `id` trên API/FE |
| Campaign **không có cột `code`** | Discount code thường nằm ở `vouchers.code` (có thể gắn `campaign_id`) — cần chốt map khi code |
| `ShiftStatus` Java chưa đủ OPEN/CLOSED | Member ca xử lý |
| QR member payload | Cần format QR app (member_code / userId / phone) khi làm scan |

**Kết luận:** DB **đủ cover** luồng bán hàng + tích/đổi điểm. Chỉ sửa DB nếu lúc implement phát hiện thiếu thật sự.

---

## 10. Đã chốt (2026-07-16) & còn mở

### Đã chốt

| # | Chủ đề | Quyết định |
|---|--------|------------|
| 1 | Thứ tự giảm giá | PROMO dòng hàng → discount code → gắn KH → đổi điểm → thanh toán |
| 2 | Đổi điểm tại quầy | Thỏa thuận miệng; ≥1 điểm; 1 điểm = 1.000 VND |
| 3 | Tích điểm | Trên **số tiền thực trả**; 10.000 VND → +1 điểm; chỉ member |
| 4 | QR App | Dùng field DB có sẵn (`member_code`, `phone`); **không** lộ email/PII thừa; payload JSON tối thiểu — phối hợp team mobile khi họ có spec |
| 5 | Mở ca | **`shift_id = null`** tạm cho đến khi có API mở ca |

### Tạm hoãn (chưa chốt — không chặn làm FE)

> Ghi nhớ 2026-07-16: team tạm bỏ qua 3 câu hỏi này, quay lại khi gắn BE / phối hợp mobile.

1. **Trần giảm tối đa** nếu tổng discount > subtotal? (cap về 0đ hay báo lỗi?)
2. **UI đổi điểm:** thu ngân nhập số điểm muốn dùng hay nút “dùng tối đa”?
3. **Format JSON QR** chính thức từ app mobile (chờ team app; phase 1 dùng SĐT trước).

### Ưu tiên thấp / sau

6. Settings POS gồm gì?
7. Customer Display: chỉ phase PayOS?
8. Order History: filter theo ca / cashier / chi nhánh?

---

## 11. Thứ tự implement đề xuất (phạm vi của bạn)

| # | Module | Ghi chú |
|---|--------|---------|
| 1 | POS shell + login redirect `/pos` | Layout Figma, chặn admin routes |
| 2 | New Order UI (mock data) | Giỏ, barcode, PROMO UI |
| 3 | Customer phone / QR lookup UI | |
| 4 | Discount code + đổi điểm UI | |
| 5 | Cash payment UI | |
| 6 | Order History + Inventory (read-only) UI | |
| 7 | BE APIs: products/inventory for POS, cart checkout cash | |
| 8 | BE: apply discounts A/B/C + earn points | |
| 9 | Wire FE ↔ BE | |
| 10 | PayOS (phase 2) | |

Có thể **làm FE trước** với mock (xem §12).

---

## 12. Ghi chú kỹ thuật: API & FE-first

### Có phải tạo API mới không?

**Có.** Module POS bán hàng **chưa có** trên BE. Các API admin/warehouse/shift BM **không thay** được luồng checkout.

Pattern giống team đã làm (vd. purchase-order):

```
feature/pos/
  controller/   ← REST + @Tag @Operation → Swagger tự hiện
  service/
  dto/request|response/
  repository/   (hoặc dùng repo shared)
shared/entity/  ← OrderModel, OrderItemModel, PaymentModel... (map bảng có sẵn)
```

Ví dụ endpoint sẽ cần (draft):

- `GET /api/pos/products?keyword=` / barcode
- `GET /api/pos/customers?phone=`
- `POST /api/pos/discounts/validate` (code)
- `POST /api/pos/orders` (checkout cash: items, customerId, voucherCode, cashReceived…)
- `GET /api/pos/orders` (history)
- `GET /api/pos/inventory` (read-only)

Auth: JWT; authorize bằng `hasRole('CASHIER')` hoặc permission POS mới.

Swagger: viết Controller đúng chuẩn → mở `/swagger-ui.html` test — **không** “đẩy” Swagger thủ công.

### FE trước được không?

**Được.** Khuyến nghị:

1. Scaffold `PosLayout` + routes `/pos/*` theo Figma.
2. Mock cart / payment / history bằng data cứng hoặc JSON local.
3. Song song (hoặc sau) BE entity + API.
4. Thay mock bằng `src/api/pos.js` gọi `http.js`.

Lợi ích: UI ổn định theo Figma trong lúc chờ / song song với member làm ca.

---

## 13. Tài liệu tham chiếu

- Docs: `SEB490_G99_BE/BE/docs/` (Report1–4, Feature List Excel)
- Quy ước API/FE: `AI_CHATBOT.md`, `AI_CHATBOT_FE.md`
- Schema: `documents/sql/schema_chuoi_cua_hang_readonly_snapshot.sql`
- Seed mẫu: `SEP490_G99_FE/database/convenience_store_db_full.sql`

---

## 14. Changelog memo

| Ngày | Nội dung |
|------|----------|
| 2026-07-16 | Tạo memo; ghi phạm vi bán hàng + điểm; bỏ mở/đóng ca khỏi scope Giang; trả lời 8 câu hỏi; ghi DB cover + FE-first + cần tạo API POS |

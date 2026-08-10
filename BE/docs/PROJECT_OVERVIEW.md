# Mô tả dự án — Hệ thống quản lý chuỗi cửa hàng bán lẻ (SEB490_G99_BE)

> Tài liệu tham chiếu tổng quan về nghiệp vụ, kiến trúc và hiện trạng dữ liệu.
> Mọi thông tin trong file này được rút ra trực tiếp từ source code và từ database
> thực tế (không suy đoán). Cập nhật: 06/08/2026.

---

## 1. Tổng quan

**Bản chất:** Backend cho hệ thống quản lý **chuỗi cửa hàng bán lẻ**, gồm hai mảng gắn liền nhau:

1. **Quản trị chuỗi & chuỗi cung ứng nội bộ** — chi nhánh đề nghị nhập hàng, kho tổng duyệt và điều phối, vận chuyển, nhận hàng, kiểm kê.
2. **Vận hành tại cửa hàng** — xếp ca, mở/đóng ca, đối soát tiền, bán hàng POS, khuyến mãi, tích điểm khách hàng.

**Công nghệ:**

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ / Framework | Java 17, Spring Boot 3.5.0 |
| CSDL | MySQL 8 (JPA/Hibernate, `ddl-auto=none` — schema quản lý thủ công) |
| Bảo mật | Spring Security + JWT (jjwt), BCrypt, stateless |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Thanh toán | PayOS (`payos-java`) |
| Khác | Lombok, ModelMapper, Spring Mail, JaCoCo |

**Quy ước mã nguồn:** package gốc `base.api`, chia hai nhánh:

- `shared/` — dùng chung: `entity/`, `enums/`, `converter/`, `config/`, `security/`, `exception/`, `dto/`, `util/`, `base/`
- `feature/<tên module>/` — mỗi module tự chứa `controller/`, `service/` + `service/impl/`, `repository/`, `dto/request/`, `dto/response/`, `mapper/`

**Quy ước API:** response bọc trong `TFUResponse` (`success`, `data`, `message`, `statusCode`); phân trang **bắt đầu từ trang 1**; nhiều endpoint có cả bản trả `List` và bản `/page`.

---

## 2. Vai trò người dùng & phân quyền

`UserRole`: `ADMIN`, `DIRECTOR` (alias `OWNER`, `PROMOTION_DIRECTOR`), `BRANCH_MANAGER` (alias `MANAGER`), `WAREHOUSE_MANAGER`, `INVENTORY_STAFF`, `CASHIER`, `CUSTOMER`, `STAFF`.

Phân quyền không dựa trực tiếp vào role mà qua **41 quyền chi tiết** (`WebPermission`), ánh xạ role → tập quyền trong `WebRolePermissions`, kiểm tra bằng `@PreAuthorize("@permissionChecker.has('...')")`.

Nhóm quyền tiêu biểu:

| Vai trò | Quyền đặc trưng |
|---|---|
| ADMIN | `ADMIN_DASHBOARD`, `USER_MANAGEMENT_LIST`, `SYSTEM_SETTINGS_MASTER_DATA`, `BRANCH_LIST_ADMIN` |
| DIRECTOR | `DIRECTOR_DASHBOARD`, `PROMOTION_MANAGEMENT`, `BUSINESS_PERFORMANCE_REPORTS`, `STRATEGIC_PLANNING_OVERVIEW` |
| BRANCH_MANAGER | `BRANCH_DASHBOARD`, `SHIFT_MANAGEMENT`, `APPROVE_CASH_DISCREPANCY`, `SUPPLY_IMPORT_RECEIPT_APPROVE`, `CREATE_IMPORT_REQUEST`, `REFUND_APPROVAL` |
| WAREHOUSE_MANAGER | `WAREHOUSE_DASHBOARD`, `VIEW_CENTRAL_INVENTORY`, `APPROVE_IMPORT_REQUEST`, `CHOOSE_EXTERNAL_SUPPLIER`, `MANAGE_DISPATCH_ORDERS` |
| INVENTORY_STAFF | `VIEW_BRANCH_INVENTORY`, `RECEIVE_SHIPMENT`, `INVENTORY_COUNT` |
| CASHIER | `POS_CHECKOUT`, `REFUND_REQUEST`, `CASHIER_ADD_POINTS`, `CASHIER_CLOSE_SHIFT`, `MY_SHIFTS` |

**Cơ chế thu hẹp phạm vi dữ liệu (quan trọng):** một số service tự áp scope theo vai trò người gọi. Ví dụ `ReportService` — ADMIN/DIRECTOR xem toàn hệ thống (`branchId` tùy chọn), còn BRANCH_MANAGER **bị ép về chi nhánh của mình**, tham số `branchId` truyền lên bị bỏ qua.

**Bảo mật bổ sung:**
- Token thu hồi khi logout lưu ở `RevokedTokenModel` để chặn tái sử dụng.
- Thao tác nhạy cảm cần mã xác thực gửi email: `CriticalUserActionTokenModel` (xoá user), `BranchSuspendTokenModel` (tạm ngưng chi nhánh).
- Endpoint public: login, register, forgot-password, verify-email, resend-verification, `payos-hook`, Swagger. Riêng logout **cần đăng nhập** — nó phải đọc được token để thu hồi.

---

## 3. Bản đồ module

Sắp theo quy mô mã nguồn (số dòng Java):

| Module | SLOC | Vai trò nghiệp vụ |
|---|---:|---|
| `shift` | 2.513 | Xếp lịch ca làm việc, phân công nhân viên, publish theo tuần |
| `auth` | 2.124 | Đăng nhập/đăng ký, quản lý user, xác thực email, đổi/quên mật khẩu |
| `purchaserequest` | 1.949 | **Yêu cầu nhập hàng của chi nhánh** (module lõi) |
| `shiftsession` | 1.612 | Mở/đóng ca thực tế, đối soát tiền, bàn giao ca |
| `posorder` | 1.460 | Bán hàng POS, hoàn/trả hàng |
| `product` | 1.393 | Sản phẩm, quy cách đóng gói, catalog POS |
| `promotion` | 1.233 | Chiến dịch khuyến mãi |
| `branch` | 1.199 | Quản lý chi nhánh, gán nhân sự |
| `report` | 1.132 | Báo cáo doanh thu, hoá đơn, chênh lệch tiền, điểm thưởng |
| `branchreceiving` | 1.114 | Chi nhánh nhận hàng, BM duyệt phiếu nhập |
| `dispatch` | 1.075 | Gom lô vận chuyển kho tổng → chi nhánh |
| `purchaseorder` | 840 | Đặt hàng nhà cung cấp cho kho tổng |
| `inventorycount` | 688 | Kiểm kê kho tại chi nhánh |
| `cashier` | 572 | Tra cứu khách hàng, cộng điểm tại quầy |
| `payment` | 509 | Thanh toán PayOS, webhook |
| `inventory` | 480 | Tồn kho tổng & tồn kho chi nhánh, ngưỡng đặt lại |
| `supplier`, `category` | 391 / 369 | Nhà cung cấp, danh mục |
| `catalogimport` | 357 | Import catalog sản phẩm hàng loạt (chạy nền) |
| `director`, `system`, `branchmanager` | 306 / 281 / 260 | Dashboard, cấu hình hệ thống, hạng thành viên |
| `posscan` | 249 | Quét mã vạch từ điện thoại relay về máy POS |
| `warehouse`, `admin`, `permission`, `barcode` | 203 / 186 / 85 / 50 | Dashboard kho, dashboard admin, ma trận quyền, barcode |

---

## 4. Mô hình dữ liệu

43 entity JPA trong `shared/entity/`. Nhóm theo nghiệp vụ:

### 4.1 Người dùng & bảo mật
`UserModel` (có `role`, `branchId`, `points`, hạng thành viên) · `RoleModel` · `EmailVerificationTokenModel` · `PasswordResetTokenModel` · `RevokedTokenModel` · `CriticalUserActionTokenModel` · `BranchSuspendTokenModel`

### 4.2 Chi nhánh & danh mục hàng hoá
- `BranchModel` — chi nhánh; có `area` / `route` phục vụ gom tuyến vận chuyển
- `CategoryModel` — danh mục, tự tham chiếu cha–con
- `ProductModel` — sản phẩm; `scope` = `GLOBAL` (mọi chi nhánh) hoặc `BRANCH`
- `ProductPackagingModel` — **các cấp quy cách đóng gói** của một sản phẩm
- `SupplierModel` — nhà cung cấp

> **Quy ước đơn vị — điểm dễ sai nhất của hệ thống:**
> Tồn kho (`warehouse_inventory`, `branch_inventory`) luôn lưu theo **đơn vị cơ bản (BASE)**, ví dụ "chai".
> Số lượng trên yêu cầu nhập / duyệt / nhận lại tính theo **đơn vị đóng gói cao nhất (TOP)**, ví dụ "thùng 24 chai".
> Mọi phép so sánh và trừ kho đều phải quy đổi qua `ProductPackagingService.toBaseQty(...)`.

### 4.3 Tồn kho hai tầng
- `WarehouseInventoryModel` — tồn **kho tổng**, có `reorder_point`
- `BranchInventoryModel` — tồn **từng chi nhánh**, có `currentStock` + `reorder_point`

### 4.4 Chuỗi cung ứng nội bộ
`PurchaseRequestModel` + `PurchaseRequestDetailModel` (yêu cầu nhập hàng của chi nhánh) · `PurchaseOrderModel` + `PurchaseOrderItemModel` (đơn đặt nhà cung cấp) · `DispatchOrderModel` + `DispatchOrderRequestModel` (lô vận chuyển, quan hệ N–N với yêu cầu) · `GoodsReceiptModel` + `GoodsReceiptItemModel` (phiếu nhập kho tại chi nhánh)

### 4.5 Ca làm việc & đối soát tiền
- `ShiftModel` — ca theo lịch; `ShiftAssignmentModel` — phân công nhân viên (có check-in/out)
- `ShiftSessionModel` — phiên làm việc thực tế; các cột tiền: `cashSales`, `expectedCash`, `actualCash`, `difference`
- `ShiftSessionApprovalModel` — quyết định duyệt chênh lệch của BM
- `ShiftSessionHighValueItemModel` — kiểm đếm hàng giá trị cao khi đóng ca

### 4.6 Bán hàng POS
`OrderModel` (branchId, shiftId, cashierId, customerId, subtotal, discountAmount, total, pointsRedeemed, pointsEarned, invoiceCode, status) · `OrderItemModel` (chụp lại `productName` tại thời điểm bán) · `OrderDiscountModel` · `OrderRefundModel` · `PaymentModel` (CASH / PAYOS) · `PosScanEventModel`

### 4.7 Khuyến mãi & khách hàng thân thiết
`CampaignModel` + `CampaignBranchModel` + `CampaignBranchExclusionModel` · `VoucherCatalogModel` + `VoucherModel` · `MembershipTierModel` (`minPoints`, `pointMultiplier`) · `PointTransactionModel` (EARN / REDEEM / REFUND_REVERSAL)

### 4.8 Kiểm kê
`InventoryCountSessionModel` + `InventoryCountItemModel` (`variance = countedQty − systemQty`)

---

## 5. Các luồng nghiệp vụ cốt lõi

### 5.1 Luồng nhập hàng (xương sống của hệ thống)

```
   CHI NHÁNH                KHO TỔNG                    VẬN CHUYỂN            CHI NHÁNH
      │                        │                            │                    │
  [DRAFT] ──submit──> [PENDING] ──approve──┬──> [APPROVED] ──gom lô──> [DISPATCHING]
      │                        │           │                                     │
   cancel                    reject        └──> [AWAITING_STOCK]                  │
      ↓                        ↓                (chờ đặt NCC)              [IN_TRANSIT]
 [CANCELLED]            [REJECTED]                                                │
                                                                          nhận + BM duyệt
                                                                                  ↓
                                                                            [RECEIVED]
```

Các bước chi tiết:

1. **Tạo nháp** — Branch Manager chọn sản phẩm + số lượng (đơn vị TOP), hoặc dùng gợi ý tự động.
2. **Gửi duyệt** — phải có ít nhất 1 dòng, số lượng > 0.
3. **Duyệt** — ADMIN / DIRECTOR / WAREHOUSE_MANAGER nhập `approvedQuantity` (≤ số yêu cầu). Hệ thống đối chiếu với **tồn kho khả dụng của kho tổng đã trừ nhu cầu các yêu cầu APPROVED khác** (`WarehouseStockAllocationHelper`):
   - đủ hàng → `APPROVED`
   - thiếu hàng → `AWAITING_STOCK`
4. **Gom lô vận chuyển** — Warehouse Manager tạo `DispatchOrderModel`; **trừ tồn kho tổng ngay tại bước này**; yêu cầu chuyển `DISPATCHING`.
   Lô có vòng đời riêng: `PREPARING → DELIVERING → (REDELIVERY nếu giao hụt) → RECEIVED`.
5. **Vận chuyển** — lô chuyển `DELIVERING` thì các yêu cầu trong lô tự chuyển `IN_TRANSIT`.
6. **Nhận hàng** — Inventory Staff xác nhận số thực nhận → sinh `GoodsReceiptModel` trạng thái `PENDING_APPROVAL`. **Chưa cộng tồn kho ở bước này.**
7. **BM duyệt phiếu nhập** — duyệt xong mới cộng vào `branch_inventory`; yêu cầu → `RECEIVED`. Khi mọi yêu cầu trong lô đều RECEIVED thì lô → `RECEIVED`.

**Gom đơn hợp nhất** (`/api/purchase-requests/consolidated`): tổng hợp mọi yêu cầu `APPROVED`, nhóm theo **chi nhánh** rồi theo **danh mục sản phẩm**, cộng dồn `approvedQuantity` — giúp kho tổng nhìn tổng lượng cần chuẩn bị trước khi gom lô.

**Cơ chế phân bổ khi thiếu hàng:** `filterDispatchableApproved` duyệt các yêu cầu APPROVED theo **FIFO thời điểm tạo** (`createdAt`), giữ chỗ tồn kho dần; yêu cầu không đủ hàng bị hạ về `AWAITING_STOCK` bởi `reconcileApprovedStockStatus()`.

### 5.2 Đặt hàng nhà cung cấp

Khi kho tổng thiếu (có yêu cầu `AWAITING_STOCK` hoặc tồn dưới `reorder_point`), Warehouse Manager tạo `PurchaseOrderModel` (`ORDERED → RECEIVED | CANCELLED`). Nhận hàng làm tăng `warehouse_inventory`, sau đó hệ thống tự rà soát lại các yêu cầu APPROVED xem đã đủ hàng để gom lô chưa.

### 5.3 Ca làm việc & đối soát tiền

**Xếp lịch:** `DRAFT → PUBLISHED → CLOSED → APPROVED | REJECTED` (hoặc `CANCELLED`).
Ràng buộc bắt buộc khi publish (`validatePublishStaffing`):
- mỗi ca ≥ 1 nhân viên, trong đó **≥ 1 CASHIER**
- **ca đầu ngày và ca cuối ngày** phải có **≥ 1 INVENTORY_STAFF**

**Vận hành ca:** `SCHEDULED → OPEN → CLOSING/PENDING_HANDOVER → PENDING_APPROVAL → APPROVED | REJECTED`.
Trình tự đóng ca: xác nhận kiểm tra → xác nhận bàn giao → lưu nháp → `close` (Cashier) hoặc `close-inventory` (Inventory Staff).

**Đối soát:** `expectedCash = tiền đầu ca + doanh thu tiền mặt`; đối chiếu `actualCash` ra `difference`, phân loại `BALANCED` / `CASH_SHORTAGE` / `CASH_EXCESS`. BM duyệt hoặc từ chối (yêu cầu đếm lại) qua `ShiftSessionApprovalModel`.

### 5.4 Bán hàng POS

Thu ngân quét/thêm sản phẩm → áp **3 tầng giảm giá**:
- **A** — khuyến mãi tự động theo campaign (gạch giá)
- **B** — mã voucher nhập tay
- **C** — đổi điểm tại quầy (1 điểm = 1.000đ)

Sau đó gắn khách hàng (theo SĐT) → thanh toán CASH hoặc PayOS QR.

Nguyên tắc trong `checkout`:
1. Giá luôn lấy từ DB, **không tin client**; gộp dòng trùng sản phẩm.
2. Trừ tồn kho chi nhánh **atomic** — thiếu hàng thì rollback toàn đơn.
3. Tích điểm chỉ khi khách là thành viên hợp lệ, tính trên số tiền thực trả, quy tắc **10.000đ → 1 điểm**.
4. Khoá voucher sau cùng, khi mọi bước khác đã chắc chắn thành công.
5. PayOS: đơn ở `PENDING_PAYMENT`, webhook `payos-hook` chuyển sang `COMPLETED`.

**Hoàn/trả hàng:** Cashier gửi yêu cầu → BM duyệt → đơn mới chuyển `REFUNDED`.

### 5.5 Kiểm kê kho

Chi nhánh tạo phiên đếm, ghi số thực tế từng sản phẩm, hệ thống tính `variance` → `PENDING_APPROVAL` → BM duyệt/từ chối.

### 5.6 Khuyến mãi

`CampaignModel`: loại `PERCENT` / `FIXED_AMOUNT` / `BUY_X_GET_Y`; phạm vi `CHAIN` hoặc `BRANCH` (có danh sách chi nhánh áp dụng và danh sách loại trừ); có độ ưu tiên. Vòng đời `DRAFT → ACTIVE → SUSPENDED | DEACTIVATED`. Job `CampaignExpiryJob` tự hết hạn campaign.

---

## 6. Bề mặt API

28 REST controller. Nhóm chính:

| Nhóm | Base path |
|---|---|
| Xác thực & người dùng | `/api/auth` |
| Dashboard theo vai trò | `/api/admin`, `/api/director`, `/api/branch-manager`, `/api/warehouse` |
| Danh mục nền | `/api/branches`, `/api/categories`, `/api/products`, `/api/suppliers` |
| Tồn kho | `/api/inventory`, `/api/inventory-counts` |
| Chuỗi cung ứng | `/api/purchase-requests`, `/api/purchase-orders`, `/api/dispatch-orders`, `/api/branch-receiving` |
| Ca làm việc | `/api/shifts`, `/api/shift-sessions`, `/api/employees` |
| Bán hàng | `/api/pos`, `/api/pos/orders`, `/api/pos/scan-events`, `/api/cashier`, `/api/barcode` |
| Khuyến mãi & thanh toán | `/api/campaigns`, `/api/payment` |
| Báo cáo & hệ thống | `/api/reports`, `/api/permissions`, `/api/system`, `/api/admin/catalog-import` |

Swagger UI: `http://localhost:1328/swagger-ui/index.html`

---

## 7. Hiện trạng dữ liệu thực tế

Đo trực tiếp trên database dev (MySQL remote), thời điểm 06/08/2026. **48 bảng.**

### 7.1 Khối lượng bản ghi

| Bảng | Số dòng | | Bảng | Số dòng |
|---|---:|---|---|---:|
| `branch_inventory` | 2.358 | | `purchase_requests` | 21 |
| `order_items` | 1.804 | | `dispatch_order_requests` | 18 |
| `product_packagings` | 1.153 | | `goods_receipts` | 14 |
| `orders` | 743 | | `point_transactions` | 14 |
| `payments` | 743 | | `dispatch_orders` | 13 |
| `products` | 566 | | `categories` | 12 |
| `warehouse_inventory` | 565 | | `shift_sessions` | 9 |
| `shift_assignments` | 309 | | `campaigns` | 8 |
| `inventory_count_items` | 149 | | `branches` | 7 |
| `shifts` | 137 | | `purchase_orders` | 7 |
| `purchase_request_items` | 56 | | `inventory_count_sessions` | 3 |
| `goods_receipt_items` | 42 | | `suppliers` | 3 |
| `purchase_order_items` | 35 | | `customers` | 2 |
| `users` | 35 | | `order_refunds` | 2 |

### 7.2 Đặc điểm dữ liệu bán hàng

| Chỉ số | Giá trị |
|---|---|
| Khoảng thời gian | 06/07/2026 → 06/08/2026 (**31 ngày**) |
| Tổng đơn | 743 (tháng 7: 671 · tháng 8: 72) |
| Chi nhánh có phát sinh đơn | **4 / 7** (id 1, 2, 4, 5 — mỗi chi nhánh 169–210 đơn) |
| Đơn có gắn khách hàng | **14 / 743 (1,9%)**, 5 khách phân biệt |
| Lead time vận chuyển trung bình | 1,9 ngày (13 lô, đều đã có `delivered_at`) |
| Yêu cầu nhập hàng | 19 `received` · 1 `draft` · 1 `rejected` |
| Ca có số liệu chênh lệch tiền | 3 |

**Mật độ chuỗi bán hàng theo cặp (sản phẩm × chi nhánh)** — tổng 514 cặp:

| Số ngày có phát sinh bán | Số cặp |
|---|---:|
| ≥ 30 ngày | 0 |
| 14–29 ngày | 0 |
| 7–13 ngày | 20 |
| 3–6 ngày | 301 |
| 1–2 ngày | 193 |

Sản phẩm bán chạy nhất (Lavie 500ml) đạt 118 đơn vị trong 14 ngày có bán.

> **Hàm ý:** dữ liệu đủ dày để **truy vấn, tổng hợp và báo cáo**, nhưng **chưa đủ để dự báo theo từng sản phẩm** (không chuỗi nào đạt 14 ngày quan sát). Mọi tính năng dựa trên suy luận thống kê theo SKU, phát hiện bất thường tiền ca, phân tích khách hàng hay đo hiệu quả khuyến mãi đều chưa có cơ sở dữ liệu để thực hiện ở thời điểm này.

---

## 8. Ghi chú kỹ thuật cần lưu ý

1. **Quy đổi TOP ↔ BASE** — nguồn lỗi phổ biến nhất. Xem mục 4.2.
2. **Thời điểm cộng/trừ kho** — trừ kho tổng lúc *gom lô*; cộng kho chi nhánh lúc *BM duyệt phiếu nhập*, không phải lúc nhận hàng.
3. **`reorder_point` là số nhập tay**, tĩnh, không tự cập nhật theo tốc độ bán.
4. **Gợi ý nhập hàng hiện tại** (`getRecommendedProductsForBranch`) chỉ là ngưỡng tĩnh: nếu `currentStock ≤ reorderPoint` thì đề xuất bù `reorderPoint − currentStock`, quy đổi lên đơn vị TOP. Không xét tốc độ bán, lead time hay mùa vụ.
5. **Phân bổ hàng khi kho thiếu là FIFO thuần** theo `createdAt`, không ưu tiên theo mức khẩn hay doanh thu.
6. **`ddl-auto=none`** — thay đổi entity **không** tự đổi schema; phải viết migration SQL thủ công.
7. **`spring.datasource`** đang trỏ tới MySQL dùng chung trên máy chủ từ xa; thao tác ghi ảnh hưởng cả nhóm.

---

## 9. Cảnh báo: tàn dư từ dự án khác trong repo

Repo còn lẫn tài liệu và script **không thuộc hệ thống bán lẻ này**, mà thuộc một dự án đặt sân pickleball tên **"Pickaboo"**. Không có dòng code Java nào tương ứng trong `src/` (đã kiểm tra toàn bộ lịch sử mọi nhánh):

| File | Nội dung thực tế |
|---|---|
| `docs/AI_CHATBOT.md` | Chatbot đặt sân pickleball (`BookingService`, `CourtRepository`, PayOS link đặt sân) |
| `docs/AI_CHATBOT_FE.md` | Hướng dẫn FE tích hợp chatbot đặt sân |
| `documents/sql/seed_owner_analytics_demo_2026.sql` | Ghi rõ "Pickaboo — Seed demo Owner Analytics" |
| `documents/sql/fix_booking_services_fk_to_services.sql` | `booking_services` — bảng không tồn tại trong hệ thống này |
| `documents/sql/migrate_price_policies_day_of_week_vn_to_iso.sql` | `price_policies` — không thuộc schema hiện tại |

**Khuyến nghị:** xoá các file này để tránh gây nhầm lẫn khi đọc dự án hoặc khi nạp tài liệu vào công cụ AI.

---

## 10. Tài liệu liên quan trong repo

| File | Nội dung |
|---|---|
| `docs/CASHIER_POS_MEMO.md` | Nghiệp vụ bán hàng POS đã chốt: luồng bán, 3 tầng giảm giá, công thức tích/đổi điểm |
| `docs/POS_SCAN_GUIDE.md` | Quét mã vạch bằng điện thoại phụ, relay về máy POS qua `pos_scan_events` |

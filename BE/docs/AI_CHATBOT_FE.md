# Hướng dẫn tích hợp AI Chatbot — dành cho FE (Web/Mobile)

Trợ lý đặt sân pickleball **per-Branch**. User chọn chi nhánh → chat với bot → bot dẫn dắt chọn sân → tra giờ trống → đặt sân → trả về link PayOS.

Tài liệu này mô tả luồng tích hợp từ A→Z: lần đầu chưa có conversation, lúc đã có conversation, cách gửi tin, cách xử lý các case đặc biệt (chọn sân, tạo booking, lỗi).

---

## Chuẩn API

- **Base URL**: `https://api.chainstore.site` (hoặc `http://localhost:1328` khi dev)
- **Content-Type**: `application/json`
- **Thành công**:
  ```json
  { "success": true, "data": {...}, "message": "Success", "statusCode": 200 }
  ```
- **Phân trang** (response):
  ```json
  {
    "success": true,
    "data": {
      "listObjects": [...],
      "pageNumber": 1,
      "pageSize": 10,
      "totalRecords": 25,
      "totalPages": 3,
      "hasNext": true,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi**: `{ "success": false, "message": "...", "statusCode": 400|401|403 }`
- **Phân trang request**: `?page=1&size=10` (page bắt đầu từ **1**, không phải 0).

## Xác thực

Tất cả endpoint AI cần header:

```http
Authorization: Bearer <JWT>
```

JWT lấy từ `POST /api/auth/login`. Backend tự xác định `userId` từ JWT — FE **không cần** truyền userId bất kỳ đâu.

---

## Tổng quan kiến trúc

```
Mobile/Web App ──► POST /api/chat/branches/{branchId}/messages
                          │
                          ▼
                   ChainStore Backend
                   ├─ Tạo conversation mới nếu conversationId = null
                   ├─ Lưu user message vào DB
                   ├─ Gọi OpenAI (gpt-4o-mini) kèm history + tools
                   │   ├── OpenAI có thể gọi tool (list_courts_in_branch,
                   │   │    select_court, get_available_slots, create_booking...)
                   │   ├── Backend thực thi tool, trả kết quả cho OpenAI
                   │   └── OpenAI trả text reply cuối cùng (tiếng Việt)
                   ├─ Lưu assistant message vào DB
                   └─ Trả {conversationId, reply, selectedCourtId, bookingId, paymentUrl}
```

**Lưu ý quan trọng:**

- Mỗi conversation luôn gắn với **1 chi nhánh** (`branchId` trong path). User muốn đổi chi nhánh → tạo conversation mới.
- Trong 1 conversation, bot dẫn dắt user **chọn 1 sân** (`selectedCourtId`). Sau khi chốt sân, booking tools mới mở khoá.
- `paymentUrl` (PayOS) chỉ có khi tool `create_booking` chạy thành công.

---

## Endpoints

### Cho user (cần JWT user thường)

| # | Method | Path | Mô tả |
|---|--------|------|-------|
| 1 | `POST` | `/api/chat/branches/{branchId}/messages` | Gửi tin nhắn (auto-tạo conversation nếu `conversationId=null`) |
| 2 | `GET` | `/api/chat/me/conversations` | Danh sách conversations của tôi — **mọi chi nhánh** |
| 3 | `GET` | `/api/chat/branches/{branchId}/conversations` | Danh sách conversations của tôi — **trong 1 chi nhánh** |
| 4 | `GET` | `/api/chat/conversations/{conversationId}/messages` | Tin nhắn của 1 conversation (chỉ owner) |

### Cho admin (cần JWT role ADMIN)

| # | Method | Path | Mô tả |
|---|--------|------|-------|
| 5 | `GET` | `/api/admin/chat/conversations` | List toàn bộ conversations của mọi user, filter `branchId` / `userId` / `status` |

> **Không có** endpoint `POST` để tạo conversation rỗng. Conversation được tạo **tự động** ngay khi gửi tin nhắn đầu tiên.

> **Không có** endpoint `DELETE` conversation hiện tại.

> **Không có** endpoint admin xem messages của conversation người khác (chỉ owner đọc được). Cần mở ticket nếu admin cần.

---

## Mental model: conversation gắn với branch

Khác Gemini ref doc (1 conversation chung không phụ thuộc context), ChainStore:

- **1 conversation = 1 (user, branch)**. Đổi branch → conversation mới.
- 1 user có thể có nhiều conversations song song trên nhiều branches.
- Trong 1 conversation, sân được "khoá" sau khi bot gọi `select_court` (lưu `conversation.court_id`). Đổi sân → bot tự gọi lại `select_court` cập nhật.

Hệ quả UI:

- Tab "Trợ lý AI" toàn cục → gọi `GET /api/chat/me/conversations` (cross-branch).
- Tab trong màn chi tiết chi nhánh → gọi `GET /api/chat/branches/{id}/conversations` (filter theo branch).
- Cả hai đều dùng chung endpoint POST messages — vì path đã có `branchId`.

---

## Luồng FE từ A→Z

### Bước 0 — Chọn chi nhánh

FE đã có sẵn flow chọn chi nhánh (màn list branches). Lấy `branchId` ở đây.

```
User → màn Branches → tap "Cầu Giấy" (id=4) → tap nút "Trợ lý AI"
                                                 │
                                                 ▼
                                          Mở màn AI Chat với branchId=4
```

### Bước 1 — Mở màn AI Chat (lần đầu hoặc quay lại)

Khi user mở tab AI cho 1 chi nhánh, FE làm song song 2 việc:

**a. Load danh sách conversation cũ trong chi nhánh này:**

```http
GET /api/chat/branches/4/conversations?page=1&size=10
Authorization: Bearer <JWT>
```

Response:
```json
{
  "success": true,
  "data": {
    "listObjects": [
      {
        "id": 16,
        "userId": 3,
        "userName": "Customer ChainStore",
        "branchId": 4,
        "branchName": "Cầu Giấy",
        "courtId": null,
        "courtName": null,
        "title": "Đặt sân tối nay",
        "status": "ACTIVE",
        "createdAt": "2026-05-11T05:40:58",
        "updatedAt": "2026-05-11T05:41:05"
      }
    ],
    "pageNumber": 1, "pageSize": 10, "totalRecords": 1,
    "totalPages": 1, "hasNext": false, "hasPrevious": false
  }
}
```

**b. Hai trường hợp:**

| Tình huống | Hành động FE |
|---|---|
| `totalRecords = 0` (lần đầu chat) | Hiện màn **empty state**: icon robot + greeting + chip suggested prompts. **KHÔNG** gọi POST để tạo conversation. Đợi user nhập tin đầu tiên. |
| Có conversation cũ | Hiện danh sách. User có thể tap vào 1 conversation cũ để tiếp tục, hoặc tap "Cuộc trò chuyện mới" → reset state về empty. |

> **Tip UX**: ở lần đầu, lưu trong app state `conversationId = null`. Khi user gửi tin đầu, BE tự tạo và trả `conversationId` về — FE cache để dùng cho các turn sau.

### Bước 2 — User gửi tin nhắn đầu tiên

User nhập "Tôi muốn đặt sân tối nay" → tap gửi.

FE call:

```http
POST /api/chat/branches/4/messages
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "conversationId": null,
  "message": "Tôi muốn đặt sân tối nay"
}
```

Response (BE tự tạo conversation, gọi OpenAI, OpenAI gọi tool `list_courts_in_branch`, trả lại text):

```json
{
  "success": true,
  "data": {
    "conversationId": 17,
    "reply": "Chi nhánh Cầu Giấy có 5 sân:\n- Sân 1 (Indoor)\n- Sân 2 (Outdoor)\n- Sân 3 (Indoor)\n- Sân 4 (Outdoor)\n- Sân A (Indoor)\nBạn muốn chọn sân nào?",
    "selectedCourtId": null,
    "bookingId": null,
    "paymentUrl": null
  },
  "message": "Success",
  "statusCode": 200
}
```

FE xử lý:
1. **Lưu `conversationId = 17`** vào state. Mọi tin sau dùng id này.
2. Render bubble user (text user vừa nhập) + bubble AI (`reply`).
3. `selectedCourtId = null` → cập nhật badge "Chưa chọn sân" trên header.

### Bước 3 — User chốt sân

User: "Cho sân 1 đi" → tap gửi.

```http
POST /api/chat/branches/4/messages
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "conversationId": 17,
  "message": "Cho sân 1 đi"
}
```

Backend: OpenAI gọi tool `select_court` → side-effect set `conversation.court_id = 1` → trả text.

Response:
```json
{
  "success": true,
  "data": {
    "conversationId": 17,
    "reply": "OK, đã chọn Sân 1 (Indoor) cho bạn. Bạn muốn đặt ngày nào và giờ nào?",
    "selectedCourtId": 1,
    "bookingId": null,
    "paymentUrl": null
  }
}
```

FE: cập nhật header "Đang đặt: Sân 1". Có thể đổi màu bubble hoặc hiện chip "🏸 Sân 1".

### Bước 4 — User chọn giờ, bot quote giá

User: "Tối nay 19h-21h được không?"

Response (OpenAI gọi `check_specific_slot` → trả về giá):
```json
{
  "success": true,
  "data": {
    "conversationId": 17,
    "reply": "Sân 1 vào tối nay (14/05) từ 19:00-21:00 còn trống. Giá 200.000đ/giờ → tổng 400.000đ, cọc 30% = 120.000đ. Bạn xác nhận đặt không?",
    "selectedCourtId": 1,
    "bookingId": null,
    "paymentUrl": null
  }
}
```

### Bước 5 — User xác nhận, bot tạo booking

User: "Ok đặt đi"

Response (OpenAI gọi `create_booking` → side-effect set `bookingId` + `paymentUrl`):
```json
{
  "success": true,
  "data": {
    "conversationId": 17,
    "reply": "Đã đặt thành công! Booking #9006788 — Sân 1, 14/05 19:00-21:00, cọc 120.000đ. Bấm link để thanh toán: https://pay.payos.vn/web/abc123",
    "selectedCourtId": 1,
    "bookingId": 9006788,
    "paymentUrl": "https://pay.payos.vn/web/abc123"
  }
}
```

FE xử lý:
1. Render bubble AI.
2. **`paymentUrl` có giá trị** → hiện CTA button rõ rệt: **"💳 Thanh toán ngay"** mở link PayOS trong WebView/Browser.
3. **`paymentUrl = null` nhưng `bookingId != null`** → user có thể đã dùng point đủ trừ hết cọc, booking đã confirmed luôn. Hiện "✅ Đặt thành công, không cần thanh toán".
4. **`bookingId` có giá trị** → có thể hiện chip "Xem booking" deeplink sang màn booking detail.

### Bước 6 — User quay lại sau (load conversation cũ)

User quay lại app sau vài giờ, mở lại conversation `id=17`:

```http
GET /api/chat/conversations/17/messages?page=1&size=50
Authorization: Bearer <JWT>
```

Response:
```json
{
  "success": true,
  "data": {
    "listObjects": [
      {
        "id": 101, "role": "USER",
        "content": "Tôi muốn đặt sân tối nay",
        "toolName": null, "toolArgsJson": null, "toolResultJson": null,
        "createdAt": "2026-05-14T14:00:00"
      },
      {
        "id": 102, "role": "ASSISTANT",
        "content": null,
        "toolName": "list_courts_in_branch",
        "toolArgsJson": "{}",
        "toolResultJson": null,
        "createdAt": "2026-05-14T14:00:01"
      },
      {
        "id": 103, "role": "TOOL",
        "content": null,
        "toolName": "list_courts_in_branch",
        "toolArgsJson": null,
        "toolResultJson": "{\"result\":{\"courts\":[...]}}",
        "createdAt": "2026-05-14T14:00:01"
      },
      {
        "id": 104, "role": "ASSISTANT",
        "content": "Chi nhánh Cầu Giấy có 5 sân:\n- Sân 1...",
        "toolName": null,
        "createdAt": "2026-05-14T14:00:03"
      }
      // ... nhiều messages
    ],
    "pageNumber": 1, "pageSize": 50, "totalRecords": 12, "totalPages": 1
  }
}
```

FE render:
- **`role: USER`** → bubble bên phải.
- **`role: ASSISTANT` + `content != null`** → bubble bên trái, render markdown.
- **`role: ASSISTANT` + `content = null` + `toolName != null`** → chip "🔍 Đang tra cứu \<toolName\>..." (tùy chọn, có thể ẩn hoàn toàn).
- **`role: TOOL`** → ẨN. Đây là raw output JSON từ tool gửi lại OpenAI, không hiển thị cho user.
- **`role: SYSTEM`** → ẨN.

> ⚠️ **KHÔNG** gửi lại các message ASSISTANT/TOOL về server khi gọi POST tiếp theo. FE chỉ gửi `message` của user. Backend tự load history từ DB và đẩy lên OpenAI.

Sau khi load, FE giữ `conversationId = 17` và tiếp tục gửi tin như bình thường.

> **Lưu ý quan trọng:** sau khi load history, FE muốn biết `selectedCourtId` hiện tại của conversation → **không có trong response messages**. Lấy từ item của `GET /api/chat/branches/{id}/conversations` (field `courtId`) khi user pick conversation đó, hoặc gửi 1 tin nhắn "thường" đầu tiên — response sẽ có `selectedCourtId`.

---

## Reference từng endpoint

### 1. `POST /api/chat/branches/{branchId}/messages`

Gửi 1 tin nhắn user. Tạo conversation mới nếu `conversationId = null`.

**Path params:**
| Param | Type | Mô tả |
|---|---|---|
| `branchId` | Long | ID chi nhánh user đang chat |

**Body:**
| Field | Bắt buộc | Type | Mô tả |
|---|---|---|---|
| `conversationId` | Không | Long \| null | `null` ở turn đầu → BE tạo mới. Các turn sau dùng id BE trả về. |
| `message` | **Có** | string | Nội dung user nhập. Không được rỗng. |

**Response data:**
| Field | Type | Mô tả |
|---|---|---|
| `conversationId` | Long | ID conversation. **Cache lại** sau turn đầu. |
| `reply` | string | Text AI trả về. Có thể chứa markdown nhẹ (xuống dòng, `**bold**`). |
| `selectedCourtId` | Long \| null | Sân đang được chốt. `null` khi chưa chọn → FE ẩn flow booking, hiện greeting "chọn sân trước". |
| `bookingId` | Long \| null | Có giá trị khi turn này AI tạo booking thành công. |
| `paymentUrl` | string \| null | Link PayOS. Có giá trị cùng `bookingId`. |

**Lỗi thường gặp:**

| HTTP | Tình huống | Body |
|---|---|---|
| 401 | Token sai/thiếu | `{ "success": false, "message": "Chưa đăng nhập" }` |
| 400 | `message` rỗng | `{ "success": false, "message": "Validation failed for argument ... [Field error ... 'message': rejected value [] ... default message [Nội dung không được để trống]]" }` — message dài do Spring bean validation. FE chỉ cần check `success=false` + `statusCode=400`. |
| 400 | `branchId` không tồn tại | `{ "success": false, "message": "Không tìm thấy chi nhánh id=99" }` |
| 400 | `conversationId` không thuộc user | `{ "success": false, "message": "Không tìm thấy cuộc trò chuyện" }` |

### 2. `GET /api/chat/me/conversations`

Tất cả conversation của user hiện tại, **mọi chi nhánh**.

**Query params:**
| Param | Default | Mô tả |
|---|---|---|
| `page` | 1 | Trang (>=1) |
| `size` | 10 | Số item / trang |

**Response item shape** (giống bên dưới phần `branches/{id}/conversations`).

### 3. `GET /api/chat/branches/{branchId}/conversations`

Conversations của user trong **1 chi nhánh cụ thể**. Cùng query params như trên.

**Item shape:**
```json
{
  "id": 17,
  "userId": 3,
  "userName": "Customer ChainStore",
  "branchId": 4,
  "branchName": "Cầu Giấy",
  "courtId": 1,
  "courtName": "Sân 1",
  "title": "Tôi muốn đặt sân tối nay",
  "status": "ACTIVE",
  "createdAt": "2026-05-14T14:00:00",
  "updatedAt": "2026-05-14T14:05:00"
}
```

**Note:** không có `last_message` trong response (khác Gemini API tham khảo). Nếu cần preview tin cuối, gọi `GET /api/chat/conversations/{id}/messages?page=1&size=1` riêng.

### 4. `GET /api/chat/conversations/{conversationId}/messages`

Tin nhắn của 1 conversation. Chỉ owner đọc được, không phải owner → 403.

**Query params:** `page`, `size` (default 10).

**Item shape:**
```json
{
  "id": 104,
  "role": "USER" | "ASSISTANT" | "TOOL" | "SYSTEM",
  "content": "string | null",
  "toolName": "string | null",
  "toolArgsJson": "string | null",
  "toolResultJson": "string | null",
  "createdAt": "2026-05-14T14:00:03"
}
```

**Sort:** ASC theo `id` (cũ → mới). FE infinite scroll lên trên: tăng `page` để load tin cũ hơn (cần đảo lại khi render).

### 5. `GET /api/admin/chat/conversations` *(Admin only)*

List **tất cả** conversations của **mọi user**. Yêu cầu JWT có role `ADMIN`. Trả về 400 + `"Access Denied"` nếu role khác.

**Query params** (tất cả optional):
| Param | Type | Mô tả |
|---|---|---|
| `branchId` | Long | Lọc theo chi nhánh |
| `userId` | Long | Lọc theo user |
| `status` | enum | `ACTIVE` (hiện chỉ có status này) |
| `page` | int | Default 1 |
| `size` | int | Default 10 |

**Response item shape:** giống endpoint #3 (có `userId`, `userName`, `branchId`, `branchName`, `courtId`, `courtName`, `title`, `status`, timestamps).

**Use case:** màn dashboard admin xem mọi cuộc trò chuyện, audit, lọc theo chi nhánh / user.

```http
GET /api/admin/chat/conversations?branchId=4&page=1&size=20
Authorization: Bearer <ADMIN_JWT>
```

---

## Gợi ý UI

### Màn AI Chat — Empty state (chưa có conversation)

```
┌────────────────────────────────────┐
│ ← Trợ lý AI - Cầu Giấy        ⋮  │
├────────────────────────────────────┤
│                                    │
│              🤖                    │
│                                    │
│     Xin chào! Tôi là trợ lý       │
│     đặt sân Cầu Giấy.             │
│     Bạn cần gì hôm nay?           │
│                                    │
│  ┌─────────────────────────────┐  │
│  │ 🎾 Đặt sân tối nay         │  │  ← suggested prompt
│  └─────────────────────────────┘  │
│  ┌─────────────────────────────┐  │
│  │ ⏰ Còn giờ nào cuối tuần?   │  │
│  └─────────────────────────────┘  │
│  ┌─────────────────────────────┐  │
│  │ 🏸 Có sân nào loại Indoor?  │  │
│  └─────────────────────────────┘  │
│                                    │
├────────────────────────────────────┤
│ [Nhập tin nhắn...        ]  [➤]  │
└────────────────────────────────────┘
```

### Màn AI Chat — Đang chat (có conversation)

```
┌────────────────────────────────────┐
│ ← Trợ lý AI - Cầu Giấy        ⋮  │
│   🏸 Đang đặt: Sân 1              │  ← badge selectedCourtId
├────────────────────────────────────┤
│                                    │
│                  ┌──────────────┐  │
│                  │ Tôi muốn đặt │  │  ← user bubble (phải)
│                  │ sân tối nay  │  │
│                  └──────────────┘  │
│                                    │
│ ┌────────────────────────────┐    │
│ │🤖 Chi nhánh có 5 sân:     │    │  ← AI bubble (trái)
│ │ - Sân 1 (Indoor)           │    │
│ │ - Sân 2 (Outdoor)          │    │
│ │ Bạn muốn chọn sân nào?    │    │
│ └────────────────────────────┘    │
│                                    │
│              ┌──────────┐          │
│              │ Sân 1 đi │          │
│              └──────────┘          │
│                                    │
│ 🔍 Đang chọn sân...                │  ← tool chip (tùy chọn)
│                                    │
│ ┌────────────────────────────┐    │
│ │🤖 Đã chọn Sân 1. Đặt giờ  │    │
│ │ nào, ngày nào?             │    │
│ └────────────────────────────┘    │
│                                    │
├────────────────────────────────────┤
│ [Nhập tin nhắn...        ]  [➤]  │
└────────────────────────────────────┘
```

### Màn AI Chat — Sau khi tạo booking

Bubble AI cuối + CTA thanh toán nổi bật:

```
│ ┌────────────────────────────┐    │
│ │🤖 Đã đặt thành công!       │    │
│ │ Booking #9006788           │    │
│ │ Sân 1, 14/05 19:00-21:00   │    │
│ │ Cọc: 120.000đ              │    │
│ └────────────────────────────┘    │
│                                    │
│ ┌────────────────────────────┐    │
│ │  💳 THANH TOÁN NGAY        │    │  ← CTA full-width
│ └────────────────────────────┘    │   từ paymentUrl
│                                    │
│ ┌────────────────────────────┐    │
│ │  📋 Xem booking            │    │  ← deeplink bookingId
│ └────────────────────────────┘    │
```

### Suggested prompts (chỉ hiện khi empty/conversation chưa có message)

- "Đặt sân tối nay"
- "Còn giờ nào cuối tuần?"
- "Có sân Indoor không?"
- "Giá sân bao nhiêu?"
- "Xem các sân của chi nhánh"

Khi user tap chip → đẩy thẳng vào input và auto-send (UX nhanh hơn).

---

## State machine khuyến nghị

FE quản lý 3 state trong màn chat:

| State | Khi nào | Hành vi |
|---|---|---|
| `IDLE` | Vừa mở màn / vừa nhận xong reply | Input enabled, gửi được. |
| `SENDING` | Vừa POST messages, đang chờ response | **Disable** input và nút gửi. Hiện bubble user lạc quan (optimistic) + typing indicator "🤖 ..." bên trái. |
| `ERROR` | Response trả `success: false` hoặc network fail | Hiện toast lỗi + nút "Thử lại" trên bubble user vừa gửi. |

**Lưu ý timing:** OpenAI có thể mất 2-15 giây vì có function calling (1 turn có thể gọi 2-3 tool). FE nên:

- < 3s: chỉ hiện typing indicator nhỏ.
- 3-8s: đổi label thành "Trợ lý đang tra cứu...".
- > 8s: vẫn giữ loading nhưng có thể hiện hint "Hệ thống đang xử lý, vui lòng đợi...".
- > 30s: timeout client side, hiện lỗi, cho retry.

---

## Khác biệt so với spec Gemini ref (lưu ý nếu bạn đã quen flow đó)

| Aspect | Gemini ref doc | ChainStore thực tế |
|---|---|---|
| Tạo conversation | `POST /api/ai-chat/conversations` riêng | **Không có**. Conversation tự tạo trong `POST messages` khi `conversationId=null` |
| Context | 1 conversation toàn cục | 1 conversation = 1 (user, branch) |
| Path POST tin | `/api/ai-chat/conversations/:id/messages` | `/api/chat/branches/{branchId}/messages` (branch trong path) |
| Body POST | `{content: "..."}` | `{conversationId, message: "..."}` |
| Response | `{user_message, ai_message, tool_calls_count}` | `{conversationId, reply, selectedCourtId, bookingId, paymentUrl}` |
| Pagination | `page=1&limit=20`, response `items/total/page/limit` | `page=1&size=10`, response `listObjects/totalRecords/pageNumber/pageSize` |
| Role enum | `user/assistant/function_call/function_response` | `USER/ASSISTANT/TOOL/SYSTEM` (uppercase) |
| Action tags | Có `[action:xxx] Label` | **Không có**. Reply là plain text/markdown. CTA dựa vào `paymentUrl` / `bookingId` ở response level. |
| Delete | `DELETE /api/ai-chat/conversations/:id` | **Không có** |
| Admin view | Không có | `GET /api/admin/chat/conversations` (chỉ list, không xem messages) |

---

## Xử lý lỗi & edge cases

| Tình huống | Triệu chứng | Xử lý FE |
|---|---|---|
| Token hết hạn | 401 + `{"message":"Token expired"}` | Chạy refresh token flow, retry. Nếu không có refresh, redirect login. |
| OpenAI hết quota | 200 + `reply` chứa "Trợ lý AI hiện không khả dụng (hết credit/bị rate-limit)" | Show banner cảnh báo + disable AI tab tạm thời (cooldown ~5 phút). Vẫn render reply như bình thường. |
| `branchId` không tồn tại | 400 + `message: "Không tìm thấy chi nhánh"` | Toast "Chi nhánh không tồn tại", quay về màn chọn branch. |
| Conversation orphan (đổi DB) | 400 + `message: "Không tìm thấy cuộc trò chuyện"` | Clear `conversationId` cache, gửi lại với `conversationId: null`. |
| Network timeout | Fetch reject | Toast "Mạng yếu, thử lại". Giữ tin user trên UI với badge ⚠️ + nút retry. |
| User gửi tin khi `SENDING` | (đã disable nút từ trước) | Đảm bảo đè debounce/disable kỹ — gửi 2 tin liên tiếp có thể tạo race condition với history. |

---

## Pseudo-code FE (TypeScript)

```ts
// State
let conversationId: number | null = null;
let selectedCourtId: number | null = null;
let messages: Message[] = [];
let status: "IDLE" | "SENDING" | "ERROR" = "IDLE";

// Khi mở màn
async function onOpenChat(branchId: number) {
  const res = await fetch(
    `/api/chat/branches/${branchId}/conversations?page=1&size=10`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  const json = await res.json();
  // KHÔNG có json.data.items — phải là listObjects
  const convs = json.data.listObjects;

  if (convs.length === 0) {
    // Empty state — đợi user nhập
    conversationId = null;
    messages = [];
    selectedCourtId = null;
  } else {
    // Hiện list, user pick một cái
    showConversationList(convs);
  }
}

// User pick 1 conversation cũ
async function onPickConversation(conv: ConversationDto) {
  conversationId = conv.id;
  selectedCourtId = conv.courtId; // ← lấy từ conversation item, KHÔNG có trong messages
  const res = await fetch(
    `/api/chat/conversations/${conv.id}/messages?page=1&size=50`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  const json = await res.json();
  messages = json.data.listObjects.filter(m =>
    m.role === "USER" || (m.role === "ASSISTANT" && m.content)
  );
}

// User gửi tin
async function onSendMessage(branchId: number, text: string) {
  if (status === "SENDING" || !text.trim()) return;

  status = "SENDING";
  messages.push({ role: "USER", content: text, optimistic: true });

  try {
    const res = await fetch(
      `/api/chat/branches/${branchId}/messages`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ conversationId, message: text }),
      }
    );
    const json = await res.json();

    if (!json.success) {
      throw new Error(json.message);
    }

    const { data } = json;
    conversationId = data.conversationId;     // cache cho turn sau
    selectedCourtId = data.selectedCourtId;
    messages.push({ role: "ASSISTANT", content: data.reply });

    if (data.bookingId && data.paymentUrl) {
      showPaymentCTA(data.bookingId, data.paymentUrl);
    }
    status = "IDLE";
  } catch (e) {
    status = "ERROR";
    showRetry(text);
  }
}
```

---

## Checklist FE trước khi release

- [ ] Lấy `branchId` đúng từ flow chọn chi nhánh.
- [ ] Cache `conversationId` sau turn đầu, dùng cho mọi turn sau.
- [ ] **Disable** nút gửi khi `status = SENDING` để chống double-submit.
- [ ] Render đúng 4 role: USER (phải), ASSISTANT-text (trái), ASSISTANT-tool (chip / ẩn), TOOL (ẩn).
- [ ] Render markdown nhẹ trong `reply`: `**bold**`, xuống dòng `\n`, list `-`.
- [ ] Khi `paymentUrl != null` → hiện CTA thanh toán nổi bật, không để user phải tìm link.
- [ ] Header hiện badge sân đang chọn khi `selectedCourtId != null`.
- [ ] Empty state có suggested prompts để user dễ bắt đầu.
- [ ] Typing indicator + label thay đổi theo thời gian chờ.
- [ ] Error toast + retry button khi network/server lỗi.
- [ ] Banner cảnh báo khi OpenAI hết quota (parse `reply` content).
- [ ] Pagination khi load history (page bắt đầu từ 1).

---

## Tài liệu liên quan

- **Swagger UI**: `http://localhost:1328/swagger-ui/index.html` → group **"AI Chatbot (theo chi nhánh)"**.
- **OpenAPI JSON**: `http://localhost:1328/v3/api-docs`.
- **BE Architecture doc**: [docs/AI_CHATBOT.md](AI_CHATBOT.md) — chi tiết kiến trúc, tool catalog, cách viết tool mới.

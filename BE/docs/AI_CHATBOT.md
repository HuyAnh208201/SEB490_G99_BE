# AI Chatbot — Hướng dẫn triển khai

Trợ lý đặt sân pickleball cho từng chi nhánh. User chat với bot, bot dẫn dắt
chọn sân → tra giờ trống → đặt sân → trả về link thanh toán PayOS.

Toàn bộ AI feature được implement bằng **OpenAI Chat Completions API + Function
Calling**. Spring Boot orchestrate vòng lặp tool, persist hội thoại vào MySQL.

---

## 1. Kiến trúc tổng quan

```
┌─────────┐     POST /api/chat/branches/{id}/messages     ┌────────────────────┐
│   FE    │ ───────────────────────────────────────────▶  │  ChatController    │
└─────────┘                                                └─────────┬──────────┘
                                                                     │
                                                                     ▼
                                                        ┌────────────────────────┐
                                                        │  ChatServiceImpl       │
                                                        │  (orchestrator loop)   │
                                                        └─┬────────┬─────────┬───┘
                                                          │        │         │
                              ┌───────────────────────────┘        │         │
                              ▼                                    ▼         ▼
                    ┌──────────────────┐               ┌──────────────────┐ ┌───────────────┐
                    │  OpenAIClient    │ ─REST/JSON──▶ │  OpenAI API      │ │ ToolRegistry  │
                    │  (RestTemplate)  │ ◀─tool_calls─ │  gpt-4o-mini     │ └──────┬────────┘
                    └──────────────────┘               └──────────────────┘        │
                                                                                   ▼
                                                                          ┌────────────────┐
                                                                          │  ChatTool impl │
                                                                          │  (7 tools)     │
                                                                          └────────────────┘
                                                                                   │
                                                                                   ▼
                                                                          BookingService,
                                                                          BranchRepository,
                                                                          CourtRepository...
```

**Đặc điểm:**

- Mỗi conversation gắn cứng `(user, branch)`. Sau khi user chốt sân trong chat,
  conversation lưu thêm `court_id`.
- Tool **không nhận** `branchId/courtId/userId` từ tham số LLM — luôn lấy từ
  `ToolContext` (server-side). Chống user dụ LLM book sân chi nhánh khác.
- Vòng lặp tool tối đa `app.openai.max-tool-iterations` (mặc định 5). Hết vòng
  mà chưa có plain text reply → trả fallback.

---

## 2. Bố cục code

```
src/main/java/base/api/
├── feature/chat/
│   ├── controller/ChatController.java         # 3 endpoint: send, list conv, list msg
│   ├── service/
│   │   ├── IChatService.java
│   │   └── impl/ChatServiceImpl.java          # orchestrator chính
│   ├── repository/
│   │   ├── IConversationRepository.java
│   │   └── IChatMessageRepository.java
│   └── dto/
│       ├── request/SendMessageRequest.java
│       └── response/{ChatTurnResponseDto, ConversationDto, ChatMessageDto}.java
│
├── shared/ai/
│   ├── openai/
│   │   ├── OpenAIClient.java                  # gọi /v1/chat/completions
│   │   ├── OpenAIException.java
│   │   ├── OpenAIQuotaException.java          # 429 + insufficient_quota
│   │   └── dto/                               # request/response wire format
│   │
│   └── tool/
│       ├── ChatTool.java                      # interface
│       ├── ToolContext.java                   # (userId, branchId, selectedCourtId)
│       ├── ToolResult.java                    # success / error / side-effect
│       ├── ToolRegistry.java                  # auto-pickup tất cả @Component ChatTool
│       └── impl/
│           ├── ListCourtsInBranchTool.java    # list_courts_in_branch
│           ├── SelectCourtTool.java           # select_court  (set conversation.court)
│           ├── GetBranchInfoTool.java         # get_branch_info
│           ├── GetCourtInfoTool.java          # get_court_info
│           ├── GetAvailableSlotsTool.java     # get_available_slots
│           ├── CheckSpecificSlotTool.java     # check_specific_slot
│           └── CreateBookingTool.java         # create_booking
│
└── shared/entity/
    ├── ConversationModel.java                 # bảng chat_conversation
    └── ChatMessageModel.java                  # bảng chat_message
```

---

## 3. Cấu hình

### 3.1 application.properties

```properties
# AI Chatbot (OpenAI)
app.openai.api-key=${OPENAI_API_KEY:}
app.openai.model=gpt-4o-mini
app.openai.base-url=https://api.openai.com/v1
app.openai.max-history-messages=20
app.openai.max-tool-iterations=5
app.openai.timeout-ms=30000
```

| Property | Ý nghĩa |
|---|---|
| `api-key` | **BẮT BUỘC** đọc từ env `OPENAI_API_KEY`. Tuyệt đối **không hardcode** vào file properties (đã từng có incident leak). |
| `model` | Chat model. `gpt-4o-mini` là default — rẻ, đủ dùng. |
| `base-url` | Có thể trỏ về proxy nội bộ / Azure OpenAI nếu cần. |
| `max-history-messages` | Số tin nhắn cuối cùng được nạp vào context. Cân bằng giữa token cost và chất lượng. |
| `max-tool-iterations` | Số lần lặp tool trong 1 turn. Quá thấp → bot không hoàn thành; quá cao → token cost cao + nguy cơ loop. |
| `timeout-ms` | Timeout HTTP gọi OpenAI. |

### 3.2 Env variable

Đặt trên môi trường chạy (KHÔNG commit):

```bash
# Linux/macOS
export OPENAI_API_KEY=sk-proj-...

# Windows PowerShell
$env:OPENAI_API_KEY="sk-proj-..."
```

Hoặc nếu deploy bằng Docker:

```yaml
environment:
  OPENAI_API_KEY: ${OPENAI_API_KEY}
```

### 3.3 Schema DB

JPA `ddl-auto=update` tự tạo 2 bảng khi start app lần đầu:

- **`chat_conversation`** — `(id, user_id, branch_id, court_id NULL, title, status, created_at, updated_at)`.
  Index: `(user_id, branch_id, status)` và `(user_id, branch_id, updated_at)`.
- **`chat_message`** — `(id, conversation_id, role, content LONGTEXT, tool_name, tool_args_json JSON, tool_result_json JSON, created_at)`.
  Index: `(conversation_id, created_at)`.

`MessageRole`: `USER | ASSISTANT | TOOL | SYSTEM`.
`ConversationStatus`: `ACTIVE | ...`.

---

## 4. API endpoints

Tất cả endpoint đều **cần JWT** (header `Authorization: Bearer <token>`).

### `POST /api/chat/branches/{branchId}/messages`

Gửi 1 tin nhắn user vào chi nhánh. Tạo conversation mới nếu `conversationId = null`.

```json
// Request
{
  "conversationId": null,           // hoặc id của conversation đang dùng
  "message": "Tôi muốn đặt sân tối nay"
}

// Response (success)
{
  "success": true,
  "data": {
    "conversationId": 42,
    "reply": "Chi nhánh có 5 sân... Bạn muốn xem sân nào?",
    "selectedCourtId": null,         // null khi chưa chốt sân
    "bookingId": null,               // có giá trị khi create_booking thành công
    "paymentUrl": null               // PayOS link, có khi tạo booking xong
  }
}
```

### `GET /api/chat/branches/{branchId}/conversations?page=1&size=10`

List conversation ACTIVE của user trong chi nhánh đó (sort `updated_at` desc).

### `GET /api/chat/conversations/{conversationId}/messages?page=1&size=20`

Lịch sử tin nhắn. Forbidden nếu user không phải owner.

### `GET /api/chat/me/conversations?page=1&size=10`

**Cần JWT.** Trả về **mọi conversation ACTIVE của user hiện tại trên tất cả chi nhánh** (lấy userId từ token). Sort `updated_at` desc. Dùng cho màn "Hộp thoại của tôi".

### `GET /api/admin/chat/conversations?branchId=&userId=&status=&page=&size=`

**Cần role ADMIN** (`@PreAuthorize("hasRole('ADMIN')")`). List toàn bộ conversations, các filter đều optional:

| Param | Kiểu | Mô tả |
|---|---|---|
| `branchId` | Long | Lọc theo chi nhánh |
| `userId` | Long | Lọc theo user cụ thể |
| `status` | enum `ConversationStatus` | Lọc theo trạng thái (`ACTIVE`...) |
| `page`, `size` | int | Phân trang chuẩn |

Response có thêm `userId` / `userName` ở mỗi conversation để admin biết ai chat (field này cũng có ở `/me/conversations` và `/branches/{id}/conversations` — backward compat OK vì là field mới nullable).

---

## 5. Tool catalog

| Tool name | Khi nào LLM gọi | Yêu cầu `selectedCourtId`? |
|---|---|:---:|
| `list_courts_in_branch` | User vào chat / muốn đổi sân | ❌ |
| `select_court` | User chỉ rõ 1 sân (vd "sân 3", "sân ngoài trời") | ❌ |
| `get_branch_info` | User hỏi địa chỉ / hotline / thông tin chi nhánh | ❌ |
| `get_court_info` | User hỏi chi tiết sân đang chọn | ✅ |
| `get_available_slots` | User hỏi "còn giờ nào", "sân tối nay đặt được khung nào" | ✅ |
| `check_specific_slot` | User chỉ giờ cụ thể, cần quote giá | ✅ |
| `create_booking` | User đã confirm ngày/giờ/giá | ✅ |

Tool blocked-by-context (cần `selectedCourtId`) sẽ trả `error` nếu user chưa chọn sân → LLM được hướng (qua system prompt) tự gọi `list_courts_in_branch` trước.

### Side-effects (đọc bởi orchestrator)

`ToolResult` có 3 field side-effect:

```java
public record ToolResult(
    Map<String, Object> payload,
    Long   bookingIdSideEffect,
    String paymentUrlSideEffect,
    Long   selectedCourtIdSideEffect
) { ... }
```

- `CreateBookingTool` → set `bookingIdSideEffect` + `paymentUrlSideEffect`. Service lan ra response DTO để FE redirect/hiện link PayOS.
- `SelectCourtTool` → set `selectedCourtIdSideEffect`. Service gọi `applyCourtSelection()` cập nhật `conversation.court_id`.

---

## 6. Vòng lặp orchestration (ChatServiceImpl)

```
for (iter = 0; iter < maxToolIterations; iter++) {

    1. Gửi (system + history + tools) lên OpenAI.
    2. Nhận assistant message.

    if (message có tool_calls) {
       a. Parse args JSON.
       b. Persist assistant message (kèm tool_name + tool_args_json).
       c. Append assistant message + tool_calls vào local messages.
       d. Execute tool qua ToolRegistry.find(name).execute(args, ctx).
       e. Apply side-effects: bookingId, paymentUrl, selectedCourtId.
       f. Persist TOOL message (kèm tool_result_json).
       g. Append tool message (với tool_call_id khớp) vào local messages.
       h. continue;
    }

    // plain text → kết thúc
    persist ASSISTANT text;
    break;
}
```

**Catch ngoài loop:**

- `OpenAIQuotaException` (429 + insufficient_quota) → reply "AI hết credit, dùng cách thường".
- `OpenAIException` chung → reply "AI tạm không khả dụng".
- Vẫn persist message để FE thấy.

---

## 7. Viết tool mới

1. Tạo class trong `src/main/java/base/api/shared/ai/tool/impl/` triển khai `ChatTool`:

```java
@Component
public class MyNewTool implements ChatTool {

    @Override public String name() { return "my_new_tool"; }   // snake_case

    @Override public String description() {
        return "Mô tả ngắn cho LLM hiểu khi nào dùng. Đừng dài dòng.";
    }

    @Override public Map<String, Object> parametersSchema() {
        // JSON Schema kiểu OpenAPI 3.x (lowercase types)
        Map<String, Object> dateProp = new LinkedHashMap<>();
        dateProp.put("type", "string");
        dateProp.put("description", "Định dạng YYYY-MM-DD.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("date", dateProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("date"));
        return schema;
    }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        // 1. Validate ctx (cần selectedCourtId? userId?).
        // 2. Validate args, return ToolResult.error(...) nếu sai.
        // 3. Gọi service / repository thường.
        // 4. Trả ToolResult.success(Map.of(...)) — payload sẽ thành tool message content.
    }
}
```

2. **KHÔNG** cần đăng ký thêm — `ToolRegistry` autowire tất cả bean `ChatTool`.

3. Nếu tool có side-effect (tạo entity, đổi state conversation):
   - Dùng `ToolResult.successWithBooking(...)` hoặc `ToolResult.successWithCourtSelection(...)`.
   - Hoặc bổ sung side-effect mới: thêm field vào `ToolResult` + factory method + handle trong `ChatServiceImpl`.

4. Cập nhật system prompt trong `ChatServiceImpl#buildSystemPrompt` nếu tool có hành vi đặc biệt (vd cần gọi trước/sau tool khác).

### Quy ước payload trả về LLM

- `LinkedHashMap` (giữ thứ tự) — đỡ token, dễ debug.
- Numeric primitive khi có thể (đừng wrap String unnecessarily).
- Đính kèm `note` field nếu cần giải thích semantics cho LLM (ví dụ `GetAvailableSlotsTool` có note giải thích `pricePerHour=null`).

### Anti-pattern

- ❌ Đừng nhận `branchId/courtId/userId` từ `args` — security risk.
- ❌ Đừng throw exception trong `execute()` → orchestrator sẽ catch và biến thành `ToolResult.error`, nhưng message lỗi ra LLM kém. Trả `ToolResult.error(msg)` chủ động.
- ❌ Đừng quên `required` trong JSON Schema — không có thì LLM hay quên truyền.

---

## 8. System prompt

`ChatServiceImpl#buildSystemPrompt(branch, selectedCourt)` build động prompt mỗi turn. Vài điểm quan trọng:

- **Biết chi nhánh đang phục vụ** (tên + địa chỉ).
- **Biết ngày hôm nay** (thứ + ngày VN).
- **Branching logic** theo `selectedCourt`:
  - Chưa chọn → bắt buộc `list_courts_in_branch` + `select_court` trước khi sang booking.
  - Đã chọn → unlock booking tools.
- **Quy tắc hệ thống**: 7 ngày tới, block 30 phút, cọc 30%, point 1=1000đ tối đa 50% cọc.
- **Format reply**:
  - Không trả JSON / code block.
  - Hỏi "còn khung trống" → bắt buộc liệt kê đầy đủ.
  - Trước `create_booking` → confirm với user.
  - Sau `create_booking` thành công → dán link PayOS thẳng vào câu trả lời.

Sửa prompt nếu thêm domain rule mới hoặc thay đổi flow.

---

## 9. Test thủ công (Swagger / curl)

```bash
# 1) Login lấy token
TOKEN=$(curl -s -X POST http://localhost:1328/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@123"}' \
    | jq -r '.data.accessToken')

# 2) Gửi tin nhắn đầu tiên cho branch 1
curl -s -X POST http://localhost:1328/api/chat/branches/1/messages \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"conversationId":null,"message":"Tôi muốn đặt sân tối nay"}' \
    | jq '.data'

# 3) Conversation ID copy từ response trên, gửi tiếp
curl -s -X POST http://localhost:1328/api/chat/branches/1/messages \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"conversationId":42,"message":"Cho sân 1 đi"}' \
    | jq '.data'
```

Hoặc dùng Swagger UI: `http://localhost:1328/swagger-ui/index.html` → group **"AI Chatbot (theo chi nhánh)"**.

---

## 10. Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp | Hướng xử |
|---|---|---|
| `OpenAI API key chưa được cấu hình` | Env `OPENAI_API_KEY` rỗng hoặc property bị override. | Set env var, restart app. Check `OpenAIClient#isConfigured()`. |
| Reply "AI hết credit/bị rate-limit" | `OpenAIQuotaException`. | Top up billing OpenAI hoặc giảm `max-tool-iterations` / `max-history-messages`. |
| Bot không gọi tool, chỉ chat | Schema tool sai (JSON Schema không hợp lệ) hoặc description quá mơ hồ. | Verify schema với OpenAPI Schema validator. Refine description. |
| `tool_call_id` mismatch error từ OpenAI | History assistant↔tool message bị lệch khi load lại. | Xem `historyToMessages()` — `lastAssistantToolCallId` reset khi USER xuất hiện. |
| Bot reply dạng JSON | Model "ngoan cố" trả JSON. | `sanitizeReply()` đã catch; nếu vẫn lọt, tăng nhấn mạnh trong system prompt. |
| Tool loop quá `maxToolIterations` | LLM gọi tool liên tục không dừng. | Tăng `max-tool-iterations` hoặc fix bug khiến tool result loop (ví dụ tool trả error giống nhau). |
| Bot book sân chi nhánh khác | LLM bị prompt-inject. | KHÔNG được nhận branchId từ args — luôn dùng `ToolContext`. Check tool impl. |

---

## 11. Cost & performance

- 1 turn ~ 1-3 lần gọi OpenAI (tool + final). Mỗi call ~ 1-2k input tokens (system + history + tools schema) + ~ 500 output tokens.
- Với `gpt-4o-mini`: ~ $0.0002 / turn. 5000 turn / tháng ~ $1.
- Bottleneck thường là latency OpenAI (~ 1-3s / call), không phải DB.

Tối ưu nếu cần:
- Giảm `max-history-messages` (mặc định 20 đã đủ cho luồng đặt sân).
- Cache `tools schema` (hiện rebuild mỗi turn — không tốn nhiều).
- Stream response nếu UX cần (yêu cầu đổi `OpenAIClient` sang SSE).

---

## 12. Bảo mật

- API key OpenAI luôn qua env var, không hardcode. Đã ignore `.claude/` và `*.log` trong `.gitignore`.
- Tool context ép server-side: `userId` từ JWT, `branchId` từ path param, `selectedCourtId` từ DB conversation. **Không tin args LLM**.
- Owner check ở `listMessages`: chỉ user tạo conversation đó mới đọc được.
- Log không in API key, không in PII trong production.

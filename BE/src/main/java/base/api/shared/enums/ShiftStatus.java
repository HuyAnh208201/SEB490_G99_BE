package base.api.shared.enums;

public enum ShiftStatus {
    DRAFT,            // BM vừa tạo, chưa publish
    PUBLISHED,        // BM đã publish, nhân viên đang làm việc
    CLOSED,           // Staff đã đóng ca + nhập tiền thực, chờ BM đối soát
    APPROVED,         // BM đã phê duyệt chênh lệch ✅
    REJECTED,         // BM từ chối — yêu cầu đếm lại ❌
    CANCELLED         // Ca bị hủy
}

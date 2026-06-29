-- =============================================================================
-- CHỈ chạy một lần nếu dữ liệu `price_policies.day_of_week` đang theo quy ước cũ:
--   1 = Chủ nhật, 2 = Thứ 2, 3 = Thứ 3, …, 7 = Thứ 7
-- Sau khi chạy, cột lưu theo ISO-8601 (giống java.time.DayOfWeek.getValue()):
--   1 = Thứ 2, 2 = Thứ 3, …, 6 = Thứ 7, 7 = Chủ nhật
--
-- KHÔNG chạy nếu DB đã nhập đúng ISO hoặc không chắc — sẽ làm sai dữ liệu.
-- Nên backup bảng trước khi chạy.
-- =============================================================================

UPDATE price_policies
SET day_of_week = CASE day_of_week
    WHEN 1 THEN 7
    WHEN 2 THEN 1
    WHEN 3 THEN 2
    WHEN 4 THEN 3
    WHEN 5 THEN 4
    WHEN 6 THEN 5
    WHEN 7 THEN 6
    ELSE day_of_week
END
WHERE day_of_week BETWEEN 1 AND 7;

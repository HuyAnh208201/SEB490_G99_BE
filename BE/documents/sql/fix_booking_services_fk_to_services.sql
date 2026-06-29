-- Pickaboo: booking_services.service_id -> services.id (cùng bảng với JPA ServiceModel).
-- Chạy từng lệnh ALTER một lần (Execute Statement), không chạy cả file một lượt nếu tool báo lỗi 1064.

-- Bước A — xem đúng tên FK hiện tại:
-- SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME
-- FROM information_schema.KEY_COLUMN_USAGE
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'booking_services'
--   AND COLUMN_NAME = 'service_id'
--   AND REFERENCED_TABLE_NAME IS NOT NULL;

-- Bước B — gỡ FK cũ (thay tên nếu khác FK54clakuh2mj7p7p7bq6l7vy1o):
ALTER TABLE `booking_services` DROP FOREIGN KEY `FK54clakuh2mj7p7p7bq6l7vy1o`;

-- Bước C — thêm FK trỏ bảng `services` (một dòng, có backtick tránh reserved word):
ALTER TABLE `booking_services` ADD CONSTRAINT `fk_booking_services_services` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`);

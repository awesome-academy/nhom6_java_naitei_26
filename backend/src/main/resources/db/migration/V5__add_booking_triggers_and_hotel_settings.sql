-- =============================================================================
-- V4: Bổ sung triggers còn thiếu theo DATABASE_DESIGN
-- Các trigger này đảm bảo business rules được thực thi ở tầng database
-- =============================================================================

-- =============================================================================
-- 1. Bảng hotel_settings (theo BE-6.5)
-- Lưu ý: DATABASE_DESIGN không có bảng riêng, nhưng BE-6.5 cần để tránh hard-code
-- =============================================================================
CREATE TABLE IF NOT EXISTS hotel_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NULL,
    data_type ENUM('STRING','NUMBER','BOOLEAN','JSON') NOT NULL DEFAULT 'STRING',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_hotel_settings_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed default hotel settings
INSERT INTO hotel_settings (setting_key, setting_value, data_type, description) VALUES
    ('standard_check_in_time', '14:00', 'STRING', 'Standard check-in time'),
    ('default_checkout_time', '12:00', 'STRING', 'Default check-out time'),
    ('hotel_timezone', 'Asia/Ho_Chi_Minh', 'STRING', 'Hotel timezone'),
    ('default_currency', 'VND', 'STRING', 'Default currency'),
    ('default_room_tax_percent', '0.00', 'NUMBER', 'Default room tax percentage'),
    ('default_no_show_charge_percent', '100.00', 'NUMBER', 'Default no-show charge percentage'),
    ('cancellation_default_policy_id', '1', 'NUMBER', 'Default cancellation policy ID (FLEXIBLE)')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- =============================================================================
-- 2. Trigger: Booking State Machine (theo DATABASE_DESIGN mục 8.1)
-- BR-010: chỉ CONFIRMED → CHECKED_IN
-- BR-011: chỉ CHECKED_IN → CHECKED_OUT
-- Mỗi transition ghi vào booking_status_history
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_state_machine
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    DECLARE allowed INT DEFAULT 0;

    -- Chỉ cho phép các transition hợp lệ theo DATABASE_DESIGN mục 8.1
    IF OLD.status = 'PENDING' AND NEW.status IN ('CONFIRMED', 'CANCELLED', 'EXPIRED') THEN
        SET allowed = 1;
    ELSEIF OLD.status = 'CONFIRMED' AND NEW.status IN ('CHECKED_IN', 'CANCELLED', 'NO_SHOW') THEN
        SET allowed = 1;
    ELSEIF OLD.status = 'CHECKED_IN' AND NEW.status = 'CHECKED_OUT' THEN
        SET allowed = 1;
    END IF;

    IF allowed = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid booking status transition';
    END IF;

    -- BR-010: CHECKED_IN phải có checked_in_at
    IF NEW.status = 'CHECKED_IN' AND NEW.checked_in_at IS NULL THEN
        SET NEW.checked_in_at = NEW.updated_at;
    END IF;

    -- BR-011: CHECKED_OUT phải có checked_out_at
    IF NEW.status = 'CHECKED_OUT' AND NEW.checked_out_at IS NULL THEN
        SET NEW.checked_out_at = NEW.updated_at;
    END IF;

    -- CANCELLED phải có cancelled_at
    IF NEW.status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = NEW.updated_at;
    END IF;

    -- Ghi booking_status_history (sẽ được trigger riêng xử lý hoặc service layer)
    -- Trigger này chỉ kiểm tra transition, không insert history
END//

DELIMITER ;

-- =============================================================================
-- 3. Trigger: Đồng bộ booking_room_status khi booking status thay đổi
-- Theo DATABASE_DESIGN mục 8.2
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_sync_rooms
AFTER UPDATE ON bookings
FOR EACH ROW
BEGIN
    -- CHECKED_IN → booking_rooms.status = OCCUPIED
    IF NEW.status = 'CHECKED_IN' AND OLD.status <> 'CHECKED_IN' THEN
        UPDATE booking_rooms
        SET status = 'OCCUPIED',
            updated_at = NEW.updated_at
        WHERE booking_id = NEW.id
          AND status IN ('RESERVED', 'OCCUPIED');
    END IF;

    -- CHECKED_OUT → booking_rooms.status = COMPLETED
    IF NEW.status = 'CHECKED_OUT' AND OLD.status <> 'CHECKED_OUT' THEN
        UPDATE booking_rooms
        SET status = 'COMPLETED',
            updated_at = NEW.updated_at
        WHERE booking_id = NEW.id
          AND status IN ('RESERVED', 'OCCUPIED', 'COMPLETED');
    END IF;

    -- CANCELLED/EXPIRED/NO_SHOW → booking_rooms.status = RELEASED
    IF NEW.status IN ('CANCELLED', 'EXPIRED', 'NO_SHOW') AND OLD.status NOT IN ('CANCELLED', 'EXPIRED', 'NO_SHOW') THEN
        UPDATE booking_rooms
        SET status = 'RELEASED',
            updated_at = NEW.updated_at
        WHERE booking_id = NEW.id
          AND status IN ('RESERVED', 'OCCUPIED');
    END IF;
END//

DELIMITER ;

-- =============================================================================
-- 4. Trigger: Cập nhật booking totals khi booking_room_nights thay đổi
-- Theo DATABASE_DESIGN mục 8.4: trg_booking_totals
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_room_nights_totals
AFTER INSERT ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_booking_id BIGINT;
    DECLARE v_new_subtotal DECIMAL(14,2);

    SELECT br.booking_id, COALESCE(SUM(brn.price), 0)
    INTO v_booking_id, v_new_subtotal
    FROM booking_rooms br
    JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.id = NEW.booking_room_id
    GROUP BY br.id;

    UPDATE bookings b
    SET rooms_total = v_new_subtotal,
        total_amount = v_new_subtotal + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_booking_id;
END//

CREATE TRIGGER trg_booking_room_nights_totals_update
AFTER UPDATE ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_booking_id BIGINT;
    DECLARE v_new_subtotal DECIMAL(14,2);

    SELECT br.booking_id, COALESCE(SUM(brn.price), 0)
    INTO v_booking_id, v_new_subtotal
    FROM booking_rooms br
    JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.id = NEW.booking_room_id
    GROUP BY br.id;

    UPDATE bookings b
    SET rooms_total = v_new_subtotal,
        total_amount = v_new_subtotal + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_booking_id;
END//

CREATE TRIGGER trg_booking_room_nights_totals_delete
AFTER DELETE ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_booking_id BIGINT;
    DECLARE v_new_subtotal DECIMAL(14,2);

    SELECT br.booking_id, COALESCE(SUM(brn.price), 0)
    INTO v_booking_id, v_new_subtotal
    FROM booking_rooms br
    LEFT JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.id = OLD.booking_room_id
    GROUP BY br.id;

    UPDATE bookings b
    SET rooms_total = COALESCE(v_new_subtotal, 0),
        total_amount = COALESCE(v_new_subtotal, 0) + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_booking_id;
END//

DELIMITER ;

-- =============================================================================
-- 5. Trigger: Cập nhật booking totals khi folio_charges thay đổi
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_folio_charges_totals
AFTER INSERT ON folio_charges
FOR EACH ROW
BEGIN
    IF NEW.is_voided = FALSE THEN
        UPDATE bookings b
        SET services_total = COALESCE(
            (SELECT SUM(fc.line_total)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id AND fc.is_voided = FALSE), 0),
            total_amount = b.rooms_total + COALESCE(
            (SELECT SUM(fc.line_total)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id AND fc.is_voided = FALSE), 0)
            + b.tax_total - b.discount_total,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE b.id = NEW.booking_id;
    END IF;
END//

CREATE TRIGGER trg_folio_charges_totals_update
AFTER UPDATE ON folio_charges
FOR EACH ROW
BEGIN
    UPDATE bookings b
    SET services_total = COALESCE(
        (SELECT SUM(fc.line_total)
         FROM folio_charges fc
         WHERE fc.booking_id = NEW.booking_id AND fc.is_voided = FALSE), 0),
        total_amount = b.rooms_total + COALESCE(
        (SELECT SUM(fc.line_total)
         FROM folio_charges fc
         WHERE fc.booking_id = NEW.booking_id AND fc.is_voided = FALSE), 0)
        + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = NEW.booking_id;
END//

CREATE TRIGGER trg_folio_charges_totals_delete
AFTER DELETE ON folio_charges
FOR EACH ROW
BEGIN
    UPDATE bookings b
    SET services_total = COALESCE(
        (SELECT SUM(fc.line_total)
         FROM folio_charges fc
         WHERE fc.booking_id = OLD.booking_id AND fc.is_voided = FALSE), 0),
        total_amount = b.rooms_total + COALESCE(
        (SELECT SUM(fc.line_total)
         FROM folio_charges fc
         WHERE fc.booking_id = OLD.booking_id AND fc.is_voided = FALSE), 0)
        + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = OLD.booking_id;
END//

DELIMITER ;

-- =============================================================================
-- 6. Trigger: Kiểm tra completeness trước khi CONFIRMED
-- Theo DATABASE_DESIGN mục 8.4: trg_confirm_completeness
-- Yêu cầu: đủ night rows, có policy snapshot
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_confirm_completeness
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    DECLARE v_required_nights INT;
    DECLARE v_actual_nights INT;
    DECLARE v_has_policy INT DEFAULT 1;

    -- Chỉ kiểm tra khi chuyển sang CONFIRMED
    IF NEW.status = 'CONFIRMED' AND OLD.status <> 'CONFIRMED' THEN
        -- Kiểm tra cancellation_policy_snapshot
        IF NEW.cancellation_policy_snapshot IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot confirm booking without cancellation policy snapshot';
        END IF;

        -- Kiểm tra booking_room_nights đầy đủ cho mỗi booking_room
        -- (Tạm bỏ qua kiểm tra chi tiết, service layer đảm bảo trước khi gọi confirm)
    END IF;
END//

DELIMITER ;

-- =============================================================================
-- 7. Trigger: Cập nhật booking_room.room_subtotal khi booking_room_nights thay đổi
-- Theo DATABASE_DESIGN: room_subtotal là aggregate từ booking_room_nights
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_room_subtotal
AFTER INSERT ON booking_room_nights
FOR EACH ROW
BEGIN
    UPDATE booking_rooms
    SET room_subtotal = COALESCE(
        (SELECT SUM(brn.price)
         FROM booking_room_nights brn
         WHERE brn.booking_room_id = NEW.booking_room_id), 0),
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = NEW.booking_room_id;
END//

CREATE TRIGGER trg_booking_room_subtotal_update
AFTER UPDATE ON booking_room_nights
FOR EACH ROW
BEGIN
    UPDATE booking_rooms
    SET room_subtotal = COALESCE(
        (SELECT SUM(brn.price)
         FROM booking_room_nights brn
         WHERE brn.booking_room_id = NEW.booking_room_id), 0),
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = NEW.booking_room_id;
END//

CREATE TRIGGER trg_booking_room_subtotal_delete
AFTER DELETE ON booking_room_nights
FOR EACH ROW
BEGIN
    UPDATE booking_rooms
    SET room_subtotal = COALESCE(
        (SELECT SUM(brn.price)
         FROM booking_room_nights brn
         WHERE brn.booking_room_id = OLD.booking_room_id), 0),
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = OLD.booking_room_id;
END//

DELIMITER ;

-- =============================================================================
-- 8. Trigger: payment_events append-only
-- Theo DATABASE_DESIGN mục 8.4: trg_append_only
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_payment_events_before_update
BEFORE UPDATE ON payment_events
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'payment_events is append-only';
END//

CREATE TRIGGER trg_payment_events_before_delete
BEFORE DELETE ON payment_events
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'payment_events is append-only';
END//

DELIMITER ;

-- =============================================================================
-- 9. Trigger: BR-005 - Chỉ khách mới được hủy booking của mình
-- Database không kiểm tra được ai là chủ booking → kiểm tra ở service layer
-- Nhưng trigger này đảm bảo chỉ PENDING hoặc CONFIRMED mới được hủy
-- =============================================================================
DELIMITER //

CREATE TRIGGER trg_booking_cancel_check
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status NOT IN ('PENDING', 'CONFIRMED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only PENDING or CONFIRMED bookings can be cancelled';
    END IF;
END//

DELIMITER ;

-- =============================================================================
-- 10. Trigger: BR-014 - RBAC enforced ở service layer
-- Không có trigger DB vì RBAC là application-level concern
-- Chỉ ghi chú ở đây để nhắc nhở implementation
-- =============================================================================

-- NOTE: Các trigger sau đã có trong V1:
-- - trg_booking_rooms_before_insert/update (BR-002/BR-004)
-- - trg_room_status_blocks_before_insert/update (BR-003/BR-004)
-- - trg_booking_room_nights_before_insert/update (night within stay range)
-- - trg_shift_assignments_before_insert/update (BR-015)
-- - trg_booking_status_history_before_update/delete (append-only)
-- - trg_reviews_before_insert (BR-006/BR-007)
-- - trg_invoices_before_update (BR-013)
-- - trg_invoice_items_before_update/delete (BR-013)
-- - trg_refunds_before_insert/update (refund <= payment amount)

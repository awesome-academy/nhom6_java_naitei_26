-- =============================================================================
-- V9: Fix booking room night aggregate triggers
--
-- booking_room_nights.price is the source of truth for sold room revenue.
-- Recalculate booking-level totals from all room nights in the booking, not only
-- from the single booking_room affected by the current row.
-- =============================================================================

DROP TRIGGER IF EXISTS trg_booking_room_nights_totals;
DROP TRIGGER IF EXISTS trg_booking_room_nights_totals_update;
DROP TRIGGER IF EXISTS trg_booking_room_nights_totals_delete;
DROP TRIGGER IF EXISTS trg_booking_room_subtotal;
DROP TRIGGER IF EXISTS trg_booking_room_subtotal_update;
DROP TRIGGER IF EXISTS trg_booking_room_subtotal_delete;

DELIMITER //

CREATE TRIGGER trg_booking_room_nights_totals
AFTER INSERT ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_booking_id BIGINT;
    DECLARE v_rooms_total DECIMAL(14,2);

    SELECT br.booking_id
    INTO v_booking_id
    FROM booking_rooms br
    WHERE br.id = NEW.booking_room_id;

    SELECT COALESCE(SUM(brn.price), 0)
    INTO v_rooms_total
    FROM booking_rooms br
    LEFT JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.booking_id = v_booking_id;

    UPDATE bookings b
    SET rooms_total = v_rooms_total,
        total_amount = v_rooms_total + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_booking_id;
END//

CREATE TRIGGER trg_booking_room_nights_totals_update
AFTER UPDATE ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_old_booking_id BIGINT;
    DECLARE v_new_booking_id BIGINT;
    DECLARE v_rooms_total DECIMAL(14,2);

    SELECT br.booking_id
    INTO v_old_booking_id
    FROM booking_rooms br
    WHERE br.id = OLD.booking_room_id;

    SELECT br.booking_id
    INTO v_new_booking_id
    FROM booking_rooms br
    WHERE br.id = NEW.booking_room_id;

    SELECT COALESCE(SUM(brn.price), 0)
    INTO v_rooms_total
    FROM booking_rooms br
    LEFT JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.booking_id = v_new_booking_id;

    UPDATE bookings b
    SET rooms_total = v_rooms_total,
        total_amount = v_rooms_total + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_new_booking_id;

    IF v_old_booking_id <> v_new_booking_id THEN
        SELECT COALESCE(SUM(brn.price), 0)
        INTO v_rooms_total
        FROM booking_rooms br
        LEFT JOIN booking_room_nights brn ON brn.booking_room_id = br.id
        WHERE br.booking_id = v_old_booking_id;

        UPDATE bookings b
        SET rooms_total = v_rooms_total,
            total_amount = v_rooms_total + b.services_total + b.tax_total - b.discount_total,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE b.id = v_old_booking_id;
    END IF;
END//

CREATE TRIGGER trg_booking_room_nights_totals_delete
AFTER DELETE ON booking_room_nights
FOR EACH ROW
BEGIN
    DECLARE v_booking_id BIGINT;
    DECLARE v_rooms_total DECIMAL(14,2);

    SELECT br.booking_id
    INTO v_booking_id
    FROM booking_rooms br
    WHERE br.id = OLD.booking_room_id;

    SELECT COALESCE(SUM(brn.price), 0)
    INTO v_rooms_total
    FROM booking_rooms br
    LEFT JOIN booking_room_nights brn ON brn.booking_room_id = br.id
    WHERE br.booking_id = v_booking_id;

    UPDATE bookings b
    SET rooms_total = v_rooms_total,
        total_amount = v_rooms_total + b.services_total + b.tax_total - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = v_booking_id;
END//

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

    IF OLD.booking_room_id <> NEW.booking_room_id THEN
        UPDATE booking_rooms
        SET room_subtotal = COALESCE(
            (SELECT SUM(brn.price)
             FROM booking_room_nights brn
             WHERE brn.booking_room_id = OLD.booking_room_id), 0),
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE id = OLD.booking_room_id;
    END IF;
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

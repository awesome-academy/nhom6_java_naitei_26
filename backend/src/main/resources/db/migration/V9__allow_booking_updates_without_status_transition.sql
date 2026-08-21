-- =============================================================================
-- V9: Allow booking updates that do not change status
--
-- trg_booking_state_machine should validate status transitions only when the
-- status actually changes. Other updates, such as aggregate totals maintained
-- by triggers, must not be rejected as invalid transitions.
-- =============================================================================

DROP TRIGGER IF EXISTS trg_booking_state_machine;

DELIMITER //

CREATE TRIGGER trg_booking_state_machine
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    DECLARE allowed INT DEFAULT 0;

    IF OLD.status = NEW.status THEN
        SET allowed = 1;
    ELSEIF OLD.status = 'PENDING' AND NEW.status IN ('CONFIRMED', 'CANCELLED', 'EXPIRED') THEN
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

    IF NEW.status = 'CHECKED_IN' AND NEW.checked_in_at IS NULL THEN
        SET NEW.checked_in_at = NEW.updated_at;
    END IF;

    IF NEW.status = 'CHECKED_OUT' AND NEW.checked_out_at IS NULL THEN
        SET NEW.checked_out_at = NEW.updated_at;
    END IF;

    IF NEW.status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = NEW.updated_at;
    END IF;
END//

DELIMITER ;

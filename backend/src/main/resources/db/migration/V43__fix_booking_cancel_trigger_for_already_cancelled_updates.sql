-- =============================================================================
-- Fix trg_booking_cancel_check (V5): the original condition fired on ANY update
-- to an already-CANCELLED booking (e.g. refund completion syncing refunded_amount /
-- payment_status), because Hibernate issues full-column UPDATEs and NEW.status stays
-- 'CANCELLED' while OLD.status is also 'CANCELLED' (not in PENDING/CONFIRMED). This
-- blocked RefundService.complete() with a 500 error even though no status transition
-- was actually being attempted. Only block when a transition INTO CANCELLED is
-- attempted from a status that isn't PENDING/CONFIRMED/already-CANCELLED.
-- =============================================================================
DROP TRIGGER IF EXISTS trg_booking_cancel_check;

DELIMITER //

CREATE TRIGGER trg_booking_cancel_check
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED' AND OLD.status NOT IN ('PENDING', 'CONFIRMED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only PENDING or CONFIRMED bookings can be cancelled';
    END IF;
END//

DELIMITER ;

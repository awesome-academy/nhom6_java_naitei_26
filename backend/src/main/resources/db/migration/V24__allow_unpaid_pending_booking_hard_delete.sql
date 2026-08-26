-- A customer may remove an unpaid PENDING hold. This is the only exception to the
-- append-only business timeline/payment-event rules; all committed bookings remain immutable.
DROP TRIGGER IF EXISTS trg_booking_status_history_before_delete;
DROP TRIGGER IF EXISTS trg_payment_events_before_delete;

DELIMITER //

CREATE TRIGGER trg_booking_status_history_before_delete
BEFORE DELETE ON booking_status_history
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM bookings booking
        WHERE booking.id = OLD.booking_id
          AND booking.status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM payments payment
              WHERE payment.booking_id = booking.id
                AND payment.status IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED')
          )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_status_history is append-only';
    END IF;
END//

CREATE TRIGGER trg_payment_events_before_delete
BEFORE DELETE ON payment_events
FOR EACH ROW
BEGIN
    IF OLD.payment_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM payments payment
        JOIN bookings booking ON booking.id = payment.booking_id
        WHERE payment.id = OLD.payment_id
          AND booking.status = 'PENDING'
          AND payment.status NOT IN ('SUCCEEDED','PARTIALLY_REFUNDED','REFUNDED')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'payment_events is append-only';
    END IF;
END//

DELIMITER ;

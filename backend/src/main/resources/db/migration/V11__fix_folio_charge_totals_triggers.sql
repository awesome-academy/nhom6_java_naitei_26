-- Recalculate folio aggregates according to DATABASE_DESIGN 7.2:
-- services_total is pre-tax service subtotal, while tax_total contains
-- room tax plus tax from every non-voided folio charge.

DROP TRIGGER IF EXISTS trg_folio_charges_totals;
DROP TRIGGER IF EXISTS trg_folio_charges_totals_update;
DROP TRIGGER IF EXISTS trg_folio_charges_totals_delete;

DELIMITER //

CREATE TRIGGER trg_folio_charges_totals
AFTER INSERT ON folio_charges
FOR EACH ROW
BEGIN
    UPDATE bookings b
    SET services_total = COALESCE(
            (SELECT SUM(fc.line_subtotal)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        tax_total = ROUND(
            b.rooms_total * b.room_tax_percent_snapshot / 100,
            2
        ) + COALESCE(
            (SELECT SUM(fc.tax_amount)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        total_amount = b.rooms_total
            + COALESCE(
                (SELECT SUM(fc.line_subtotal)
                 FROM folio_charges fc
                 WHERE fc.booking_id = NEW.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            + ROUND(b.rooms_total * b.room_tax_percent_snapshot / 100, 2)
            + COALESCE(
                (SELECT SUM(fc.tax_amount)
                 FROM folio_charges fc
                 WHERE fc.booking_id = NEW.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = NEW.booking_id;
END//

CREATE TRIGGER trg_folio_charges_totals_update
AFTER UPDATE ON folio_charges
FOR EACH ROW
BEGIN
    UPDATE bookings b
    SET services_total = COALESCE(
            (SELECT SUM(fc.line_subtotal)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        tax_total = ROUND(
            b.rooms_total * b.room_tax_percent_snapshot / 100,
            2
        ) + COALESCE(
            (SELECT SUM(fc.tax_amount)
             FROM folio_charges fc
             WHERE fc.booking_id = NEW.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        total_amount = b.rooms_total
            + COALESCE(
                (SELECT SUM(fc.line_subtotal)
                 FROM folio_charges fc
                 WHERE fc.booking_id = NEW.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            + ROUND(b.rooms_total * b.room_tax_percent_snapshot / 100, 2)
            + COALESCE(
                (SELECT SUM(fc.tax_amount)
                 FROM folio_charges fc
                 WHERE fc.booking_id = NEW.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = NEW.booking_id;

    IF OLD.booking_id <> NEW.booking_id THEN
        UPDATE bookings b
        SET services_total = COALESCE(
                (SELECT SUM(fc.line_subtotal)
                 FROM folio_charges fc
                 WHERE fc.booking_id = OLD.booking_id
                   AND fc.is_voided = FALSE),
                0
            ),
            tax_total = ROUND(
                b.rooms_total * b.room_tax_percent_snapshot / 100,
                2
            ) + COALESCE(
                (SELECT SUM(fc.tax_amount)
                 FROM folio_charges fc
                 WHERE fc.booking_id = OLD.booking_id
                   AND fc.is_voided = FALSE),
                0
            ),
            total_amount = b.rooms_total
                + COALESCE(
                    (SELECT SUM(fc.line_subtotal)
                     FROM folio_charges fc
                     WHERE fc.booking_id = OLD.booking_id
                       AND fc.is_voided = FALSE),
                    0
                )
                + ROUND(b.rooms_total * b.room_tax_percent_snapshot / 100, 2)
                + COALESCE(
                    (SELECT SUM(fc.tax_amount)
                     FROM folio_charges fc
                     WHERE fc.booking_id = OLD.booking_id
                       AND fc.is_voided = FALSE),
                    0
                )
                - b.discount_total,
            updated_at = CURRENT_TIMESTAMP(6)
        WHERE b.id = OLD.booking_id;
    END IF;
END//

CREATE TRIGGER trg_folio_charges_totals_delete
AFTER DELETE ON folio_charges
FOR EACH ROW
BEGIN
    UPDATE bookings b
    SET services_total = COALESCE(
            (SELECT SUM(fc.line_subtotal)
             FROM folio_charges fc
             WHERE fc.booking_id = OLD.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        tax_total = ROUND(
            b.rooms_total * b.room_tax_percent_snapshot / 100,
            2
        ) + COALESCE(
            (SELECT SUM(fc.tax_amount)
             FROM folio_charges fc
             WHERE fc.booking_id = OLD.booking_id
               AND fc.is_voided = FALSE),
            0
        ),
        total_amount = b.rooms_total
            + COALESCE(
                (SELECT SUM(fc.line_subtotal)
                 FROM folio_charges fc
                 WHERE fc.booking_id = OLD.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            + ROUND(b.rooms_total * b.room_tax_percent_snapshot / 100, 2)
            + COALESCE(
                (SELECT SUM(fc.tax_amount)
                 FROM folio_charges fc
                 WHERE fc.booking_id = OLD.booking_id
                   AND fc.is_voided = FALSE),
                0
            )
            - b.discount_total,
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE b.id = OLD.booking_id;
END//

DELIMITER ;

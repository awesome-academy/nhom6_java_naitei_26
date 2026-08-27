-- Admins can issue/void invoices through the same permissions as staff, but an
-- admin account does not necessarily have a staff_profiles row. Keep the
-- existing staff references and store the user actor as a fallback.
ALTER TABLE invoices
    ADD COLUMN issued_by_user_id BIGINT NULL AFTER issued_by,
    ADD COLUMN voided_by_user_id BIGINT NULL AFTER voided_by,
    ADD KEY idx_invoices_issued_by_user (issued_by_user_id),
    ADD KEY idx_invoices_voided_by_user (voided_by_user_id),
    ADD CONSTRAINT fk_invoices_issued_by_user
        FOREIGN KEY (issued_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoices_voided_by_user
        FOREIGN KEY (voided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE invoices
    DROP CHECK chk_invoices_issued,
    ADD CONSTRAINT chk_invoices_issued CHECK (
        status <> 'ISSUED'
        OR (
            invoice_number IS NOT NULL
            AND issued_at IS NOT NULL
            AND (issued_by IS NOT NULL OR issued_by_user_id IS NOT NULL)
        )
    );

-- Keep the new user actor immutable once the invoice is issued, just like the
-- existing staff actor column.
DELIMITER //

DROP TRIGGER IF EXISTS trg_invoices_before_update//

CREATE TRIGGER trg_invoices_before_update
BEFORE UPDATE ON invoices
FOR EACH ROW
BEGIN
    IF OLD.status = 'ISSUED' AND NEW.status NOT IN ('ISSUED', 'VOID') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'issued invoice can only remain ISSUED or become VOID';
    END IF;

    IF OLD.status = 'ISSUED' AND (
        NOT (OLD.booking_id <=> NEW.booking_id)
        OR NOT (OLD.invoice_number <=> NEW.invoice_number)
        OR NOT (OLD.issued_at <=> NEW.issued_at)
        OR NOT (OLD.issued_by <=> NEW.issued_by)
        OR NOT (OLD.issued_by_user_id <=> NEW.issued_by_user_id)
        OR NOT (OLD.buyer_name <=> NEW.buyer_name)
        OR NOT (OLD.buyer_address <=> NEW.buyer_address)
        OR NOT (OLD.buyer_tax_code <=> NEW.buyer_tax_code)
        OR NOT (OLD.buyer_email <=> NEW.buyer_email)
        OR NOT (OLD.subtotal <=> NEW.subtotal)
        OR NOT (OLD.discount_total <=> NEW.discount_total)
        OR NOT (OLD.tax_total <=> NEW.tax_total)
        OR NOT (OLD.total_amount <=> NEW.total_amount)
        OR NOT (OLD.currency <=> NEW.currency)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'issued invoice document fields are immutable';
    END IF;
END//

DELIMITER ;

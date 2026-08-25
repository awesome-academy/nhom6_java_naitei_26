-- Default deposit policy is configurable for future bookings. Existing bookings retain the
-- previous full-payment behavior by snapshotting 100 percent of their total as the deposit.
INSERT INTO hotel_settings (setting_key, setting_value, data_type, description)
VALUES ('default_deposit_percent', '30.00', 'NUMBER', 'Percentage of booking total required to confirm a reservation')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

ALTER TABLE bookings
    ADD COLUMN deposit_percent_snapshot DECIMAL(5,2) NOT NULL DEFAULT 100.00 AFTER total_amount,
    ADD COLUMN required_deposit_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 AFTER deposit_percent_snapshot;

UPDATE bookings
SET required_deposit_amount = total_amount
WHERE required_deposit_amount = 0.00 AND total_amount > 0.00;

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_deposit_percent
        CHECK (deposit_percent_snapshot > 0.00 AND deposit_percent_snapshot <= 100.00),
    ADD CONSTRAINT chk_bookings_required_deposit
        CHECK (required_deposit_amount >= 0.00 AND required_deposit_amount <= total_amount);

-- Booking confirmation requires the full booking total. Cancellation policies remain
-- responsible for refund calculations after a booking has been paid and cancelled.
ALTER TABLE bookings
    DROP CHECK chk_bookings_deposit_percent,
    DROP CHECK chk_bookings_required_deposit,
    DROP COLUMN deposit_percent_snapshot,
    DROP COLUMN required_deposit_amount;

DELETE FROM hotel_settings
WHERE setting_key = 'default_deposit_percent';

-- Website bookings are online-payment only. Keep the legacy columns for schema
-- compatibility, but disable the retired option for all existing room types.
UPDATE room_types
SET pay_at_hotel_enabled = FALSE,
    pay_at_hotel_price_adjustment_percent = 0.00;

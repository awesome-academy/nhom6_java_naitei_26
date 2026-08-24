-- =============================================================================
-- Bổ sung hotel_settings cho header hóa đơn PDF (BE-6.4)
-- =============================================================================
INSERT INTO hotel_settings (setting_key, setting_value, data_type, description) VALUES
    ('hotel_name', 'Playon Hotel', 'STRING', 'Hotel legal/trading name shown on invoice PDFs'),
    ('hotel_address', '', 'STRING', 'Hotel address shown on invoice PDFs'),
    ('hotel_tax_code', '', 'STRING', 'Hotel tax identification number shown on invoice PDFs'),
    ('hotel_phone', '', 'STRING', 'Hotel contact phone shown on invoice PDFs'),
    ('hotel_email', '', 'STRING', 'Hotel contact email shown on invoice PDFs')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

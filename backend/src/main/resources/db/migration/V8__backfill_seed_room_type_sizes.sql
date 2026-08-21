UPDATE room_types
SET size_sqm = CASE code
    WHEN 'STD' THEN 24.00
    WHEN 'DLX' THEN 35.00
    WHEN 'SUITE' THEN 50.00
    ELSE size_sqm
END
WHERE code IN ('STD', 'DLX', 'SUITE')
  AND size_sqm IS NULL;

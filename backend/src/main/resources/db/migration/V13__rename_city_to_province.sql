-- Rename city column to province for clarity
ALTER TABLE customer_profiles CHANGE COLUMN city province VARCHAR(100) NULL;

ALTER TABLE users
    ADD COLUMN avatar_storage_key TEXT NULL AFTER avatar_url,
    ADD COLUMN avatar_content_type VARCHAR(100) NULL AFTER avatar_storage_key;

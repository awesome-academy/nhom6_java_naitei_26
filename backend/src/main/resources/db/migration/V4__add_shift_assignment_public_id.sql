ALTER TABLE shift_assignments
    ADD COLUMN public_id CHAR(36) NULL AFTER id;

UPDATE shift_assignments
SET public_id = UUID()
WHERE public_id IS NULL;

ALTER TABLE shift_assignments
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uk_shift_assignments_public_id UNIQUE (public_id);

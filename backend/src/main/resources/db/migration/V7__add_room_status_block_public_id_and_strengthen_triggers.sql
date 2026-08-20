-- Add a stable public identifier without exposing the BIGINT primary key.
ALTER TABLE room_status_blocks
    ADD COLUMN public_id CHAR(36) NULL AFTER id;

UPDATE room_status_blocks
SET public_id = UUID()
WHERE public_id IS NULL;

ALTER TABLE room_status_blocks
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uk_room_status_blocks_public_id UNIQUE (public_id);

-- Serialize booking/block checks for the same room to close the BR-003/BR-004 race window.
DROP TRIGGER IF EXISTS trg_booking_rooms_before_insert;
DROP TRIGGER IF EXISTS trg_booking_rooms_before_update;
DROP TRIGGER IF EXISTS trg_room_status_blocks_before_insert;
DROP TRIGGER IF EXISTS trg_room_status_blocks_before_update;

DELIMITER //

CREATE TRIGGER trg_booking_rooms_before_insert
BEFORE INSERT ON booking_rooms
FOR EACH ROW
BEGIN
    DECLARE v_locked_room_id BIGINT;

    SELECT r.id
    INTO v_locked_room_id
    FROM rooms r
    WHERE r.id = NEW.room_id
    FOR UPDATE;

    IF NEW.check_out_date <= NEW.check_in_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_rooms requires check_out_date > check_in_date';
    END IF;

    IF NEW.status IN ('RESERVED', 'OCCUPIED') THEN
        IF EXISTS (
            SELECT 1
            FROM rooms r
            WHERE r.id = NEW.room_id
              AND (r.operational_status <> 'ACTIVE' OR r.is_active = FALSE OR r.deleted_at IS NOT NULL)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room is not operationally active';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM booking_rooms br
            WHERE br.room_id = NEW.room_id
              AND br.status IN ('RESERVED', 'OCCUPIED')
              AND NEW.check_in_date < br.check_out_date
              AND NEW.check_out_date > br.check_in_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps an active booking';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM room_status_blocks rb
            WHERE rb.room_id = NEW.room_id
              AND NEW.check_in_date < rb.end_date
              AND NEW.check_out_date > rb.start_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps a room status block';
        END IF;
    END IF;
END//

CREATE TRIGGER trg_booking_rooms_before_update
BEFORE UPDATE ON booking_rooms
FOR EACH ROW
BEGIN
    DECLARE v_locked_room_id BIGINT;

    SELECT r.id
    INTO v_locked_room_id
    FROM rooms r
    WHERE r.id = NEW.room_id
    FOR UPDATE;

    IF NEW.check_out_date <= NEW.check_in_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_rooms requires check_out_date > check_in_date';
    END IF;

    IF NEW.status IN ('RESERVED', 'OCCUPIED') THEN
        IF EXISTS (
            SELECT 1
            FROM rooms r
            WHERE r.id = NEW.room_id
              AND (r.operational_status <> 'ACTIVE' OR r.is_active = FALSE OR r.deleted_at IS NOT NULL)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room is not operationally active';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM booking_rooms br
            WHERE br.room_id = NEW.room_id
              AND br.id <> OLD.id
              AND br.status IN ('RESERVED', 'OCCUPIED')
              AND NEW.check_in_date < br.check_out_date
              AND NEW.check_out_date > br.check_in_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps an active booking';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM room_status_blocks rb
            WHERE rb.room_id = NEW.room_id
              AND NEW.check_in_date < rb.end_date
              AND NEW.check_out_date > rb.start_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps a room status block';
        END IF;
    END IF;
END//

CREATE TRIGGER trg_room_status_blocks_before_insert
BEFORE INSERT ON room_status_blocks
FOR EACH ROW
BEGIN
    DECLARE v_locked_room_id BIGINT;

    SELECT r.id
    INTO v_locked_room_id
    FROM rooms r
    WHERE r.id = NEW.room_id
    FOR UPDATE;

    IF NEW.end_date <= NEW.start_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room_status_blocks requires end_date > start_date';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM room_status_blocks rb
        WHERE rb.room_id = NEW.room_id
          AND NEW.start_date < rb.end_date
          AND NEW.end_date > rb.start_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an existing block';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.room_id = NEW.room_id
          AND br.status IN ('RESERVED', 'OCCUPIED')
          AND NEW.start_date < br.check_out_date
          AND NEW.end_date > br.check_in_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an active booking';
    END IF;
END//

CREATE TRIGGER trg_room_status_blocks_before_update
BEFORE UPDATE ON room_status_blocks
FOR EACH ROW
BEGIN
    DECLARE v_locked_room_id BIGINT;

    SELECT r.id
    INTO v_locked_room_id
    FROM rooms r
    WHERE r.id = NEW.room_id
    FOR UPDATE;

    IF NEW.end_date <= NEW.start_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room_status_blocks requires end_date > start_date';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM room_status_blocks rb
        WHERE rb.room_id = NEW.room_id
          AND rb.id <> OLD.id
          AND NEW.start_date < rb.end_date
          AND NEW.end_date > rb.start_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an existing block';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.room_id = NEW.room_id
          AND br.status IN ('RESERVED', 'OCCUPIED')
          AND NEW.start_date < br.check_out_date
          AND NEW.end_date > br.check_in_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an active booking';
    END IF;
END//

DELIMITER ;

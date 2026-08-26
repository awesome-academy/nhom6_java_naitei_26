-- Rate overrides are configured at room-type level only.
-- Existing room-only rows are rejected instead of being silently widened to an
-- entire room type or deleted. Migrate those rows explicitly before upgrading.

DELIMITER //

DROP PROCEDURE IF EXISTS migrate_rate_overrides_to_room_types//

CREATE PROCEDURE migrate_rate_overrides_to_room_types()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM rate_overrides
        WHERE room_type_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot drop room_id: migrate room-only rate overrides to room types first';
    END IF;

    ALTER TABLE rate_overrides
        DROP FOREIGN KEY fk_rate_room,
        DROP FOREIGN KEY fk_rate_room_type,
        DROP INDEX idx_rate_room_dates,
        DROP CHECK chk_rate_one_target;

    ALTER TABLE rate_overrides
        MODIFY room_type_id BIGINT NOT NULL,
        DROP COLUMN room_id,
        ADD CONSTRAINT fk_rate_room_type
            FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE;
END//

DELIMITER ;

CALL migrate_rate_overrides_to_room_types();
DROP PROCEDURE migrate_rate_overrides_to_room_types;

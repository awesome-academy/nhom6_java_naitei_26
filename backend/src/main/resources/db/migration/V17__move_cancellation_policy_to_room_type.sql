ALTER TABLE room_types
    ADD COLUMN cancellation_policy_id BIGINT NULL AFTER sort_order,
    ADD KEY idx_room_types_cancel_policy (cancellation_policy_id),
    ADD CONSTRAINT fk_room_types_cancel_policy
        FOREIGN KEY (cancellation_policy_id) REFERENCES cancellation_policies (id) ON DELETE RESTRICT;

SET @default_cancellation_policy_id := (
    SELECT id
    FROM cancellation_policies
    WHERE is_default = TRUE
      AND is_active = TRUE
    ORDER BY id
    LIMIT 1
);

UPDATE room_types
SET cancellation_policy_id = @default_cancellation_policy_id
WHERE cancellation_policy_id IS NULL
  AND deleted_at IS NULL;

ALTER TABLE booking_rooms
    ADD COLUMN cancellation_policy_id BIGINT NULL AFTER room_type_name_snapshot,
    ADD COLUMN cancellation_policy_snapshot JSON NULL AFTER cancellation_policy_id,
    ADD KEY idx_br_cancel_policy (cancellation_policy_id),
    ADD CONSTRAINT fk_booking_rooms_cancel_policy
        FOREIGN KEY (cancellation_policy_id) REFERENCES cancellation_policies (id) ON DELETE RESTRICT;

UPDATE booking_rooms br
JOIN bookings b ON b.id = br.booking_id
JOIN room_types rt ON rt.id = br.room_type_id
SET br.cancellation_policy_id = COALESCE(
        b.cancellation_policy_id,
        rt.cancellation_policy_id,
        @default_cancellation_policy_id
    );

UPDATE booking_rooms br
JOIN cancellation_policies p ON p.id = br.cancellation_policy_id
LEFT JOIN bookings b ON b.id = br.booking_id
SET br.cancellation_policy_snapshot = COALESCE(
    b.cancellation_policy_snapshot,
    JSON_OBJECT(
        'code', p.code,
        'name', p.name,
        'no_show_charge_percent', p.no_show_charge_percent,
        'rules', (
            SELECT JSON_EXTRACT(
                CONCAT(
                    '[',
                    COALESCE(
                        GROUP_CONCAT(
                            JSON_OBJECT(
                                'min_hours_before', r.min_hours_before,
                                'refund_percent', r.refund_percent
                            )
                            ORDER BY r.min_hours_before DESC
                            SEPARATOR ','
                        ),
                        ''
                    ),
                    ']'
                ),
                '$'
            )
            FROM cancellation_policy_rules r
            WHERE r.policy_id = p.id
        )
    )
);

ALTER TABLE bookings
    DROP FOREIGN KEY fk_bookings_cancel_policy;

ALTER TABLE bookings
    DROP INDEX idx_bookings_cancel_policy;

ALTER TABLE bookings
    DROP COLUMN cancellation_policy_id,
    DROP COLUMN cancellation_policy_snapshot;

DROP TRIGGER IF EXISTS trg_booking_confirm_completeness;

DELIMITER //

CREATE TRIGGER trg_booking_confirm_completeness
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    DECLARE v_missing_policy_snapshots INT DEFAULT 0;

    IF NEW.status = 'CONFIRMED' AND OLD.status <> 'CONFIRMED' THEN
        SELECT COUNT(*)
        INTO v_missing_policy_snapshots
        FROM booking_rooms br
        WHERE br.booking_id = NEW.id
          AND br.cancellation_policy_snapshot IS NULL;

        IF v_missing_policy_snapshots > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot confirm booking with rooms missing cancellation policy snapshot';
        END IF;
    END IF;
END//

DELIMITER ;

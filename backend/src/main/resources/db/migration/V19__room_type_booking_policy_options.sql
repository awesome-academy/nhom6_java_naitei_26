ALTER TABLE cancellation_policies
    ADD COLUMN price_adjustment_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER no_show_charge_percent,
    ADD CONSTRAINT chk_cancel_policy_price_adjustment CHECK (price_adjustment_percent BETWEEN 0 AND 100);

ALTER TABLE room_types
    ADD COLUMN pay_at_hotel_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER sort_order,
    ADD COLUMN pay_at_hotel_price_adjustment_percent DECIMAL(5,2) NOT NULL DEFAULT 10.00 AFTER pay_at_hotel_enabled,
    ADD CONSTRAINT chk_room_types_pay_at_hotel_adjustment CHECK (pay_at_hotel_price_adjustment_percent BETWEEN 0 AND 100);

CREATE TABLE room_type_cancellation_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    cancellation_policy_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_type_cancel_policy (room_type_id, cancellation_policy_id),
    KEY idx_rt_cancel_policy_policy (cancellation_policy_id),
    KEY idx_rt_cancel_policy_active (room_type_id, is_active, sort_order),
    CONSTRAINT fk_rt_cancel_policy_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_rt_cancel_policy_policy FOREIGN KEY (cancellation_policy_id) REFERENCES cancellation_policies (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @non_refund_policy_id := (
    SELECT id
    FROM cancellation_policies
    WHERE UPPER(code) = 'NON_REFUND'
    LIMIT 1
);

INSERT INTO room_type_cancellation_policies (
    room_type_id,
    cancellation_policy_id,
    is_active,
    sort_order
)
SELECT
    rt.id,
    COALESCE(rt.cancellation_policy_id, @non_refund_policy_id),
    TRUE,
    0
FROM room_types rt
WHERE COALESCE(rt.cancellation_policy_id, @non_refund_policy_id) IS NOT NULL;

ALTER TABLE booking_rooms
    ADD COLUMN payment_option VARCHAR(30) NOT NULL DEFAULT 'ONLINE' AFTER cancellation_policy_snapshot,
    ADD COLUMN price_adjustment_percent_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0.00 AFTER payment_option,
    ADD CONSTRAINT chk_booking_rooms_payment_option CHECK (payment_option IN ('ONLINE','PAY_AT_HOTEL')),
    ADD CONSTRAINT chk_booking_rooms_price_adjustment CHECK (price_adjustment_percent_snapshot BETWEEN 0 AND 100);

UPDATE booking_rooms br
JOIN cancellation_policies p ON p.id = br.cancellation_policy_id
SET br.price_adjustment_percent_snapshot = p.price_adjustment_percent;

ALTER TABLE room_types
    DROP FOREIGN KEY fk_room_types_cancel_policy;

ALTER TABLE room_types
    DROP INDEX idx_room_types_cancel_policy,
    DROP COLUMN cancellation_policy_id;

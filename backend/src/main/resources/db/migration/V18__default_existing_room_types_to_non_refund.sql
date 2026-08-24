SET @non_refund_cancellation_policy_id := (
    SELECT id
    FROM cancellation_policies
    WHERE code = 'NON_REFUND'
      AND is_active = TRUE
    ORDER BY id
    LIMIT 1
);

SET @default_cancellation_policy_id := (
    SELECT id
    FROM cancellation_policies
    WHERE is_default = TRUE
      AND is_active = TRUE
    ORDER BY id
    LIMIT 1
);

UPDATE room_types
SET cancellation_policy_id = @non_refund_cancellation_policy_id
WHERE @non_refund_cancellation_policy_id IS NOT NULL
  AND deleted_at IS NULL
  AND (
      cancellation_policy_id IS NULL
      OR cancellation_policy_id = @default_cancellation_policy_id
  );

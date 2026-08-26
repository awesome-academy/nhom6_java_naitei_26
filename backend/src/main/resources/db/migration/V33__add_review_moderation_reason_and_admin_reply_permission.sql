ALTER TABLE reviews
    ADD COLUMN moderation_reason TEXT NULL AFTER status;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'review:reply'
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

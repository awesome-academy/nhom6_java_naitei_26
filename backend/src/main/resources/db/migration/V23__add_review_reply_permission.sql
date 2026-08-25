INSERT INTO permissions (code, resource, action, description)
SELECT 'review:reply', 'review', 'reply', 'Reply to a guest review on behalf of the hotel'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'review:reply'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'review:reply'
WHERE role.code = 'STAFF'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

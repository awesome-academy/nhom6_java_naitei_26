INSERT INTO permissions (code, resource, action, description)
SELECT 'dashboard:read', 'dashboard', 'read', 'Read staff dashboard overview'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'dashboard:read'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'dashboard:read'
WHERE role.code IN ('ADMIN', 'STAFF')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

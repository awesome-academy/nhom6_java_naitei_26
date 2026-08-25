INSERT INTO permissions (code, resource, action, description)
SELECT 'revenue:read', 'revenue', 'read', 'Read revenue dashboard metrics'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'revenue:read'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'revenue:read'
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

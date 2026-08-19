INSERT INTO permissions (code, resource, action, description)
SELECT 'rbac:read', 'rbac', 'read', 'Read roles and permissions'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'rbac:read'
);

INSERT INTO permissions (code, resource, action, description)
SELECT 'rbac:manage', 'rbac', 'manage', 'Manage roles and role permissions'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'rbac:manage'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('rbac:read', 'rbac:manage')
WHERE role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

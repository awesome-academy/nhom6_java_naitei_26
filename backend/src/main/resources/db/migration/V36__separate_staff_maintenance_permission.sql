INSERT INTO permissions (code, resource, action, description)
SELECT 'maintenance:manage', 'maintenance', 'manage', 'Create, extend, and remove room maintenance blocks'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'maintenance:manage'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'maintenance:manage'
WHERE role.code IN ('ADMIN', 'STAFF')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

DELETE role_permission
FROM role_permissions role_permission
JOIN roles role ON role.id = role_permission.role_id
JOIN permissions permission ON permission.id = role_permission.permission_id
WHERE role.code = 'STAFF'
  AND permission.code IN ('room:create', 'room:update');

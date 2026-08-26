INSERT INTO permissions (code, resource, action, description)
SELECT 'shift:read_own', 'shift', 'read_own', 'Read own shift assignments'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'shift:read_own'
);

INSERT INTO permissions (code, resource, action, description)
SELECT 'shift:update_own', 'shift', 'update_own', 'Update own shift assignment status'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'shift:update_own'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('shift:read_own', 'shift:update_own')
WHERE role.code = 'STAFF'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

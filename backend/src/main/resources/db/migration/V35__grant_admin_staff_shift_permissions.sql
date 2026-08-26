INSERT INTO role_permissions (role_id, permission_id)
SELECT admin_role.id, permission.id
FROM roles admin_role
JOIN permissions permission ON permission.code IN ('shift:read_own', 'shift:update_own')
WHERE admin_role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = admin_role.id
        AND existing.permission_id = permission.id
  );

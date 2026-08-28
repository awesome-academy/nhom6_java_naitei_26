-- Housekeeping now uses the operational cycle CLEAN -> DIRTY -> CLEANING -> CLEAN.
-- Convert legacy values before narrowing the MySQL enum.
UPDATE rooms
SET housekeeping_status = 'CLEAN'
WHERE housekeeping_status = 'INSPECTED';

ALTER TABLE rooms
    MODIFY COLUMN housekeeping_status ENUM('CLEAN', 'DIRTY', 'CLEANING') NOT NULL DEFAULT 'CLEAN';

INSERT INTO permissions (code, resource, action, description)
SELECT 'room:housekeeping:update', 'room', 'housekeeping_update', 'Update room housekeeping status'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'room:housekeeping:update'
);

INSERT INTO permissions (code, resource, action, description)
SELECT 'room:occupancy:read', 'room', 'occupancy_read', 'Read room booking occupancy status'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'room:occupancy:read'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN ('room:housekeeping:update', 'room:occupancy:read')
WHERE role.code IN ('ADMIN', 'STAFF')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

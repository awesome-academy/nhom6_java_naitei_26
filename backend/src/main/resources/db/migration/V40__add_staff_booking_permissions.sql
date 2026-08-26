-- Staff-assisted booking creation and room booking-map data are internal operations.
INSERT INTO permissions (code, resource, action, description)
SELECT 'booking:create_staff', 'booking', 'create_staff', 'Create a booking for a customer from the staff portal'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'booking:create_staff'
);

INSERT INTO permissions (code, resource, action, description)
SELECT 'room:booking_map:read', 'room', 'booking_map_read', 'Read room availability timeline for staff booking'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'room:booking_map:read'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('booking:create_staff', 'room:booking_map:read')
WHERE role.code IN ('ADMIN', 'STAFF')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

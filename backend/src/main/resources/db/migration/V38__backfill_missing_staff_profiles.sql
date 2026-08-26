-- Staff accounts must always have a StaffProfile. This repairs legacy accounts
-- that received the STAFF role before the invitation-based hiring flow existed.
INSERT INTO staff_profiles (
    user_id,
    employee_code,
    position,
    department,
    hired_at,
    employment_status
)
SELECT
    user.id,
    CONCAT('EMP-', LPAD(user.id, 8, '0')),
    NULL,
    NULL,
    DATE(user.created_at),
    'ACTIVE'
FROM users user
JOIN user_roles user_role ON user_role.user_id = user.id
JOIN roles role ON role.id = user_role.role_id AND role.code = 'STAFF'
LEFT JOIN staff_profiles profile ON profile.user_id = user.id
WHERE profile.id IS NULL;

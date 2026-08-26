-- Normalize legacy multi-role users before enforcing the one-role invariant.
-- ADMIN wins over STAFF, and STAFF wins over CUSTOMER.
DELETE user_role
FROM user_roles user_role
JOIN roles role_to_delete ON role_to_delete.id = user_role.role_id
JOIN user_roles better_role_link ON better_role_link.user_id = user_role.user_id
JOIN roles better_role ON better_role.id = better_role_link.role_id
WHERE CASE role_to_delete.code
          WHEN 'ADMIN' THEN 3
          WHEN 'STAFF' THEN 2
          WHEN 'CUSTOMER' THEN 1
          ELSE 0
      END < CASE better_role.code
                WHEN 'ADMIN' THEN 3
                WHEN 'STAFF' THEN 2
                WHEN 'CUSTOMER' THEN 1
                ELSE 0
            END
   OR (CASE role_to_delete.code
           WHEN 'ADMIN' THEN 3
           WHEN 'STAFF' THEN 2
           WHEN 'CUSTOMER' THEN 1
           ELSE 0
       END = CASE better_role.code
                 WHEN 'ADMIN' THEN 3
                 WHEN 'STAFF' THEN 2
                 WHEN 'CUSTOMER' THEN 1
                 ELSE 0
             END
       AND better_role_link.role_id > user_role.role_id);

ALTER TABLE user_roles
    ADD UNIQUE KEY uk_user_roles_user (user_id);

ALTER TABLE staff_profiles
    ADD COLUMN email_at_termination VARCHAR(255) NULL AFTER terminated_at;

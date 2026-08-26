-- CANCELLED and ABSENT assignments remain in history but must not reserve a
-- staff/shift/date slot for a future effective assignment (BR-015).
ALTER TABLE shift_assignments
    DROP INDEX uk_shift_assign_staff_shift_date;

DROP TRIGGER IF EXISTS trg_shift_assignments_before_insert;
DROP TRIGGER IF EXISTS trg_shift_assignments_before_update;

DELIMITER //

CREATE TRIGGER trg_shift_assignments_before_insert
BEFORE INSERT ON shift_assignments
FOR EACH ROW
BEGIN
    IF NEW.shift_end_at <= NEW.shift_start_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift_end_at must be after shift_start_at';
    END IF;

    IF NEW.status IN ('SCHEDULED', 'COMPLETED') AND EXISTS (
        SELECT 1
        FROM shift_assignments sa
        WHERE sa.staff_id = NEW.staff_id
          AND sa.shift_id = NEW.shift_id
          AND sa.work_date = NEW.work_date
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment already exists for staff, shift, and date';
    END IF;

    IF NEW.status IN ('SCHEDULED', 'COMPLETED') AND EXISTS (
        SELECT 1
        FROM shift_assignments sa
        WHERE sa.staff_id = NEW.staff_id
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
          AND NEW.shift_start_at < sa.shift_end_at
          AND NEW.shift_end_at > sa.shift_start_at
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment overlaps an existing effective shift';
    END IF;
END//

CREATE TRIGGER trg_shift_assignments_before_update
BEFORE UPDATE ON shift_assignments
FOR EACH ROW
BEGIN
    IF NEW.shift_end_at <= NEW.shift_start_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift_end_at must be after shift_start_at';
    END IF;

    IF NEW.status IN ('SCHEDULED', 'COMPLETED') AND EXISTS (
        SELECT 1
        FROM shift_assignments sa
        WHERE sa.staff_id = NEW.staff_id
          AND sa.shift_id = NEW.shift_id
          AND sa.work_date = NEW.work_date
          AND sa.id <> OLD.id
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment already exists for staff, shift, and date';
    END IF;

    IF NEW.status IN ('SCHEDULED', 'COMPLETED') AND EXISTS (
        SELECT 1
        FROM shift_assignments sa
        WHERE sa.staff_id = NEW.staff_id
          AND sa.id <> OLD.id
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
          AND NEW.shift_start_at < sa.shift_end_at
          AND NEW.shift_end_at > sa.shift_start_at
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment overlaps an existing effective shift';
    END IF;
END//

DELIMITER ;

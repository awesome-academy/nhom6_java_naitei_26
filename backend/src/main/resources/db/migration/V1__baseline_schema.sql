CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    email_verified_at TIMESTAMP(6) NULL,
    password_hash TEXT NULL,
    phone VARCHAR(20) NULL,
    full_name VARCHAR(150) NOT NULL,
    avatar_url TEXT NULL,
    status ENUM('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DEACTIVATED') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    last_login_at TIMESTAMP(6) NULL,
    deleted_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_public_id (public_id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_status (status),
    KEY idx_users_status_deleted (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description TEXT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL,
    resource VARCHAR(60) NULL,
    action VARCHAR(30) NULL,
    description TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code),
    UNIQUE KEY uk_permissions_resource_action (resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by BIGINT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_assigned_by (assigned_by),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    date_of_birth DATE NULL,
    gender ENUM('MALE','FEMALE','OTHER','UNDISCLOSED') NULL,
    nationality VARCHAR(2) NULL,
    address_line VARCHAR(255) NULL,
    city VARCHAR(100) NULL,
    country VARCHAR(100) NULL,
    loyalty_points INT NOT NULL DEFAULT 0,
    total_stays INT NOT NULL DEFAULT 0,
    notes TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_profiles_user (user_id),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_customer_profiles_loyalty CHECK (loyalty_points >= 0),
    CONSTRAINT chk_customer_profiles_total_stays CHECK (total_stays >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    employee_code VARCHAR(20) NOT NULL,
    position VARCHAR(80) NOT NULL,
    department VARCHAR(80) NULL,
    hired_at DATE NOT NULL,
    terminated_at DATE NULL,
    employment_status ENUM('ACTIVE','ON_LEAVE','TERMINATED') NOT NULL DEFAULT 'ACTIVE',
    base_salary DECIMAL(14,2) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_profiles_user (user_id),
    UNIQUE KEY uk_staff_profiles_employee_code (employee_code),
    CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_staff_profiles_dates CHECK (terminated_at IS NULL OR terminated_at >= hired_at),
    CONSTRAINT chk_staff_profiles_salary CHECK (base_salary IS NULL OR base_salary >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider ENUM('GOOGLE','FACEBOOK','TWITTER') NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    provider_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    raw_profile JSON NULL,
    linked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_provider_user (provider, provider_user_id),
    KEY idx_social_user_provider (user_id, provider),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_type ENUM('EMAIL_VERIFICATION','PASSWORD_RESET','EMAIL_CHANGE') NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    requested_ip VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_tokens_hash (token_hash),
    KEY idx_auth_token_user_type (user_id, token_type),
    KEY idx_auth_tokens_expires_at (expires_at),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shifts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(80) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shifts_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shift_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    staff_id BIGINT NOT NULL,
    shift_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    shift_start_at TIMESTAMP(6) NOT NULL,
    shift_end_at TIMESTAMP(6) NOT NULL,
    status ENUM('SCHEDULED','COMPLETED','ABSENT','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    note TEXT NULL,
    assigned_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_assign_staff_shift_date (staff_id, shift_id, work_date),
    KEY idx_shift_assign_work_date (work_date, shift_id),
    KEY idx_shift_assign_staff_date (staff_id, work_date),
    KEY idx_shift_assign_shift (shift_id),
    KEY idx_shift_assign_assigned_by (assigned_by),
    CONSTRAINT fk_shift_assign_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_assign_shift FOREIGN KEY (shift_id) REFERENCES shifts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_assign_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_assign_time CHECK (shift_end_at > shift_start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE amenities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    icon VARCHAR(60) NULL,
    category ENUM('ROOM','BATHROOM','TECH','SERVICE') NOT NULL,
    is_filterable BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_amenities_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description TEXT NULL,
    bed_count INT NOT NULL,
    max_occupancy INT NOT NULL,
    max_adults INT NOT NULL,
    max_children INT NOT NULL DEFAULT 0,
    base_price DECIMAL(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    extra_bed_price DECIMAL(14,2) NULL,
    has_air_conditioner BOOLEAN NOT NULL DEFAULT TRUE,
    size_sqm DECIMAL(6,2) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_types_code (code),
    UNIQUE KEY uk_room_types_slug (slug),
    KEY idx_room_types_active_deleted (is_active, deleted_at),
    KEY idx_room_types_base_price (base_price),
    CONSTRAINT chk_room_types_bed_count CHECK (bed_count BETWEEN 1 AND 10),
    CONSTRAINT chk_room_types_occupancy CHECK (max_occupancy >= 1),
    CONSTRAINT chk_room_types_adults CHECK (max_adults >= 1 AND max_adults <= max_occupancy),
    CONSTRAINT chk_room_types_children CHECK (max_children >= 0 AND max_children <= max_occupancy),
    CONSTRAINT chk_room_types_base_price CHECK (base_price >= 0),
    CONSTRAINT chk_room_types_extra_bed_price CHECK (extra_bed_price IS NULL OR extra_bed_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_type_beds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    bed_type ENUM('SINGLE','DOUBLE','QUEEN','KING','SOFA_BED','BUNK') NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_type_beds_type (room_type_id, bed_type),
    CONSTRAINT fk_room_type_beds_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE,
    CONSTRAINT chk_room_type_beds_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_type_amenities (
    room_type_id BIGINT NOT NULL,
    amenity_id BIGINT NOT NULL,
    PRIMARY KEY (room_type_id, amenity_id),
    CONSTRAINT fk_room_type_amenities_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_type_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_type_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    url TEXT NOT NULL,
    storage_key TEXT NULL,
    alt_text VARCHAR(200) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_room_type_images_sort (room_type_id, sort_order),
    CONSTRAINT fk_room_type_images_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    view_type ENUM('SEA','CITY','GARDEN','POOL','MOUNTAIN','NONE') NOT NULL DEFAULT 'NONE',
    floor INT NULL,
    operational_status ENUM('ACTIVE','MAINTENANCE','OUT_OF_SERVICE','RENOVATION') NOT NULL DEFAULT 'ACTIVE',
    housekeeping_status ENUM('CLEAN','DIRTY','CLEANING','INSPECTED') NOT NULL DEFAULT 'CLEAN',
    price_override DECIMAL(14,2) NULL,
    max_occupancy_override INT NULL,
    description TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NULL,
    deleted_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_number_deleted (room_number, deleted_at),
    KEY idx_room_view_type (view_type),
    KEY idx_room_type_id (room_type_id),
    KEY idx_room_operational_active (operational_status, is_active),
    KEY idx_rooms_created_by (created_by),
    CONSTRAINT fk_rooms_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rooms_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_rooms_price_override CHECK (price_override IS NULL OR price_override >= 0),
    CONSTRAINT chk_rooms_max_occupancy_override CHECK (max_occupancy_override IS NULL OR max_occupancy_override >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_amenities (
    room_id BIGINT NOT NULL,
    amenity_id BIGINT NOT NULL,
    PRIMARY KEY (room_id, amenity_id),
    CONSTRAINT fk_room_amenities_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES amenities (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    url TEXT NOT NULL,
    storage_key TEXT NULL,
    alt_text VARCHAR(200) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_room_images_sort (room_id, sort_order),
    CONSTRAINT fk_room_images_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_status_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    block_type ENUM('MAINTENANCE','RENOVATION','OUT_OF_SERVICE','INTERNAL_USE','DEEP_CLEANING') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason TEXT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_room_block_dates (room_id, start_date, end_date),
    KEY idx_room_blocks_created_by (created_by),
    CONSTRAINT fk_room_blocks_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_blocks_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_room_blocks_dates CHECK (end_date > start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rate_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NULL,
    room_id BIGINT NULL,
    name VARCHAR(120) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    price DECIMAL(14,2) NOT NULL,
    weekdays JSON NULL,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_rate_room_type_dates (room_type_id, start_date, end_date),
    KEY idx_rate_room_dates (room_id, start_date, end_date),
    CONSTRAINT fk_rate_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_rate_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT chk_rate_one_target CHECK ((room_type_id IS NULL) <> (room_id IS NULL)),
    CONSTRAINT chk_rate_dates CHECK (end_date > start_date),
    CONSTRAINT chk_rate_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancellation_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    no_show_charge_percent DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cancellation_policies_code (code),
    KEY idx_cancellation_policy_default (is_default),
    CONSTRAINT chk_cancel_policy_no_show CHECK (no_show_charge_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancellation_policy_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_id BIGINT NOT NULL,
    min_hours_before INT NOT NULL,
    refund_percent DECIMAL(5,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cancel_policy_rules_hours (policy_id, min_hours_before),
    CONSTRAINT fk_cancel_rules_policy FOREIGN KEY (policy_id) REFERENCES cancellation_policies (id) ON DELETE CASCADE,
    CONSTRAINT chk_cancel_rules_min_hours CHECK (min_hours_before >= 0),
    CONSTRAINT chk_cancel_rules_refund CHECK (refund_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(80) NOT NULL,
    is_external BOOLEAN NOT NULL DEFAULT FALSE,
    requires_account BOOLEAN NOT NULL DEFAULT FALSE,
    commission_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_sources_code (code),
    CONSTRAINT chk_booking_sources_commission CHECK (commission_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    booking_code VARCHAR(20) NOT NULL,
    customer_id BIGINT NULL,
    source_id BIGINT NOT NULL,
    source_commission_percent_snapshot DECIMAL(5,2) NULL,
    external_reference VARCHAR(100) NULL,
    status ENUM('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW','EXPIRED') NOT NULL DEFAULT 'PENDING',
    contact_name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    contact_phone VARCHAR(20) NULL,
    adults INT NOT NULL DEFAULT 1,
    children INT NOT NULL DEFAULT 0,
    rooms_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    services_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    discount_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    tax_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    room_tax_percent_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    payment_status ENUM('UNPAID','PARTIALLY_PAID','PAID','PARTIALLY_REFUNDED','REFUNDED') NOT NULL DEFAULT 'UNPAID',
    cancellation_policy_id BIGINT NULL,
    cancellation_policy_snapshot JSON NULL,
    hold_expires_at TIMESTAMP(6) NULL,
    special_requests TEXT NULL,
    internal_notes TEXT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    checked_in_at TIMESTAMP(6) NULL,
    checked_in_by BIGINT NULL,
    checked_out_at TIMESTAMP(6) NULL,
    checked_out_by BIGINT NULL,
    cancelled_at TIMESTAMP(6) NULL,
    cancelled_by BIGINT NULL,
    cancellation_reason TEXT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_public_id (public_id),
    UNIQUE KEY uk_bookings_code (booking_code),
    UNIQUE KEY uk_bookings_source_external_ref (source_id, external_reference),
    KEY idx_booking_customer_status (customer_id, status),
    KEY idx_booking_status_created (status, created_at),
    KEY idx_booking_status_hold (status, hold_expires_at),
    KEY idx_booking_source (source_id),
    KEY idx_bookings_cancel_policy (cancellation_policy_id),
    KEY idx_bookings_checked_in_by (checked_in_by),
    KEY idx_bookings_checked_out_by (checked_out_by),
    KEY idx_bookings_cancelled_by (cancelled_by),
    KEY idx_bookings_created_by (created_by),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_source FOREIGN KEY (source_id) REFERENCES booking_sources (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_cancel_policy FOREIGN KEY (cancellation_policy_id) REFERENCES cancellation_policies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_checked_in_by FOREIGN KEY (checked_in_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_checked_out_by FOREIGN KEY (checked_out_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    -- Tổng booking = tiền phòng + dịch vụ + thuế - giảm giá.
    -- Các cột total là aggregate để đọc nhanh, nhưng phải được cập nhật cùng transaction ở service/trigger sau này.
    CONSTRAINT chk_bookings_amounts CHECK (total_amount = rooms_total + services_total + tax_total - discount_total),
    -- paid_amount là tổng tiền đã thu thành công; refunded_amount tách riêng để còn đối soát dòng tiền.
    CONSTRAINT chk_bookings_paid CHECK (paid_amount >= 0 AND paid_amount <= total_amount + 0.01),
    CONSTRAINT chk_bookings_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= paid_amount),
    CONSTRAINT chk_bookings_occupancy CHECK (adults >= 1 AND children >= 0),
    CONSTRAINT chk_bookings_checked_in_state CHECK (status <> 'CHECKED_IN' OR checked_in_at IS NOT NULL),
    CONSTRAINT chk_bookings_checked_out_state CHECK (status <> 'CHECKED_OUT' OR checked_out_at IS NOT NULL),
    CONSTRAINT chk_bookings_cancelled_state CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    room_type_id BIGINT NOT NULL,
    room_type_code_snapshot VARCHAR(30) NOT NULL,
    room_type_name_snapshot VARCHAR(120) NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    -- Số đêm được MySQL tự tính theo khoảng nửa mở [check_in_date, check_out_date).
    -- Ví dụ 20/08 -> 22/08 = 2 đêm; khách khác được nhận phòng đúng ngày 22/08.
    nights INT GENERATED ALWAYS AS (DATEDIFF(check_out_date, check_in_date)) STORED,
    room_subtotal DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    status ENUM('RESERVED','OCCUPIED','COMPLETED','RELEASED','MOVED_OUT') NOT NULL DEFAULT 'RESERVED',
    guest_count INT NOT NULL DEFAULT 1,
    moved_from_booking_room_id BIGINT NULL,
    assigned_at TIMESTAMP(6) NULL,
    assigned_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_rooms_id_booking (id, booking_id),
    KEY idx_br_booking (booking_id),
    KEY idx_br_room_status (room_id, status),
    KEY idx_br_dates_status (room_id, check_in_date, check_out_date, status),
    KEY idx_br_arrivals (check_in_date, status),
    KEY idx_br_room_type (room_type_id),
    KEY idx_br_moved_from (moved_from_booking_room_id),
    KEY idx_br_assigned_by (assigned_by),
    CONSTRAINT fk_booking_rooms_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_rooms_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_rooms_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_rooms_moved_from FOREIGN KEY (moved_from_booking_room_id) REFERENCES booking_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_rooms_assigned_by FOREIGN KEY (assigned_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT chk_booking_rooms_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_booking_rooms_guest_count CHECK (guest_count >= 1),
    CONSTRAINT chk_booking_rooms_subtotal CHECK (room_subtotal >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_room_nights (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_room_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    price DECIMAL(14,2) NOT NULL,
    rate_override_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_brn_room_stay_date (booking_room_id, stay_date),
    KEY idx_brn_stay_date (stay_date),
    KEY idx_brn_rate_override (rate_override_id),
    CONSTRAINT fk_brn_booking_room FOREIGN KEY (booking_room_id) REFERENCES booking_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_brn_rate_override FOREIGN KEY (rate_override_id) REFERENCES rate_overrides (id) ON DELETE RESTRICT,
    CONSTRAINT chk_brn_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_guests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    booking_room_id BIGINT NULL,
    full_name VARCHAR(150) NOT NULL,
    nationality VARCHAR(2) NULL,
    id_document_type ENUM('NATIONAL_ID','PASSPORT','DRIVER_LICENSE') NULL,
    id_document_number_encrypted VARBINARY(512) NULL,
    id_document_lookup_hash VARBINARY(64) NULL,
    date_of_birth DATE NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_bg_booking (booking_id),
    KEY idx_bg_booking_room (booking_room_id),
    KEY idx_bg_room_booking (booking_room_id, booking_id),
    KEY idx_bg_doc_hash (id_document_lookup_hash),
    CONSTRAINT fk_booking_guests_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    -- Composite FK bảo đảm booking_guest không trỏ nhầm sang booking_room thuộc booking khác.
    CONSTRAINT fk_booking_guests_room_booking FOREIGN KEY (booking_room_id, booking_id) REFERENCES booking_rooms (id, booking_id) ON DELETE RESTRICT,
    -- Số giấy tờ không lưu plaintext: nếu có ciphertext thì phải có lookup hash để tìm kiếm exact match.
    CONSTRAINT chk_booking_guests_doc_pair CHECK ((id_document_number_encrypted IS NULL AND id_document_lookup_hash IS NULL) OR (id_document_number_encrypted IS NOT NULL AND id_document_lookup_hash IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    from_status ENUM('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW','EXPIRED') NULL,
    to_status ENUM('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW','EXPIRED') NOT NULL,
    actor_type ENUM('USER','SYSTEM') NOT NULL,
    changed_by BIGINT NULL,
    source ENUM('MANUAL','PAYMENT_CALLBACK','HOLD_EXPIRY_JOB','NO_SHOW_JOB','OTA_IMPORT','SYSTEM_OTHER') NOT NULL,
    reason TEXT NULL,
    metadata JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_bsh_booking_created (booking_id, created_at),
    KEY idx_bsh_changed_by (changed_by),
    CONSTRAINT fk_bsh_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bsh_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_bsh_actor CHECK ((actor_type = 'USER' AND changed_by IS NOT NULL) OR (actor_type = 'SYSTEM' AND changed_by IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category ENUM('FNB','LAUNDRY','SPA','TRANSPORT','MINIBAR','PENALTY','OTHER') NOT NULL,
    unit_price DECIMAL(14,2) NOT NULL,
    tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_items_code (code),
    CONSTRAINT chk_service_items_price CHECK (unit_price >= 0),
    CONSTRAINT chk_service_items_tax CHECK (tax_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE folio_charges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    service_item_id BIGINT NULL,
    description VARCHAR(200) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    unit_price DECIMAL(14,2) NOT NULL,
    line_subtotal DECIMAL(14,2) NOT NULL,
    discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    line_total DECIMAL(14,2) NOT NULL,
    charged_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    charged_by BIGINT NULL,
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    voided_at TIMESTAMP(6) NULL,
    voided_by BIGINT NULL,
    void_reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_fc_booking_voided (booking_id, is_voided),
    KEY idx_fc_charged_at (charged_at),
    KEY idx_fc_service_item (service_item_id),
    KEY idx_fc_charged_by (charged_by),
    KEY idx_fc_voided_by (voided_by),
    CONSTRAINT fk_folio_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_folio_service_item FOREIGN KEY (service_item_id) REFERENCES service_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_folio_charged_by FOREIGN KEY (charged_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_folio_voided_by FOREIGN KEY (voided_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    -- line_subtotal = số lượng * đơn giá snapshot tại thời điểm phát sinh dịch vụ.
    -- line_total = sau giảm giá + thuế; dùng để đóng băng chi phí dịch vụ trước khi xuất hóa đơn.
    CONSTRAINT chk_folio_quantity CHECK (quantity > 0),
    CONSTRAINT chk_folio_subtotal CHECK (line_subtotal = ROUND(quantity * unit_price, 2)),
    CONSTRAINT chk_folio_discount CHECK (discount_amount >= 0 AND discount_amount <= line_subtotal),
    CONSTRAINT chk_folio_total CHECK (line_total = line_subtotal - discount_amount + tax_amount),
    CONSTRAINT chk_folio_void CHECK (is_voided = FALSE OR (voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(255) NOT NULL,
    invoice_number VARCHAR(30) NULL,
    booking_id BIGINT NOT NULL,
    status ENUM('DRAFT','ISSUED','VOID') NOT NULL DEFAULT 'DRAFT',
    payment_status ENUM('UNPAID','PARTIALLY_PAID','PAID','PARTIALLY_REFUNDED','REFUNDED') NOT NULL DEFAULT 'UNPAID',
    issued_at TIMESTAMP(6) NULL,
    issued_by BIGINT NULL,
    buyer_name VARCHAR(150) NOT NULL,
    buyer_address TEXT NULL,
    buyer_tax_code VARCHAR(20) NULL,
    buyer_email VARCHAR(255) NULL,
    subtotal DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    discount_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    tax_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    pdf_url TEXT NULL,
    pdf_storage_key TEXT NULL,
    replaces_invoice_id BIGINT NULL,
    voided_at TIMESTAMP(6) NULL,
    voided_by BIGINT NULL,
    void_reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_public_id (public_id),
    UNIQUE KEY uk_invoices_number (invoice_number),
    KEY idx_inv_booking (booking_id),
    KEY idx_inv_status_issued (status, issued_at),
    KEY idx_invoices_replaces (replaces_invoice_id),
    KEY idx_invoices_issued_by (issued_by),
    KEY idx_invoices_voided_by (voided_by),
    CONSTRAINT fk_invoices_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_issued_by FOREIGN KEY (issued_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_voided_by FOREIGN KEY (voided_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_replaces FOREIGN KEY (replaces_invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    -- Hóa đơn tổng hợp từ invoice_items: subtotal - discount_total + tax_total.
    -- Sau khi ISSUED, trigger bên dưới chặn sửa các trường chứng từ theo BR-013.
    CONSTRAINT chk_invoices_total CHECK (total_amount = subtotal - discount_total + tax_total),
    CONSTRAINT chk_invoices_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= paid_amount),
    CONSTRAINT chk_invoices_issued CHECK (status <> 'ISSUED' OR (invoice_number IS NOT NULL AND issued_at IS NOT NULL AND issued_by IS NOT NULL)),
    CONSTRAINT chk_invoices_draft CHECK (status <> 'DRAFT' OR (invoice_number IS NULL AND issued_at IS NULL)),
    CONSTRAINT chk_invoices_void CHECK (status <> 'VOID' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    line_type ENUM('ROOM','SERVICE','ADJUSTMENT') NOT NULL,
    description VARCHAR(200) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    unit_price DECIMAL(14,2) NOT NULL,
    line_subtotal DECIMAL(14,2) NOT NULL,
    discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    line_total DECIMAL(14,2) NOT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_invoice_items_invoice (invoice_id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    -- Mỗi dòng hóa đơn cũng tự kiểm tra phép tính tiền để tránh header và line item lệch nhau.
    -- ADJUSTMENT là loại duy nhất được phép âm để giảm/trừ tiền có giải thích.
    CONSTRAINT chk_invoice_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_invoice_items_subtotal CHECK (line_subtotal = ROUND(quantity * unit_price, 2)),
    CONSTRAINT chk_invoice_items_discount CHECK (discount_amount >= 0),
    CONSTRAINT chk_invoice_items_total CHECK (line_total = line_subtotal - discount_amount + tax_amount),
    CONSTRAINT chk_invoice_items_non_adjustment CHECK (line_type = 'ADJUSTMENT' OR (line_subtotal >= 0 AND line_total >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_code VARCHAR(30) NOT NULL,
    booking_id BIGINT NOT NULL,
    invoice_id BIGINT NULL,
    method ENUM('INTERNET_BANKING','CARD','CASH','BANK_TRANSFER','E_WALLET') NOT NULL,
    provider VARCHAR(40) NULL,
    amount DECIMAL(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status ENUM('PENDING','PROCESSING','SUCCEEDED','FAILED','CANCELLED','EXPIRED','REFUNDED','PARTIALLY_REFUNDED') NOT NULL DEFAULT 'PENDING',
    provider_txn_id VARCHAR(120) NULL,
    provider_bank_code VARCHAR(40) NULL,
    idempotency_key VARCHAR(80) NULL,
    paid_at TIMESTAMP(6) NULL,
    verified_at TIMESTAMP(6) NULL,
    failure_code VARCHAR(255) NULL,
    failure_message TEXT NULL,
    refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    expires_at TIMESTAMP(6) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_code (payment_code),
    UNIQUE KEY uk_payments_provider_txn (provider_txn_id),
    UNIQUE KEY uk_payments_idempotency (idempotency_key),
    KEY idx_pay_booking_status (booking_id, status),
    KEY idx_pay_status_created (status, created_at),
    KEY idx_pay_invoice (invoice_id),
    KEY idx_pay_created_by (created_by),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    -- BR-012: payment SUCCEEDED chỉ hợp lệ khi đã có mốc paid_at và verified_at từ gateway/nhân viên xác minh.
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_refund CHECK (refunded_amount >= 0 AND refunded_amount <= amount),
    CONSTRAINT chk_payments_succeeded_verified CHECK (status <> 'SUCCEEDED' OR (paid_at IS NOT NULL AND verified_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NULL,
    event_type VARCHAR(60) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(120) NULL,
    signature_valid BOOLEAN NULL,
    http_status INT NULL,
    raw_payload JSON NOT NULL,
    received_ip VARCHAR(255) NULL,
    processed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_events_provider_event (provider, provider_event_id),
    KEY idx_pe_processed (processed_at),
    KEY idx_pe_payment (payment_id),
    CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    reason ENUM('CUSTOMER_CANCEL','HOTEL_CANCEL','OVERCHARGE','NO_SHOW_ADJUST','OTHER') NOT NULL,
    status ENUM('PENDING','PROCESSING','COMPLETED','FAILED','REJECTED') NOT NULL DEFAULT 'PENDING',
    policy_applied JSON NULL,
    provider_refund_id VARCHAR(120) NULL,
    requested_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    processed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_provider_refund (provider_refund_id),
    KEY idx_ref_payment (payment_id),
    KEY idx_ref_booking (booking_id),
    KEY idx_ref_status (status),
    KEY idx_ref_requested_by (requested_by),
    KEY idx_ref_approved_by (approved_by),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE RESTRICT,
    -- amount là số tiền hoàn theo policy snapshot của booking; trigger cuối file chặn tổng hoàn vượt số đã thu.
    CONSTRAINT chk_refunds_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    room_id BIGINT NULL,
    room_type_id BIGINT NULL,
    overall_rating INT NOT NULL,
    room_rating INT NULL,
    cleanliness_rating INT NULL,
    service_rating INT NULL,
    value_rating INT NULL,
    title VARCHAR(200) NULL,
    comment TEXT NULL,
    status ENUM('PENDING','PUBLISHED','HIDDEN','REJECTED') NOT NULL DEFAULT 'PUBLISHED',
    staff_reply TEXT NULL,
    staff_reply_by BIGINT NULL,
    staff_replied_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_booking (booking_id),
    KEY idx_rev_room_type_status (room_type_id, status, created_at),
    KEY idx_reviews_room_status (room_id, status),
    KEY idx_reviews_customer (customer_id),
    KEY idx_reviews_staff_reply_by (staff_reply_by),
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_staff_reply_by FOREIGN KEY (staff_reply_by) REFERENCES staff_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT chk_reviews_overall CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_room CHECK (room_rating IS NULL OR room_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_cleanliness CHECK (cleanliness_rating IS NULL OR cleanliness_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_service CHECK (service_rating IS NULL OR service_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_value CHECK (value_rating IS NULL OR value_rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_code VARCHAR(60) NULL,
    to_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    to_user_id BIGINT NULL,
    cc VARCHAR(255) NULL,
    bcc VARCHAR(255) NULL,
    subject VARCHAR(300) NOT NULL,
    body_html TEXT NULL,
    body_text TEXT NULL,
    status ENUM('QUEUED','SENDING','SENT','FAILED','BOUNCED') NOT NULL DEFAULT 'QUEUED',
    provider VARCHAR(40) NULL,
    provider_message_id VARCHAR(150) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    scheduled_at TIMESTAMP(6) NULL,
    sent_at TIMESTAMP(6) NULL,
    related_booking_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_em_status_scheduled (status, scheduled_at),
    KEY idx_em_to_email (to_email),
    KEY idx_em_booking (related_booking_id),
    KEY idx_em_to_user (to_user_id),
    KEY idx_em_created_by (created_by),
    CONSTRAINT fk_email_to_user FOREIGN KEY (to_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_related_booking FOREIGN KEY (related_booking_id) REFERENCES bookings (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_email_attempt_count CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    ip_address VARCHAR(255) NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_al_entity (entity_type, entity_id, created_at),
    KEY idx_al_actor (actor_user_id, created_at),
    KEY idx_audit_logs_action (action),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- View này derive khoảng lưu trú chung của một booking từ các booking_rooms còn hiệu lực.
-- Bảng bookings cố ý không có check_in/check_out vì một booking có thể gồm nhiều phòng/ngày khác nhau.
CREATE OR REPLACE VIEW v_booking_stay_range AS
SELECT
    br.booking_id,
    MIN(br.check_in_date) AS check_in_date,
    MAX(br.check_out_date) AS check_out_date,
    SUM(br.nights) AS total_room_nights
FROM booking_rooms br
WHERE br.status IN ('RESERVED','OCCUPIED','COMPLETED')
GROUP BY br.booking_id;

-- Seed role hệ thống tối thiểu cho RBAC.
INSERT INTO roles (id, code, name, description, is_system) VALUES
    (1, 'CUSTOMER', 'Customer', 'Customer role', TRUE),
    (2, 'STAFF', 'Staff', 'Hotel staff role', TRUE),
    (3, 'ADMIN', 'Admin', 'System administrator role', TRUE);

-- Seed permission nền cho các module trong project plan; ADMIN nhận toàn bộ permission.
INSERT INTO permissions (id, code, resource, action, description) VALUES
    (1, 'booking:create', 'booking', 'create', 'Create booking'),
    (2, 'booking:read_own', 'booking', 'read_own', 'Read own bookings'),
    (3, 'booking:cancel_own', 'booking', 'cancel_own', 'Cancel own booking'),
    (4, 'review:create', 'review', 'create', 'Create review'),
    (5, 'room:read', 'room', 'read', 'Read room inventory'),
    (6, 'room:create', 'room', 'create', 'Create room inventory'),
    (7, 'room:update', 'room', 'update', 'Update room inventory'),
    (8, 'room:delete', 'room', 'delete', 'Soft delete room inventory'),
    (9, 'booking:read_any', 'booking', 'read_any', 'Read all bookings'),
    (10, 'booking:cancel_any', 'booking', 'cancel_any', 'Cancel any booking'),
    (11, 'booking:check_in', 'booking', 'check_in', 'Check in booking'),
    (12, 'booking:check_out', 'booking', 'check_out', 'Check out booking'),
    (13, 'booking:assign_room', 'booking', 'assign_room', 'Assign room to booking'),
    (14, 'guest:read_id', 'guest', 'read_id', 'Read guest identity document'),
    (15, 'pricing:manage', 'pricing', 'manage', 'Manage rates'),
    (16, 'policy:manage', 'policy', 'manage', 'Manage cancellation policies'),
    (17, 'payment:manage', 'payment', 'manage', 'Manage payments'),
    (18, 'refund:approve', 'refund', 'approve', 'Approve refunds'),
    (19, 'invoice:issue', 'invoice', 'issue', 'Issue invoices'),
    (20, 'invoice:void', 'invoice', 'void', 'Void invoices'),
    (21, 'review:moderate', 'review', 'moderate', 'Moderate reviews'),
    (22, 'email:send', 'email', 'send', 'Send email messages'),
    (23, 'staff:manage', 'staff', 'manage', 'Manage staff'),
    (24, 'shift:manage', 'shift', 'manage', 'Manage shifts'),
    (25, 'audit:read', 'audit', 'read', 'Read audit logs');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 3, id FROM permissions;

INSERT INTO role_permissions (role_id, permission_id) VALUES
    (2, 5), (2, 6), (2, 7), (2, 9), (2, 10), (2, 11), (2, 12), (2, 13),
    (2, 14), (2, 17), (2, 19), (2, 20), (2, 22),
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5);

-- Seed nguồn booking. commission_percent là cấu hình hiện tại; khi tạo booking sẽ snapshot sang bookings.
INSERT INTO booking_sources (id, code, name, is_external, requires_account, commission_percent, is_active) VALUES
    (1, 'WEBSITE', 'Website', FALSE, TRUE, 0.00, TRUE),
    (2, 'WALK_IN', 'Walk-in', FALSE, FALSE, 0.00, TRUE),
    (3, 'PHONE', 'Phone', FALSE, FALSE, 0.00, TRUE),
    (4, 'BOOKING_COM', 'Booking.com', TRUE, FALSE, 15.00, TRUE),
    (5, 'AGODA', 'Agoda', TRUE, FALSE, 18.00, TRUE),
    (6, 'STAFF_MANUAL', 'Staff manual', FALSE, FALSE, 0.00, TRUE);

-- Seed chính sách hủy mẫu. Rules bên dưới luôn có mốc 0 giờ để service luôn tìm được rule áp dụng.
INSERT INTO cancellation_policies (id, code, name, description, no_show_charge_percent, is_default, is_active) VALUES
    (1, 'FLEXIBLE', 'Flexible', 'Flexible cancellation policy', 100.00, TRUE, TRUE),
    (2, 'MODERATE', 'Moderate', 'Standard cancellation policy', 100.00, FALSE, TRUE),
    (3, 'NON_REFUND', 'Non-refundable', 'No refund after booking confirmation', 100.00, FALSE, TRUE);

INSERT INTO cancellation_policy_rules (id, policy_id, min_hours_before, refund_percent) VALUES
    (1, 1, 72, 100.00),
    (2, 1, 30, 50.00),
    (3, 1, 0, 0.00),
    (4, 2, 168, 100.00),
    (5, 2, 72, 50.00),
    (6, 2, 0, 0.00),
    (7, 3, 0, 0.00);

-- Seed room type tối thiểu để dev có dữ liệu inventory ban đầu.
INSERT INTO room_types (id, code, name, slug, description, bed_count, max_occupancy, max_adults, max_children, base_price, currency, has_air_conditioner, is_active, sort_order) VALUES
    (1, 'STD', 'Standard', 'standard', 'Standard room', 1, 2, 2, 0, 800000.00, 'VND', TRUE, TRUE, 10),
    (2, 'DLX', 'Deluxe', 'deluxe', 'Deluxe room', 2, 3, 3, 0, 1500000.00, 'VND', TRUE, TRUE, 20),
    (3, 'SUITE', 'Suite', 'suite', 'Suite room', 2, 4, 4, 0, 3000000.00, 'VND', TRUE, TRUE, 30);

INSERT INTO room_type_beds (room_type_id, bed_type, quantity) VALUES
    (1, 'QUEEN', 1),
    (2, 'QUEEN', 2),
    (3, 'KING', 1),
    (3, 'SOFA_BED', 1);

-- Seed tiện nghi dùng cho filter phòng/loại phòng.
INSERT INTO amenities (id, code, name, icon, category, is_filterable, sort_order) VALUES
    (1, 'WIFI', 'Wi-Fi', 'wifi', 'TECH', TRUE, 10),
    (2, 'TV', 'Television', 'tv', 'TECH', TRUE, 20),
    (3, 'MINIBAR', 'Minibar', 'refrigerator', 'ROOM', TRUE, 30),
    (4, 'AC', 'Air conditioner', 'air-vent', 'ROOM', TRUE, 40),
    (5, 'BALCONY', 'Balcony', 'door-open', 'ROOM', TRUE, 50),
    (6, 'POOL', 'Pool access', 'waves', 'SERVICE', TRUE, 60),
    (7, 'SPA', 'Spa access', 'sparkles', 'SERVICE', TRUE, 70);

INSERT INTO room_type_amenities (room_type_id, amenity_id) VALUES
    (1, 1), (1, 2), (1, 4),
    (2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
    (3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7);

-- Seed ca trực chuẩn. Ca NIGHT qua nửa đêm nên crosses_midnight = TRUE.
INSERT INTO shifts (id, code, name, start_time, end_time, crosses_midnight, is_active) VALUES
    (1, 'MORNING', 'Morning shift', '06:00:00', '14:00:00', FALSE, TRUE),
    (2, 'AFTERNOON', 'Afternoon shift', '14:00:00', '22:00:00', FALSE, TRUE),
    (3, 'NIGHT', 'Night shift', '22:00:00', '06:00:00', TRUE, TRUE);

-- Seed dịch vụ mẫu để folio/invoice có dữ liệu phát sinh ban đầu.
INSERT INTO service_items (id, code, name, category, unit_price, tax_percent, is_active) VALUES
    (1, 'MINIBAR_WATER', 'Minibar water', 'MINIBAR', 30000.00, 0.00, TRUE),
    (2, 'LAUNDRY_BASIC', 'Laundry basic', 'LAUNDRY', 100000.00, 0.00, TRUE),
    (3, 'LATE_CHECKOUT', 'Late checkout', 'PENALTY', 300000.00, 0.00, TRUE);

-- Đổi delimiter để MySQL hiểu toàn bộ thân trigger là một statement.
DELIMITER //

-- BR-002/BR-004 khi thêm booking_room:
-- 1) ngày trả phải sau ngày nhận;
-- 2) phòng phải đang active;
-- 3) không được overlap booking_rooms RESERVED/OCCUPIED khác theo logic [in, out);
-- 4) không được overlap room_status_blocks.
CREATE TRIGGER trg_booking_rooms_before_insert
BEFORE INSERT ON booking_rooms
FOR EACH ROW
BEGIN
    IF NEW.check_out_date <= NEW.check_in_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_rooms requires check_out_date > check_in_date';
    END IF;

    IF NEW.status IN ('RESERVED', 'OCCUPIED') THEN
        IF EXISTS (
            SELECT 1
            FROM rooms r
            WHERE r.id = NEW.room_id
              AND (r.operational_status <> 'ACTIVE' OR r.is_active = FALSE OR r.deleted_at IS NOT NULL)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room is not operationally active';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM booking_rooms br
            WHERE br.room_id = NEW.room_id
              AND br.status IN ('RESERVED', 'OCCUPIED')
              AND NEW.check_in_date < br.check_out_date
              AND NEW.check_out_date > br.check_in_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps an active booking';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM room_status_blocks rb
            WHERE rb.room_id = NEW.room_id
              AND NEW.check_in_date < rb.end_date
              AND NEW.check_out_date > rb.start_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps a room status block';
        END IF;
    END IF;
END//

-- BR-002/BR-004 khi sửa booking_room: giống insert nhưng bỏ qua chính dòng đang update.
CREATE TRIGGER trg_booking_rooms_before_update
BEFORE UPDATE ON booking_rooms
FOR EACH ROW
BEGIN
    IF NEW.check_out_date <= NEW.check_in_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_rooms requires check_out_date > check_in_date';
    END IF;

    IF NEW.status IN ('RESERVED', 'OCCUPIED') THEN
        IF EXISTS (
            SELECT 1
            FROM rooms r
            WHERE r.id = NEW.room_id
              AND (r.operational_status <> 'ACTIVE' OR r.is_active = FALSE OR r.deleted_at IS NOT NULL)
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room is not operationally active';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM booking_rooms br
            WHERE br.room_id = NEW.room_id
              AND br.id <> OLD.id
              AND br.status IN ('RESERVED', 'OCCUPIED')
              AND NEW.check_in_date < br.check_out_date
              AND NEW.check_out_date > br.check_in_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps an active booking';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM room_status_blocks rb
            WHERE rb.room_id = NEW.room_id
              AND NEW.check_in_date < rb.end_date
              AND NEW.check_out_date > rb.start_date
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking room overlaps a room status block';
        END IF;
    END IF;
END//

-- BR-003/BR-004 khi thêm block bảo trì/ngưng bán:
-- block không được sai ngày, không trùng block cũ, và không chồng lên booking đang giữ/đang ở.
CREATE TRIGGER trg_room_status_blocks_before_insert
BEFORE INSERT ON room_status_blocks
FOR EACH ROW
BEGIN
    IF NEW.end_date <= NEW.start_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room_status_blocks requires end_date > start_date';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM room_status_blocks rb
        WHERE rb.room_id = NEW.room_id
          AND NEW.start_date < rb.end_date
          AND NEW.end_date > rb.start_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an existing block';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.room_id = NEW.room_id
          AND br.status IN ('RESERVED', 'OCCUPIED')
          AND NEW.start_date < br.check_out_date
          AND NEW.end_date > br.check_in_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an active booking';
    END IF;
END//

-- BR-003/BR-004 khi sửa block: kiểm tra overlap lại nhưng bỏ qua chính dòng đang update.
CREATE TRIGGER trg_room_status_blocks_before_update
BEFORE UPDATE ON room_status_blocks
FOR EACH ROW
BEGIN
    IF NEW.end_date <= NEW.start_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room_status_blocks requires end_date > start_date';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM room_status_blocks rb
        WHERE rb.room_id = NEW.room_id
          AND rb.id <> OLD.id
          AND NEW.start_date < rb.end_date
          AND NEW.end_date > rb.start_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an existing block';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.room_id = NEW.room_id
          AND br.status IN ('RESERVED', 'OCCUPIED')
          AND NEW.start_date < br.check_out_date
          AND NEW.end_date > br.check_in_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'room status block overlaps an active booking';
    END IF;
END//

-- Bảo đảm mỗi booking_room_night chỉ nằm trong khoảng lưu trú của booking_room cha.
-- Giá từng đêm là snapshot, không được tạo dòng ngoài [check_in_date, check_out_date).
CREATE TRIGGER trg_booking_room_nights_before_insert
BEFORE INSERT ON booking_room_nights
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.id = NEW.booking_room_id
          AND NEW.stay_date >= br.check_in_date
          AND NEW.stay_date < br.check_out_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stay_date must be within booking room date range';
    END IF;
END//

-- Kiểm tra lại stay_date khi sửa booking_room_night để tránh lệch khỏi khoảng lưu trú cha.
CREATE TRIGGER trg_booking_room_nights_before_update
BEFORE UPDATE ON booking_room_nights
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM booking_rooms br
        WHERE br.id = NEW.booking_room_id
          AND NEW.stay_date >= br.check_in_date
          AND NEW.stay_date < br.check_out_date
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stay_date must be within booking room date range';
    END IF;
END//

-- BR-015 khi gán ca: một staff không được có hai ca hiệu lực giao nhau về thời gian.
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
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
          AND NEW.shift_start_at < sa.shift_end_at
          AND NEW.shift_end_at > sa.shift_start_at
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment overlaps an existing effective shift';
    END IF;
END//

-- BR-015 khi sửa ca: kiểm tra overlap lại nhưng bỏ qua chính assignment đang update.
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
          AND sa.id <> OLD.id
          AND sa.status IN ('SCHEDULED', 'COMPLETED')
          AND NEW.shift_start_at < sa.shift_end_at
          AND NEW.shift_end_at > sa.shift_start_at
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'shift assignment overlaps an existing effective shift';
    END IF;
END//

-- booking_status_history là timeline nghiệp vụ append-only, không được sửa để giữ audit trail.
CREATE TRIGGER trg_booking_status_history_before_update
BEFORE UPDATE ON booking_status_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_status_history is append-only';
END//

-- booking_status_history là timeline nghiệp vụ append-only, không được xóa để tránh mất lịch sử chuyển trạng thái.
CREATE TRIGGER trg_booking_status_history_before_delete
BEFORE DELETE ON booking_status_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_status_history is append-only';
END//

-- BR-006/BR-007: chỉ khách của booking đã CHECKED_OUT mới được tạo review.
-- UNIQUE booking_id ở bảng reviews bảo đảm mỗi booking tối đa một review.
CREATE TRIGGER trg_reviews_before_insert
BEFORE INSERT ON reviews
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM bookings b
        WHERE b.id = NEW.booking_id
          AND b.customer_id = NEW.customer_id
          AND b.status = 'CHECKED_OUT'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'review requires a checked-out booking owned by the customer';
    END IF;
END//

-- BR-013: sau khi hóa đơn ISSUED, các trường chứng từ bất biến.
-- Chỉ cho giữ ISSUED hoặc chuyển VOID; payment/pdf/void fields có thể cập nhật theo workflow.
CREATE TRIGGER trg_invoices_before_update
BEFORE UPDATE ON invoices
FOR EACH ROW
BEGIN
    IF OLD.status = 'ISSUED' AND NEW.status NOT IN ('ISSUED', 'VOID') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'issued invoice can only remain ISSUED or become VOID';
    END IF;

    IF OLD.status = 'ISSUED' AND (
        NOT (OLD.booking_id <=> NEW.booking_id)
        OR NOT (OLD.invoice_number <=> NEW.invoice_number)
        OR NOT (OLD.issued_at <=> NEW.issued_at)
        OR NOT (OLD.issued_by <=> NEW.issued_by)
        OR NOT (OLD.buyer_name <=> NEW.buyer_name)
        OR NOT (OLD.buyer_address <=> NEW.buyer_address)
        OR NOT (OLD.buyer_tax_code <=> NEW.buyer_tax_code)
        OR NOT (OLD.buyer_email <=> NEW.buyer_email)
        OR NOT (OLD.subtotal <=> NEW.subtotal)
        OR NOT (OLD.discount_total <=> NEW.discount_total)
        OR NOT (OLD.tax_total <=> NEW.tax_total)
        OR NOT (OLD.total_amount <=> NEW.total_amount)
        OR NOT (OLD.currency <=> NEW.currency)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'issued invoice document fields are immutable';
    END IF;
END//

-- BR-013: không cho sửa line item của hóa đơn đã ISSUED.
CREATE TRIGGER trg_invoice_items_before_update
BEFORE UPDATE ON invoice_items
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM invoices i WHERE i.id = OLD.invoice_id AND i.status = 'ISSUED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invoice items of an issued invoice are immutable';
    END IF;
END//

-- BR-013: không cho xóa line item của hóa đơn đã ISSUED.
CREATE TRIGGER trg_invoice_items_before_delete
BEFORE DELETE ON invoice_items
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM invoices i WHERE i.id = OLD.invoice_id AND i.status = 'ISSUED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invoice items of an issued invoice are immutable';
    END IF;
END//

-- Bảo vệ ledger payment: tổng refund COMPLETED của một payment không được vượt payment.amount.
CREATE TRIGGER trg_refunds_before_insert
BEFORE INSERT ON refunds
FOR EACH ROW
BEGIN
    IF NEW.status = 'COMPLETED' AND (
        SELECT COALESCE(SUM(r.amount), 0)
        FROM refunds r
        WHERE r.payment_id = NEW.payment_id
          AND r.status = 'COMPLETED'
    ) + NEW.amount > (
        SELECT p.amount
        FROM payments p
        WHERE p.id = NEW.payment_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'completed refunds cannot exceed payment amount';
    END IF;
END//

-- Kiểm tra lại giới hạn refund khi cập nhật amount/status của refund.
CREATE TRIGGER trg_refunds_before_update
BEFORE UPDATE ON refunds
FOR EACH ROW
BEGIN
    IF NEW.status = 'COMPLETED' AND (
        SELECT COALESCE(SUM(r.amount), 0)
        FROM refunds r
        WHERE r.payment_id = NEW.payment_id
          AND r.status = 'COMPLETED'
          AND r.id <> OLD.id
    ) + NEW.amount > (
        SELECT p.amount
        FROM payments p
        WHERE p.id = NEW.payment_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'completed refunds cannot exceed payment amount';
    END IF;
END//

DELIMITER ;

# Hotel Management — Kế hoạch Triển khai 8 Ngày

**Stack:** MySQL + Docker + Spring Boot (Java 21) + MinIO + Next.js
**Nguồn:** Schema 39 bảng, 27 enum, 70 FK (`DATABASE_DESIGN.md` + `hotel_management.dbml`)
**Ghi chú:** Từ ngày 18/08/2026. Ưu tiên BE trước FE. Task BE ≤ 8h/task. Task FE ghép chung ngày với FE setup.

---

## Ngày 1 — Infrastructure & Foundation

**Mục tiêu:** Môi trường dev chạy được, schema MySQL sinh ra, app boot không lỗi.

### Backend

#### BE-1.1 | Setup Docker Compose | Priority: Immediate | 18/08 | Est: 3h

- Tạo `docker-compose.yml` với:
  - MySQL 8.0 (port 3306)
  - MinIO (port 9000/9001)
  - Redis (port 6379, cho session/cache)
- Config volume cho MySQL + MinIO data
- Config network `hotel-network`
- Tạo env file cho password/port

#### BE-1.2 | Spring Boot Project Init | Priority: Immediate | 18/08 | Est: 2h

- Tạo project Spring Boot 3.x (Java 21)
- Dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-security`
  - `spring-boot-starter-mail`
  - `spring-boot-starter-data-redis`
  - `mybatis-spring-boot-starter`
- Cấu hình `application.yml` đọc env Docker

#### BE-1.3 | JPA Entities từ Schema | Priority: Urgent | 18/08 | Est: 8h

- Sinh JPA entity classes từ 39 bảng:
  - `users`, `roles`, `permissions`
  - `customer_profiles`, `staff_profiles`
  - `shifts`, `shift_assignments`
  - `room_types`, `rooms`, `room_images`
  - `bookings`, `booking_rooms`, `booking_room_nights`, `booking_guests`, `booking_status_history`
  - `folio_charges`, `invoices`, `invoice_items`
  - `payments`, `refunds`, `payment_events`
  - `reviews`, `email_messages`, `audit_logs`
  - `amenities`, `room_amenities`, `room_type_beds`, `room_type_images`, `room_type_amenities`
  - `cancellation_policies`, `cancellation_policy_rules`
  - `booking_sources`, `rate_overrides`, `room_status_blocks`
  - `auth_tokens`, `auth_refresh_tokens`, `user_social_accounts`
- Tạo Enum classes cho 27 enum
- Không có business logic — chỉ mapping
- Chuyển PostgreSQL types sang MySQL:
  - `daterange`/`tstzrange` → stored/generated columns
  - `JSONB` → `JSON`
  - `UUID` → `CHAR(36)`

#### BE-1.4 | Flyway Migration Scripts | Priority: Urgent | 18/08 | Est: 4h

- Viết Flyway migration V1 (baseline):
  - Tạo toàn bộ bảng, index, FK
  - Enum values
  - Seed data: roles, booking_sources, cancellation_policies + rules, room_types, shifts
- Chạy script đảm bảo schema hoàn chỉnh trên MySQL
- Viết triggers:
  - `trg_booking_rooms_before_insert/update` (BR-002/BR-004)
  - `trg_room_status_blocks_before_insert/update` (BR-003/BR-004)
  - `trg_booking_room_nights_before_insert/update`
  - `trg_shift_assignments_before_insert/update` (BR-015)
  - `trg_booking_status_history_before_update/delete`
  - `trg_reviews_before_insert` (BR-006/BR-007)
  - `trg_invoices_before_update` (BR-013)
  - `trg_invoice_items_before_update/delete` (BR-013)
  - `trg_refunds_before_insert/update`

### Frontend

#### FE-1.1 | Next.js Project Init | Priority: Immediate | 18/08 | Est: 2h

- Tạo Next.js 14+ (App Router) project
- Setup ESLint, Prettier, TypeScript strict mode
- Cấu hình `next.config.js` cho image domains (MinIO)
- Tạo folder structure:
  - `/app/(auth)`
  - `/app/(dashboard)`
  - `/components`
  - `/lib`
  - `/hooks`
  - `/types`
  - `/store`

#### FE-1.2 | Tailwind + UI Components | Priority: Normal | 18/08 | Est: 3h

- Setup Tailwind CSS với design tokens (colors, typography)
- Cài thư viện:
  - `lucide-react` (icons)
  - `clsx`, `tailwind-merge`
  - `react-hook-form`, `@hookform/resolvers`, `zod`
- Tạo base components:
  - `Button`, `Input`, `Select`, `Badge`
  - `Card`, `Modal`, `Table`, `Dropdown`

**Checkpoint cuối ngày 1:**

- `curl localhost:8080/actuator/health` → UP
- `curl localhost:3000` → Next.js welcome
- Flyway migrate thành công

---

## Ngày 2 — Authentication & User Management

**Mục tiêu:** Đăng nhập/đăng ký chạy, RBAC enforce ở API.

### Backend

#### BE-2.1 | Auth Service — JWT + OAuth | Priority: Urgent | 19/08 | Est: 6h

- Implement JWT authentication:
  - `JwtService`: generate, validate, refresh token
  - `AuthController`: login, register, logout, refresh
- Hỗ trợ OAuth2/social login stub (Google)
- Token stored in Redis with TTL
- BCrypt password hashing (cost 12)
- Failed login lock after 5 attempts (update `users.locked_until`)

#### BE-2.2 | User CRUD + Email Verification | Priority: Urgent | 19/08 | Est: 5h

- `UserService` + `UserController`
- Register: send email verification token → `auth_tokens`
- Verify email
- Request password reset
- Reset password
- `AuthTokenService`: handle token lifecycle (create, validate one-time use, expire)
- Implement email sending stub:
  - Dev: log to console
  - Prod: SES/SendGrid

#### BE-2.3 | RBAC — Roles & Permissions | Priority: Urgent | 19/08 | Est: 5h

- `RoleService`, `PermissionService`, `RolePermissionService`
- `@PreAuthorize` annotations trên controller methods
- 15+ permissions:
  - `booking:create`, `booking:read_own`, `booking:read_any`, `booking:cancel_own`, `booking:cancel_any`
  - `booking:check_in`, `booking:check_out`, `booking:assign_room`
  - `room:read`, `room:create`, `room:update`, `room:delete`
  - `guest:read_id`
  - `pricing:manage`, `policy:manage`
  - `payment:manage`, `refund:approve`
  - `invoice:issue`, `invoice:void`
  - `review:moderate`, `email:send`
  - `staff:manage`, `shift:manage`, `audit:read`
- Seed 3 roles (CUSTOMER/STAFF/ADMIN) + all permissions vào V1 migration; mỗi User chỉ có một role hiện tại và đổi role là thay thế role cũ
- Interceptor kiểm tra RBAC trước khi gọi service

#### BE-2.4 | CustomerProfile & StaffProfile CRUD | Priority: Normal | 19/08 | Est: 4h

- `CustomerProfileService` + `StaffProfileService`
- Endpoints:
  - Tạo profile sau register
  - Cập nhật thông tin cá nhân
  - Deactivate (soft delete)
- Staff-specific:
  - Admin tạo User role `STAFF` + staff_profile độc lập, không dùng Customer và không tạo CustomerProfile
  - Invitation email: User ở `PENDING_VERIFICATION`, Staff xác thực email và đặt mật khẩu mới qua link một lần
  - Edit và Admin reset mật khẩu trực tiếp; reset thu hồi refresh token hiện tại
  - Status: `ACTIVE`, `ON_LEAVE`, `TERMINATED`; chỉ Staff đã xác thực, User `ACTIVE` và employment `ACTIVE` được phân ca
  - Terminate: set `terminated_at`, lưu email lịch sử, chuyển User sang `DEACTIVATED`, không khôi phục; tuyển lại tạo User/StaffProfile mới và cho phép tái sử dụng email bằng email lưu trữ hậu tố tăng dần
- Additional fields:
  - `customer_profiles.loyalty_points`
  - `staff_profiles.base_salary` (chỉ ADMIN đọc)

#### BE-2.5 | Shifts & Shift Assignments CRUD | Priority: Normal | 19/08 | Est: 4h

- `ShiftService` + `ShiftAssignmentService`
- Endpoints:
  - Tạo/sửa/xóa ca (`shifts`)
  - Phân công Staff vào ca theo ngày (`shift_assignments`)
- Logic tính `shift_period` = `work_date + start_time` (xử lý `crosses_midnight`)
- BR-015: trigger kiểm tra Staff không overlap ca

### Frontend

#### FE-2.1 | Auth Pages — Login/Register | Priority: Urgent | 19/08 | Est: 4h

- Trang đăng nhập + đăng ký (Next.js App Router, Server Actions)
- Form validation với Zod
- Hiển thị lỗi:
  - "Tài khoản bị khóa"
  - "Email chưa xác thực"
- Toast notifications
- Responsive, mobile-first

#### FE-2.2 | Auth Pages — Email Verification & Password Reset | Priority: Normal | 19/08 | Est: 3h

- Trang xác thực email:
  - Click link từ mail → token validated
  - Redirect login
- Trang quên mật khẩu
- Trang đặt lại mật khẩu
- Tích hợp `next/link` cho magic links

#### FE-2.3 | User Profile Page | Priority: Normal | 19/08 | Est: 3h

- Trang hồ sơ cá nhân:
  - Xem/sửa `full_name`, `phone`, `date_of_birth`, `nationality`, `address`
- Customer: thêm loyalty points display
- Staff: hiển thị `position`, `department`
- Avatar upload (→ MinIO)

**Checkpoint cuối ngày 2:**

- Staff đăng nhập → redirect `/dashboard`
- Customer đăng nhập → redirect `/my-bookings`
- Không có quyền → 403

---

## Ngày 3 — Room & Inventory Management

**Mục tiêu:** CRUD đầy đủ cho loại phòng, phòng, tiện nghi, ảnh.

### Backend

#### BE-3.1 | RoomType CRUD + Beds + Amenities | Priority: Urgent | 20/08 | Est: 5h

- `RoomTypeService` + `RoomTypeController`
- Tạo/sửa/xóa (soft) loại phòng
- Quản lý `room_type_beds` (thêm/bớt giường)
- Quản lý `room_type_amenities` (gán tiện nghi)
- Seed amenities mẫu:
  - WIFI, TV, MINIBAR, AC, BALCONY, POOL, SPA
- `SlugService` auto-generate slug từ name

#### BE-3.2 | Room CRUD + Housekeeping | Priority: Urgent | 20/08 | Est: 5h

- `RoomService` + `RoomController`
- Tạo/sửa/soft-delete phòng
- Gán `room_type_id`, `view_type`, `floor`, `price_override`
- Cập nhật `housekeeping_status`:
  - CLEAN → DIRTY (sau checkout)
  - DIRTY → CLEANING → CLEAN
- `RoomImageService`: upload/sort ảnh → MinIO bucket `room-images`
- Endpoints:
  - "Danh sách phòng theo loại"
  - "Lọc theo view, tầng, tiện nghi"

#### BE-3.3 | Room Status Blocks (BR-003/BR-004) | Priority: High | 20/08 | Est: 4h

- `RoomStatusBlockService`
- Tạo/kéo dài/hủy khoảng bảo trì
- BR-004 trigger: chặn booking trên phòng đang block
- API:
  - "Phòng đang block trong khoảng ngày"
  - "Tạo block mới"
- Check `operational_status`:
  - ACTIVE/MAINTENANCE/OUT_OF_SERVICE/RENOVATION

#### BE-3.4 | Amenity CRUD + Filtering | Priority: Normal | 20/08 | Est: 2h

- `AmenityService`
- Tạo/sửa/xóa tiện nghi
- Phân loại: ROOM/BATHROOM/TECH/SERVICE
- Đánh dấu `is_filterable`
- Endpoint lấy danh sách filter options

### Frontend

#### FE-3.1 | Admin — Room Types Management | Priority: Urgent | 20/08 | Est: 5h

- Trang quản lý loại phòng:
  - Bảng danh sách (phân trang, tìm kiếm)
  - Modal tạo/sửa:
    - Form với beds config
    - Amenities multi-select
    - Giá, mô tả
  - Upload ảnh loại phòng (MinIO)
  - Soft delete với confirm dialog

#### FE-3.2 | Admin — Rooms Management + Floor Map | Priority: Urgent | 20/08 | Est: 5h

- Trang quản lý phòng:
  - Bảng + filters (loại phòng, tầng, view, trạng thái HK)
- Component "sơ đồ tầng":
  - Grid phòng
  - Màu theo `housekeeping_status`:
    - Xanh = CLEAN
    - Đỏ = DIRTY
    - Cam = CLEANING
  - Click phòng → side panel chi tiết
- Modal tạo/sửa phòng

#### FE-3.3 | Admin — Room Maintenance Scheduling | Priority: Normal | 20/08 | Est: 4h

- Trang đặt lịch bảo trì:
  - Calendar view (ngày × phòng)
  - Tạo block với ngày bắt đầu/kết thúc, loại block, ghi chú
  - Validate: không cho tạo block trùng ngày trên cùng phòng

**Checkpoint cuối ngày 3:**

- Admin tạo loại phòng Deluxe → gán 10 phòng → upload ảnh → đặt bảo trì phòng 301 ngày 25-27/08

---

## Ngày 4 — Pricing, Policies & Rate Engine

**Mục tiêu:** Hệ thống giá hoàn chỉnh, policy hủy nhiều bậc, API tính giá cho booking.

### Backend

#### BE-4.1 | Rate Engine — Giá theo ngày | Priority: Urgent | 21/08 | Est: 5h

- `RateEngineService`: tính giá phòng cho một khoảng ngày
- Logic pricing priority:
  1. `rooms.price_override` (nếu có)
  2. `rate_overrides` (priority cao nhất, đúng ngày, đúng weekdays)
  3. `room_types.base_price` (default)
- Xử lý `weekdays` array (T7, CN)
- Trả về danh sách `{date, price}` per night
- Đây là pricing snapshot tầng 1

#### BE-4.2 | RateOverride CRUD | Priority: Normal | 21/08 | Est: 3h

- `RateOverrideService` + `RateOverrideController`
- Fields: `room_type_id`/`room_id`, date range, price, weekdays, priority
- Validate: đúng 1 trong 2 room identifiers khác null
- List overrides đang active

#### BE-4.3 | CancellationPolicy + Rules | Priority: Urgent | 21/08 | Est: 4h

- `CancellationPolicyService`: CRUD policy + rules
- Validate: mỗi policy có rule `min_hours_before=0`
- Seed 3 policies:
  - FLEXIBLE: 72h → 100%, 30h → 50%, 0h → 0%
  - MODERATE: 168h → 100%, 72h → 50%, 0h → 0%
  - NON_REFUND: 0h → 0%
- Snapshot policy + rules vào JSON → gửi lên booking
- Admin gắn nhiều cancellation policy online ở từng RoomType; policy có `% tăng giá` khi bán online.
- RoomType chỉ còn các option thanh toán online gắn với cancellation policy; option `Thanh toán tại khách sạn` đã bỏ khỏi website và không còn cấu hình trong admin.
- Customer chọn RoomType + payment/cancellation option; backend tự assign phòng vật lý còn trống, không cho customer chọn phòng cụ thể.

#### BE-4.4 | Booking Price Calculator API | Priority: Urgent | 21/08 | Est: 4h

- `BookingCalculatorService`
- Nhận `{room_id, check_in, check_out, adults, children}`
- Gọi RateEngine
- Tính rooms_total
- Áp dụng `room_tax_percent_snapshot`
- Trả về tổng tiền dự kiến (không lưu DB)
- API: `POST /api/bookings/calculate-price`

### Frontend

#### FE-4.1 | Admin — Pricing Management | Priority: High | 21/08 | Est: 4h

- Trang quản lý giá:
  - Bảng rate_overrides (date range, price, priority)
  - Form tạo override:
    - Chọn loại phòng
    - Ngày, giá
    - Weekdays checkboxes (T2-T7, CN)
  - Calendar view hiển thị giá theo ngày

#### FE-4.2 | Admin — Cancellation Policies | Priority: High | 21/08 | Est: 4h

- Trang quản lý chính sách hủy:
  - Danh sách policy
  - Expand → xem các bậc hoàn tiền
    - Table: "trước X giờ → hoàn Y%"
- Form tạo/sửa policy:
  - Code, name, no_show_charge_percent
  - Thêm/bớt rule (dynamic rows)
  - Validate: luôn có bậc 0 giờ

#### FE-4.3 | Customer — Room Listing & Price Display | Priority: High | 21/08 | Est: 5h

- Trang danh sách loại phòng (Customer):
  - Grid card hiển thị:
    - Ảnh, tên
    - Giá/đêm (từ `base_price`)
    - Tiện nghi icons
  - Nút "Xem chi tiết"
- Chi tiết phòng:
  - Gallery ảnh
  - Mô tả, bed config, amenities
  - Giá theo ngày (từ RateEngine)
- Date picker chọn check-in/check-out:
  - Gọi `/calculate-price`
  - Hiển thị tổng tiền dự kiến

**Checkpoint cuối ngày 4:**

- Chọn Deluxe, 18-21/08 → hiển thị đúng giá cuối tuần 21/08 cao hơn ngày thường
- Tạo FLEXIBLE policy với 3 bậc hoàn tiền

---

## Ngày 5 — Booking Engine

**Mục tiêu:** Tạo booking, check availability, gán phòng, quản lý trạng thái booking.

### Backend

#### BE-5.1 | Availability Engine | Priority: Urgent | 22/08 | Est: 5h

- `AvailabilityService`: query kiểm tra phòng khả dụng cho `[check_in, check_out)`
- Logic kiểm tra:
  - `rooms.operational_status='ACTIVE'`
  - Không overlap `booking_rooms` (status IN RESERVED/OCCUPIED)
  - Không overlap `room_status_blocks`
- Trả về `{room_type_id: [available_room_ids]}` để FE hiển thị phòng cụ thể
- BR-002: trigger MySQL thay PostgreSQL EXCLUDE

#### BE-5.2 | Booking Creation — Snapshot Pricing | Priority: Urgent | 22/08 | Est: 6h

- `BookingService.createBooking()`: chỉ được gọi ở bước customer đã chọn payment method; tạo booking ở PENDING
- Trước khi insert: lock các room khả dụng, kiểm tra overlap/trạng thái lần cuối trong cùng transaction;
  conflict thì không tạo booking/payment
- Tính giá từng đêm → ghi `booking_room_nights` rows
- Snapshot fields:
  - `room_tax_percent_snapshot`
  - `booking_rooms.cancellation_policy_snapshot` (JSON full policy + rules theo option RoomType)
  - `booking_rooms.payment_option`, `booking_rooms.price_adjustment_percent_snapshot`
  - `source_commission_percent_snapshot`
- Tạo `booking_code` (BK-2026-XXXXXX)
- Setup `hold_expires_at` (**15 phút** nếu không thanh toán ngay)
- Ghi `booking_status_history` dòng đầu
- **Lưu ý:** Giá từng đêm là snapshot bất biến

#### BE-5.3 | Booking State Machine + Triggers | Priority: Urgent | 22/08 | Est: 5h

- `BookingStateMachineService`
- Implement đúng các transition theo DATABASE_DESIGN mục 8.1:
  - `PENDING → CONFIRMED/CANCELLED/EXPIRED`
  - `CONFIRMED → CHECKED_IN/CANCELLED/NO_SHOW`
  - `CHECKED_IN → CHECKED_OUT`
- BR-010: chỉ CONFIRMED → CHECKED_IN
- BR-011: chỉ CHECKED_IN → CHECKED_OUT
- BR-005: trigger kiểm tra customer_id + status
- Mỗi transition ghi `booking_status_history`
- **Trigger đồng bộ `booking_room_status` (8.2):**
  - `CHECKED_IN → booking_rooms.status = OCCUPIED`
  - `CHECKED_OUT → booking_rooms.status = COMPLETED`
  - `CANCELLED/EXPIRED/NO_SHOW → booking_rooms.status = RELEASED`
  - `MOVED_OUT` là thao tác riêng của luồng đổi phòng (6.3)

#### BE-5.4 | Booking Room Assignment + Room Change | Priority: High | 22/08 | Est: 4h

- `BookingRoomService`: gán phòng cụ thể khi check-in
- BR-009: ghi `assigned_at`, `assigned_by`
- **Room change giữa kỳ (theo DATABASE_DESIGN mục 6.3):**
  1. Tạo dòng `booking_rooms` mới với `moved_from_booking_room_id` trỏ về dòng cũ
  2. Dòng cũ: thu hẹp `check_out_date` về ngày chuyển + set `status = MOVED_OUT`
  3. **Transfer `booking_room_nights` từ ngày chuyển → dòng mới** (cập nhật `booking_room_id`, KHÔNG tạo mới giá)
  4. `bookings.rooms_total` không đổi
  5. **Không re-price** — giá đã cam kết giữ nguyên

#### BE-5.5 | BookingGuest + ID Document Storage | Priority: High | 22/08 | Est: 4h

- `BookingGuestService`: thêm khách lưu trú vào booking
- ID document:
  - Mã hóa AES-256-GCM trước khi lưu (key từ config/KMS)
  - `id_document_lookup_hash` = HMAC-SHA256 để search
- BR-014: RBAC `guest:read_id` để xem CCCD (cần log vào `audit_logs`)

### Frontend

#### FE-5.1 | Customer — Booking Flow | Priority: Urgent | 22/08 | Est: 6h

- Multi-step booking wizard:
  - B1: Chọn phòng & ngày (Availability API → hiển thị phòng khả dụng)
  - B2: Nhập thông tin liên hệ
  - B3: Review draft và chính sách hủy; chưa tạo booking database
  - B4: Chọn payment method, bấm tiếp tục → tạo PENDING, tạo payment đủ tổng booking → redirect gateway
  - Website chỉ dùng payment online; backend từ chối `PAY_AT_HOTEL`
- Progress indicator

#### FE-5.2 | Customer — My Bookings | Priority: Urgent | 22/08 | Est: 4h

- Trang "Đơn đặt của tôi":
  - Danh sách booking của customer đăng nhập
  - Filter theo status
- Chi tiết booking:
  - Thông tin phòng, ngày, tổng tiền, trạng thái
  - Timeline (từ `booking_status_history`)
- Actions:
  - `PENDING` còn hạn: Thanh toán, Xem chi tiết, Xóa booking (hard delete nếu chưa có payment thành công)
  - `CONFIRMED`: Hủy booking theo policy; từ `CHECKED_IN` trở đi không có xóa/hủy thường
  - `EXPIRED`: giữ trong danh sách, không còn nút thanh toán
  - Nút "Xem hóa đơn"

#### FE-5.3 | Staff — Booking Management | Priority: Urgent | 22/08 | Est: 5h

- Trang quản lý booking (Staff):
  - Bảng tất cả booking
  - Filter: ngày đến, ngày đi, nguồn, trạng thái
  - Tìm nhanh theo booking_code, tên khách, SĐT
  - Click row → drawer chi tiết
- Actions:
  - "Xác nhận booking"
  - "Check-in"
  - "Check-out"
  - "Hủy"
  - "Gán phòng"

**Checkpoint cuối ngày 5:**

- Customer đặt 2 đêm Deluxe → chọn payment online → tạo booking PENDING và thanh toán đủ
- Payment callback verified đủ tiền → tự động CONFIRMED; staff chỉ dùng Confirm thủ công cho trường hợp thu tiền mặt
- Gán phòng 301 → check-in
- Xem invoice

---

## Ngày 6 — Billing, Folio & Invoicing

**Mục tiêu:** Phát sinh dịch vụ, tạo hóa đơn, in/tải hóa đơn.

### Backend

#### BE-6.1 | Folio Charge — Service Consumption | Priority: Urgent | 23/08 | Est: 5h

- `FolioChargeService`: ghi khoản phát sinh vào booking
- Loại dịch vụ: minibar, giặt ủi, late check-out, penalty
- Snapshot fields tại thời điểm phát sinh:
  - `unit_price`
  - `description`
- Validate: quantity > 0
- Void khoản ghi sai:
  - Set `is_voided=true`
  - Ghi `voided_at`, `voided_by`, `void_reason`
- BR-008: không xóa, chỉ void
- Trigger cập nhật `bookings.services_total` + `bookings.tax_total`

#### BE-6.2 | Invoice Generation (DRAFT) | Priority: Urgent | 23/08 | Est: 5h

- `InvoiceService`: tạo hóa đơn khi check-out
- Logic collect:
  - `booking_room_nights` → dòng ROOM (group by room type, per night)
  - `folio_charges` (chưa void) → dòng SERVICE
- Tính: `subtotal`, `discount_total`, `tax_total`, `total_amount`
- Lưu DRAFT — Staff có thể thêm ADJUSTMENT rows trước khi issue

#### BE-6.3 | Invoice State Machine — Issue & Void (BR-013) | Priority: Urgent | 23/08 | Est: 4h

- `InvoiceService.issue()`: chuyển DRAFT → ISSUED
- Cấp `invoice_number` (INV-2026-XXXXXX)
- Ghi `issued_at`, `issued_by`
- **Trigger immutability (theo DATABASE_DESIGN mục 8.4, BR-013):**
  - `trg_invoices_before_update`: chặn UPDATE các cột chứng từ sau ISSUED
    - Chỉ cho phép: giữ ISSUED hoặc chuyển VOID
  - `trg_invoice_items_before_update`: chặn sửa items khi hóa đơn không còn DRAFT
  - `trg_invoice_items_before_delete`: chặn xóa items khi hóa đơn không còn DRAFT
- VOID:
  - Ghi `voided_at`, `voided_by`, `void_reason`
  - Tạo hóa đơn thay thế nếu cần
- **Chỉ DRAFT mới được DELETE**

#### BE-6.4 | PDF Invoice Generation | Priority: High | 23/08 | Est: 4h

- `InvoicePdfService`: generate PDF hóa đơn (dùng iText hoặc OpenPDF)
- Template:
  - Header khách sạn
  - Invoice number, ngày
  - Thông tin buyer
  - Bảng line items (type, description, qty, unit price, subtotal, discount, tax, total)
  - Footer
- Lưu vào MinIO bucket `invoices`
- Ghi `pdf_url` vào invoice
- API: `GET /api/invoices/{id}/pdf` → stream PDF hoặc redirect URL MinIO signed

#### BE-6.5 | Hotel Settings Table | Priority: Normal | 23/08 | Est: 3h

- `HotelSettingsService`
- Tạo bảng `hotel_settings` (schema bổ sung vào V1 baseline)
- Key fields:
  - `standard_check_in_time`
  - `default_checkout_time`
  - `hotel_timezone`
  - `default_currency`
  - `default_room_tax_percent`
  - `default_no_show_charge_percent`
- Đọc từ `hotel_settings` thay vì hard-code
- API: chỉ ADMIN đọc/sửa
- Seed default values

### Frontend

#### FE-6.1 | Staff — Folio Panel | Priority: Urgent | 23/08 | Est: 5h

- Component "Folio" trong trang chi tiết booking:
  - Tab "Tiền phòng" (từ booking_room_nights)
  - Tab "Dịch vụ" (từ folio_charges)
- Nút "Thêm khoản phát sinh":
  - Chọn loại dịch vụ từ `service_items`
  - Nhập số lượng → auto tính tiền
- Bảng charges:
  - Nút Void (sau khi void vẫn hiển thị dòng với strikethrough + badge "Đã hủy")
- Tổng cộng ở footer

#### FE-6.2 | Staff — Invoice Issuance | Priority: Urgent | 23/08 | Est: 5h

- Component trong booking detail:
  - Nút "Xuất hóa đơn"
  - Hiển thị preview hóa đơn (read-only, giống PDF)
- Form: nhập thông tin buyer:
  - Tên, địa chỉ, mã số thuế, email
  - Pre-filled từ booking contact
- Có thể thêm dòng ADJUSTMENT
- Nút "Phát hành" → gọi issue API → hiển thị invoice number
- Sau ISSUED:
  - Hiển thị badge
  - Nút "Tải PDF"
  - Nút "Hủy hóa đơn"

#### FE-6.3 | Customer — Invoice View | Priority: Normal | 23/08 | Est: 3h

- Trang xem hóa đơn:
  - Hiển thị thông tin buyer, bảng items, tổng tiền
  - Nút "Tải PDF" → download từ MinIO signed URL
  - Không cho sửa

**Checkpoint cuối ngày 6:**

- Staff check-out booking
- Folio hiển thị 2 đêm phòng + 1 khoản minibar
- Xuất invoice ISSUED → tải PDF → xem đúng số tiền

---

## Ngày 7 — Payment Integration

**Mục tiêu:** Tích hợp payment gateway, refund, dashboard doanh thu.

### Backend

#### BE-7.1 | Payment Service — Core | Priority: Urgent | 24/08 | Est: 4h

- `PaymentService`: tạo payment record (PENDING)
- Sinh `payment_code` = orderId gửi sang gateway
- Support methods:
  - INTERNET_BANKING (mock)
  - CARD
  - CASH
  - BANK_TRANSFER
  - E_WALLET
- Trả về payment URL/QR
- Idempotency: dùng `idempotency_key` tránh tạo 2 payment khi client retry

#### BE-7.2 | Payment Gateway Integration (VNPay/MoMo stub) | Priority: Urgent | 24/08 | Est: 6h

- `PaymentGatewayService` + `PaymentCallbackController`
- Interface abstraction để plug in VNPay/MoMo/Stripe
- Implement mock gateway cho dev (luôn return success)
- Real gateway:
  - Build payment URL
  - Verify callback signature (BR-012: `signature_valid` flag)
  - Handle IPN (Instant Payment Notification)
- Save raw payload vào `payment_events`
- Webhook endpoint: `POST /api/payments/callback/{provider}`
- BR-012 enforced: không cho SUCCEEDED nếu chưa verified

#### BE-7.3 | Payment Ledger Sync + Booking Payment Status | Priority: Urgent | 24/08 | Est: 4h

- Trigger cập nhật:
  - `bookings.paid_amount`
  - `invoices.paid_amount`
  - `payment_status`
- Khi payment chuyển SUCCEEDED
- BR-012: chỉ verified payment mới trigger booking CONFIRMED
  - Tự động hạ PENDING → CONFIRMED sau khi thanh toán đủ toàn bộ booking được xác minh
- Staff/admin có thể Confirm thủ công booking PENDING để ghi nhận tiền mặt ngoài payment ledger;
  lịch sử dùng `MANUAL`, không tạo payment record tự động
- Refund: cập nhật `refunded_amount` + `payment_status` trên 3 bảng

#### BE-7.4 | Refund Service — Cancellation Refund | Priority: High | 24/08 | Est: 5h

- `RefundService`: xử lý hoàn tiền khi hủy booking
- **Logic tính refund (theo DATABASE_DESIGN mục 5.3):**
  1. Đọc `booking_rooms.cancellation_policy_snapshot` (JSON) — không dùng policy hiện tại
  2. `hours_before_cancel = scheduled_check_in_time - cancelled_at`
  3. Tìm rule: `min_hours_before` LỚN NHẤT nhưng ≤ `hours_before_cancel`
     - Ví dụ: 80h → 72h rule = 100%, 50h → 30h rule = 50%
  4. Tính refund = `(rooms_total + services_total) × refund_percent`
  5. **Công thức đầy đủ:**
     - `gross_refund = (rooms_total + services_total) × refund_percent / 100`
     - `net_refund = gross_refund - (gross_refund × source_commission_percent_snapshot / 100)`
       - Hoàn không lấy lại hoa hồng đã trả OTA
  6. Ghi `refunds` record với `policy_applied` (JSON snapshot phép tính)
- Workflow: PENDING → PROCESSING → COMPLETED
- BR-005: chỉ booking owner hoặc ADMIN mới yêu cầu refund

#### BE-7.5 | Revenue Dashboard Queries | Priority: High | 24/08 | Est: 4h

- `RevenueService`: các query cho dashboard
- Metrics:
  - ADR = `SUM(booking_room_nights.price) / COUNT(DISTINCT booking_room_nights.stay_date)`
  - RevPAR: ADR × Occupancy Rate
  - Doanh thu theo ngày/tháng (SUM `total_amount` WHERE status=CHECKED_OUT)
  - Doanh thu theo nguồn (booking_sources)
  - Commission OTA: `SUM(rooms_total × source_commission_percent_snapshot)`
  - Occupancy rate: occupied_room_nights / available_room_nights × 100

### Frontend

#### FE-7.1 | Payment Flow UI | Priority: Urgent | 24/08 | Est: 4h

- Bước thanh toán trong booking wizard:
  - Chọn phương thức (Internet Banking, Thẻ, Ví điện tử)
  - Mock payment page hiển thị QR code / redirect
  - Sau callback: loading state → success/fail
  - Auto-redirect về booking detail sau 3s

#### FE-7.2 | Admin/Staff — Payment Management | Priority: High | 24/08 | Est: 4h

- Trang quản lý thanh toán:
  - Danh sách payments
  - Filter theo booking, trạng thái, ngày
- Chi tiết payment:
  - Số tiền, phương thức, gateway reference, thời điểm
- Actions:
  - Nút "Xác minh thủ công" (Admin, cho CASH)
  - Nút "Hoàn tiền":
    - Mở modal chọn amount (≤ paid)
    - Reason
    - Gửi yêu cầu refund

#### FE-7.3 | Dashboard Overview | Priority: Urgent | 24/08 | Est: 5h

- Trang dashboard Staff/Admin:
  - Cards:
    - "Booking hôm nay" (arrivals/departures count)
    - "Phòng trống" (occupancy bar chart)
    - "Doanh thu tháng này" (với so sánh tháng trước)
  - Arrivals list: bảng khách đến hôm nay (từ `booking_rooms` check-in date)
  - Quick actions: "Check-in", "Check-out", "Tạo booking"
  - Occupancy calendar mini: 7 ngày tới

#### FE-7.4 | Revenue Reports | Priority: High | 24/08 | Est: 5h

- Trang báo cáo doanh thu (Admin):
  - Date range picker
  - Biểu đồ:
    - Doanh thu theo ngày (bar chart)
    - Theo tháng (line chart)
    - Theo nguồn (pie chart)
  - Bảng: top 10 loại phòng theo doanh thu
  - Export CSV
  - Tính ADR, RevPAR, occupancy rate
  - Filter theo room type, booking source

**Checkpoint cuối ngày 7:**

- Customer đặt → redirect gateway → callback verified → booking CONFIRMED
- Admin xem dashboard: doanh thu tháng 8 = 850 triệu, ADR = 1.2 triệu

---

## Ngày 8 — Shift Management, Reviews & Polish

**Mục tiêu:** Hoàn thiện shift scheduling, reviews, email system, polish.

### Backend

#### BE-8.1 | Shift Calendar & Assignment API | Priority: High | 25/08 | Est: 4h

- `ShiftAssignmentController`: endpoints CRUD
- GET:
  - Lịch ca theo ngày/tuần (staff assigned to shifts)
  - Lịch ca theo staff (xem ai trực ngày nào)
- POST: gán staff vào ca
- PUT: đổi ca, đổi ngày
- DELETE: hủy assignment
- **BR-015 enforcement (theo DATABASE_DESIGN mục 3.8):**
  - Trigger `trg_shift_assignments_before_insert/update`: kiểm tra `staff_id` không có ca hiệu lực (SCHEDULED/COMPLETED) giao nhau về thời gian
  - Logic overlap: `NOT (existing.shift_end <= NEW.shift_start OR existing.shift_start >= NEW.shift_end)`
  - `shift_start_at`/`shift_end_at` tính từ `work_date + shift.start_time/end_time`, xử lý `crosses_midnight` (ca đêm thêm 1 ngày)
- BR-014: RBAC `shift:manage` chỉ ADMIN

#### BE-8.2 | Review System (BR-006/BR-007) | Priority: High | 25/08 | Est: 4h

- `ReviewService`
- Trigger kiểm tra: chỉ booking CHECKED_OUT + customer_id khớp
- Chỉ 1 review/booking (UNIQUE constraint)
- Stars rating (1-5):
  - overall, room, cleanliness, service, value
- Moderate:
  - Admin approve/reject
  - Status: PENDING → PUBLISHED/HIDDEN/REJECTED
  - Staff reply

#### BE-8.3 | Email System — Templates & Queue | Priority: Normal | 25/08 | Est: 4h

- `EmailService`
- Gửi email:
  - Dev: SMTP log to console
  - Prod: SES/SendGrid
- Template system:
  - BOOKING_CONFIRMED
  - PAYMENT_SUCCESS
  - BOOKING_CANCELLED
  - PAYMENT_REFUND
  - ACCOUNT_ACTIVATED
- Template engine: thay biến (`{{booking_code}}`, `{{customer_name}}`, v.v.)
- Queue: `email_messages` table
  - Worker poll (interval 30s)
  - Gửi → retry 3 lần nếu fail
- Track: `sent_at`, `attempt_count`, `last_error`

#### BE-8.4 | Audit Logging + Background Jobs | Priority: Normal | 25/08 | Est: 4h

- `AuditLogService`:
  - Hook vào Spring AOP
  - Ghi mọi thao tác update/delete vào `audit_logs` (before/after JSON)
- Jobs:
  1. `HoldExpiryJob`:
     - Scan PENDING bookings where `hold_expires_at < now()`
     - Chuyển EXPIRED
     - Giải phóng booking_rooms và đánh dấu payment PENDING/PROCESSING là EXPIRED
     - Không hard delete booking
  2. `NoShowJob`:
     - Scan CONFIRMED bookings where `check_in_date < today()` and not checked in
     - Chuyển NO_SHOW
     - Tính penalty từ `no_show_charge_percent`
  3. `ExpiredTokenCleanupJob`:
     - Xóa `auth_tokens` hết hạn quá 30 ngày

### Frontend

#### FE-8.1 | Admin — Shift Management Page | Priority: High | 25/08 | Est: 5h

- Trang quản lý ca trực (Admin):
  - Calendar view (tuần × staff, cell = ca được gán)
  - Drag-drop gán staff vào ca
- Modal tạo ca mới:
  - MORNING/AFTERNOON/NIGHT với giờ
- Modal phân công:
  - Chọn ngày, chọn staff, chọn ca
- Badge màu theo trạng thái:
  - SCHEDULED = xanh
  - COMPLETED = cam
  - ABSENT = đỏ
  - CANCELLED = xám
- Validate: không gán 2 ca overlap

#### FE-8.2 | Customer — Review Submission | Priority: Normal | 25/08 | Est: 3h

- Trang "Viết đánh giá" (chỉ hiện sau CHECKED_OUT):
  - Form stars cho từng category
  - Title, comment
  - Preview trước khi gửi
  - Sau gửi: "Cảm ơn bạn đã đánh giá" + link về My Bookings

#### FE-8.3 | Admin — Review Moderation | Priority: Normal | 25/08 | Est: 3h

- Trang duyệt đánh giá:
  - Danh sách reviews (filter: status, room type, rating)
  - Expand: xem chi tiết, nội dung
- Actions:
  - "Phê duyệt" (→ PUBLISHED)
  - "Ẩn" (→ HIDDEN)
  - "Từ chối" (→ REJECTED, có lý do)
- Reply: nhập phản hồi từ khách sạn

#### FE-8.4 | Staff — Shift Schedule View | Priority: Normal | 25/08 | Est: 3h

- Trang lịch ca của Staff:
  - Hiển thị ca được phân công (tuần hiện tại + tuần tới)
  - Badge: ngày, ca, trạng thái
- Actions:
  - Nút "Đánh dấu hoàn thành ca" (khi hết ca → COMPLETED)
  - Nút "Báo vắng" (→ ABSENT, có ghi chú)

#### FE-8.5 | Polish — Responsive, Error Handling, SEO | Priority: Normal | 25/08 | Est: 4h

- Audit tất cả pages:
  - Responsive (mobile sidebar → drawer, table → card list)
  - Error boundaries cho Next.js
  - Loading skeletons
  - Toast notifications cho mọi action
- Metadata cho SEO (room type pages)
- Accessibility:
  - aria labels
  - keyboard navigation

**Checkpoint cuối ngày 8:**

- Full system smoke test:
  1. Tạo account
  2. Đặt phòng
  3. Thanh toán
  4. Check-in
  5. Phát sinh dịch vụ
  6. Checkout
  7. Xuất hóa đơn
  8. Gửi review
  9. Xem dashboard doanh thu
  10. Phân công ca
  11. Gửi email xác nhận

---

## Tổng kết — Deliverables cuối cùng

| Phase                  | Ngày | Backend deliverables                                                                                       | Frontend deliverables                                               |
| ---------------------- | ----- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **Foundation**   | 1     | Docker compose, Spring Boot project, 39 JPA entities, Flyway migration (MySQL)                             | Next.js project, base UI components                                 |
| **Auth & Users** | 2     | JWT auth, RBAC (15+ permissions), User/Staff/Customer CRUD, Shift management                               | Login, Register, Email verification, Profile                        |
| **Inventory**    | 3     | RoomType, Room, Amenity CRUD, Room blocks (BR-003/BR-004), HK status                                       | Admin Room Management + Floor map + Maintenance scheduling          |
| **Pricing**      | 4     | Rate engine, Cancellation policies (multi-tier), Booking price calculator                                  | Admin Pricing + Policies + Customer room listing + date picker      |
| **Booking**      | 5     | Availability engine, Booking creation (snapshot pricing), State machine, Room assignment, Guest ID storage | Customer booking wizard + My Bookings + Staff booking management    |
| **Billing**      | 6     | Folio charges, Invoice generation (DRAFT→ISSUED→VOID), PDF generation (MinIO), Hotel settings            | Staff folio panel + Invoice issuance + Customer invoice view        |
| **Payment**      | 7     | Payment gateway (VNPay/MoMo stub), Callback + verification, Refund engine, Revenue queries                 | Payment UI + Staff payment management + Dashboard + Revenue reports |
| **Polish**       | 8     | Shift calendar API, Reviews (BR-006/BR-007), Email queue + templates, Audit logs + Jobs                    | Shift management + Reviews + Polish + Responsive + Accessibility    |

---

## Kiến trúc MySQL thay thế PostgreSQL

| PostgreSQL feature              | MySQL replacement                                                                  |
| ------------------------------- | ---------------------------------------------------------------------------------- |
| `EXCLUDE USING gist` (BR-002) | Trigger`BEFORE INSERT/UPDATE` kiểm tra overlap query                            |
| `EXCLUDE` BR-015 (shift)      | Trigger kiểm tra`shift_period` overlap                                          |
| `daterange` / `tstzrange`   | 2 cột DATE/TIMESTAMP, query`[)` bằng `WHERE a_in < b_out AND a_out > b_in`   |
| `CITEXT`                      | `VARCHAR` + `LOWER()` ở application layer hoặc generated column              |
| `JSONB`                       | `JSON` (MySQL 8+)                                                                |
| `UUID`                        | `CHAR(36)` hoặc `BINARY(16)`                                                  |
| `GENERATED COLUMN`            | MySQL supports`GENERATED ALWAYS AS` — dùng cho `nights`, `stay_range`      |
| GiST index                      | Trigger-based overlap check (performance acceptable cho < 10k concurrent bookings) |

---

## MinIO Integration Points

- Room images
- Room type images
- Invoice PDFs
- User avatars

**Bucket naming:**

- `room-images`
- `room-type-images`
- `invoices`
- `avatars`

**Presigned URLs:**

- TTL 1 giờ cho upload
- TTL 15 phút cho download

---

## Quy tắc Snapshot (QĐ-4, QĐ-5)

- Giá đêm → `booking_room_nights.price` (snapshot, bất biến)
- Chính sách hủy → `booking_rooms.cancellation_policy_snapshot` (JSON, bất biến theo từng dòng phòng)
- Hoa hồng OTA → `bookings.source_commission_percent_snapshot` (bất biến)
- Giá dịch vụ → `folio_charges.unit_price` (snapshot tại thời điểm phát sinh)
- Thông tin người mua → `invoices.buyer_*` (snapshot tại thời điểm phát hành)

---

## Booking Room Status (5 trạng thái theo DATABASE_DESIGN)

- `RESERVED` — đã giữ phòng, khách chưa ở (chiếm phòng)
- `OCCUPIED` — khách đang ở (chiếm phòng)
- `COMPLETED` — đã ở và checkout bình thường (không chiếm phòng)
- `RELEASED` — reservation được giải phóng trước khi hoàn tất (cancel/expire/no-show)
- `MOVED_OUT` — khách đã rời phòng này vì được chuyển sang phòng khác

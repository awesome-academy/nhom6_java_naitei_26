# Hotel Management — Kế hoạch Triển khai 8 Ngày

**Stack:** MySQL + Docker + Spring Boot (Java 21) + MinIO + Next.js
**Nguồn:** Schema 39 bảng, 27 enum, 70 FK (`DATABASE_DESIGN.md` + `hotel_management.dbml`)
**Ghi chú:** Từ ngày 18/08/2026. Ưu tiên BE trước FE. Task BE ≤ 8h/task. Task FE ghép chung ngày với FE setup.

---

## Ngày 1 — Infrastructure & Foundation
**Mục tiêu:** Môi trường dev chạy được, schema MySQL sinh ra, app boot không lỗi.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-1.1 | Setup Docker Compose | Tạo `docker-compose.yml` với MySQL 8.0 (port 3306), MinIO (port 9000/9001), Redis (port 6379, cho session/cache). Volume cho MySQL + MinIO data. Config network `hotel-network`. Env file cho password/port. | Immediate | 18/08 | 18/08 | 3 |
| BE-1.2 | Spring Boot Project Init | Tạo project Spring Boot 3.x (Java 21) với: Maven/Gradle, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-mail`, `spring-boot-starter-data-redis`, `mybatis-spring-boot-starter`. Cấu hình `application.yml` đọc env Docker. | Immediate | 18/08 | 18/08 | 2 |
| BE-1.3 | JPA Entities từ Schema | Sinh JPA entity classes từ 39 bảng: `users`, `roles`, `permissions`, `customer_profiles`, `staff_profiles`, `shifts`, `shift_assignments`, `room_types`, `rooms`, `bookings`, `booking_rooms`, `booking_room_nights`, `booking_guests`, `folio_charges`, `invoices`, `invoice_items`, `payments`, `refunds`, `reviews`, v.v. Enum classes cho 27 enum. Không có business logic ở đây — chỉ mapping. Chuyển `datarange`/`tstzrange` PostgreSQL → stored/generated columns trong MySQL (MySQL 8 không có range types). | Urgent | 18/08 | 18/08 | 8 |
| BE-1.4 | Flyway Migration Scripts | Viết Flyway migration V1 (baseline): tạo toàn bộ bảng, index, FK, enum values, seed data (roles, booking_sources, cancellation_policies + rules, room_types, shifts). Chạy script đảm bảo schema hoàn chỉnh trên MySQL. | Urgent | 18/08 | 18/08 | 4 |

**Ghi chú MySQL:** `daterange`/`tstzrange`/`EXCLUDE USING gist` không tồn tại trên MySQL. Thay bằng:
- `stay_range` → 2 cột `check_in_date` + `check_out_date`, query kiểm tra overlap bằng `WHERE check_in < other_check_out AND check_out > other_check_in`
- `EXCLUDE` → trigger BEFORE INSERT/UPDATE kiểm tra overlap (đúng logic BR-002)
- `CITEXT` → `VARCHAR` + `LOWER()` ở application layer hoặc generated column
- `UUID` → `CHAR(36)` hoặc `BINARY(16)`
- `JSONB` → `JSON`

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-1.1 | Next.js Project Init | Tạo Next.js 14+ (App Router) project. Setup ESLint, Prettier, TypeScript strict mode. Cấu hình `next.config.js` cho image domains (MinIO). Tạo folder structure: `/app/(auth)`, `/app/(dashboard)`, `/components`, `/lib`, `/hooks`, `/types`, `/store`. | Immediate | 18/08 | 18/08 | 2 |
| FE-1.2 | Tailwind + UI Components | Setup Tailwind CSS với design tokens (colors, typography từ spec). Cài thư viện: `lucide-react` (icons), `clsx`, `tailwind-merge`, `react-hook-form`, `@hookform/resolvers`, `zod`. Tạo base components: `Button`, `Input`, `Select`, `Badge`, `Card`, `Modal`, `Table`, `Dropdown`. | Normal | 18/08 | 18/08 | 3 |

**Checkpoint cuối ngày 1:** `curl localhost:8080/actuator/health` → UP; `curl localhost:3000` → Next.js welcome; Flyway migrate thành công.

---

## Ngày 2 — Authentication & User Management
**Mục tiêu:** Đăng nhập/đăng ký chạy, RBAC enforce ở API.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-2.1 | Auth Service — JWT + OAuth | Implement JWT authentication: `JwtService` (generate, validate, refresh token), `AuthController` (login, register, logout, refresh). Hỗ trợ OAuth2/social login stub (Google). Token stored in Redis with TTL. BCrypt password hashing (cost 12). Failed login lock after 5 attempts (update `users.locked_until`). | Urgent | 19/08 | 19/08 | 6 |
| BE-2.2 | User CRUD + Email Verification | `UserService` + `UserController`: register (send email verification token → `auth_tokens`), verify email, request password reset, reset password. `AuthTokenService` handle token lifecycle (create, validate one-time use, expire). Implement email sending stub (log to console for dev, swap to SES/SendGrid later). | Urgent | 19/08 | 19/08 | 5 |
| BE-2.3 | RBAC — Roles & Permissions | `RoleService`, `PermissionService`, `RolePermissionService`. `@PreAuthorize` annotations trên controller methods theo 15+ permissions (`room:create`, `booking:cancel_any`, `staff:manage`, `shift:manage`, v.v.). Seed 3 roles (CUSTOMER/STAFF/ADMIN) + all permissions vào V1 migration. Interceptor kiểm tra RBAC trước khi gọi service. | Urgent | 19/08 | 19/08 | 5 |
| BE-2.4 | CustomerProfile & StaffProfile CRUD | `CustomerProfileService` + `StaffProfileService`. Endpoints: tạo profile sau register, cập nhật thông tin cá nhân, deactivate (soft delete). Staff: hire (tạo staff_profile + user nếu chưa có), edit, deactivate (set `terminated_at`, `employment_status=TERMINATED`). Thêm thuộc tính: `customer_profiles.loyalty_points`, `staff_profiles.base_salary` (chỉ ADMIN đọc). | Normal | 19/08 | 19/08 | 4 |
| BE-2.5 | Shifts & Shift Assignments CRUD | `ShiftService` + `ShiftAssignmentService`. Endpoints: tạo/sửa/xóa ca (`shifts`), phân công Staff vào ca theo ngày (`shift_assignments`). Logic tính `shift_period` = `work_date + start_time` (xử lý `crosses_midnight`). BR-015: trigger kiểm tra Staff không overlap ca. | Normal | 19/08 | 19/08 | 4 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-2.1 | Auth Pages — Login/Register | Trang đăng nhập + đăng ký (Next.js App Router, Server Actions). Form validation với Zod. Hiển thị lỗi: "Tài khoản bị khóa", "Email chưa xác thực". Toast notifications. Responsive, mobile-first. | Urgent | 19/08 | 19/08 | 4 |
| FE-2.2 | Auth Pages — Email Verification & Password Reset | Trang xác thực email (click link từ mail → token validated → redirect login), trang quên mật khẩu, trang đặt lại mật khẩu. Tích hợp `next/link` cho magic links. | Normal | 19/08 | 19/08 | 3 |
| FE-2.3 | User Profile Page | Trang hồ sơ cá nhân: xem/sửa `full_name`, `phone`, `date_of_birth`, `nationality`, `address`. Customer: thêm loyalty points display. Staff: hiển thị `position`, `department`. Avatar upload (→ MinIO). | Normal | 19/08 | 19/08 | 3 |

**Checkpoint cuối ngày 2:** Staff đăng nhập → redirect `/dashboard`; Customer đăng nhập → redirect `/my-bookings`; Không có quyền → 403.

---

## Ngày 3 — Room & Inventory Management
**Mục tiêu:** CRUD đầy đủ cho loại phòng, phòng, tiện nghi, ảnh.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-3.1 | RoomType CRUD + Beds + Amenities | `RoomTypeService` + `RoomTypeController`: tạo/sửa/xóa (soft) loại phòng. Quản lý `room_type_beds` (thêm/bớt giường). Quản lý `room_type_amenities` (gán tiện nghi). Seed amenities mẫu (WIFI, TV, MINIBAR, AC, BALCONY, POOL, SPA). `SlugService` auto-generate slug từ name. | Urgent | 20/08 | 20/08 | 5 |
| BE-3.2 | Room CRUD + Housekeeping | `RoomService` + `RoomController`: tạo/sửa/soft-delete phòng. Gán `room_type_id`, `view_type` (enum), `floor`, `price_override`. Cập nhật `housekeeping_status` (CLEAN → DIRTY sau checkout → CLEANING → CLEAN). `RoomImageService` quản lý upload/sort ảnh → MinIO bucket `room-images`. Endpoint: "danh sách phòng theo loại" + "lọc theo view, tầng, tiện nghi". | Urgent | 20/08 | 20/08 | 5 |
| BE-3.3 | Room Status Blocks (BR-003/BR-004) | `RoomStatusBlockService`: tạo/kéo dài/hủy khoảng bảo trì. BR-004 trigger: chặn booking trên phòng đang block. API: "phòng đang block trong khoảng ngày", "tạo block mới". Check `operational_status` (ACTIVE/MAINTENANCE/RENOVATION). | High | 20/08 | 20/08 | 4 |
| BE-3.4 | Amenity CRUD + Filtering | `AmenityService`: tạo/sửa/xóa tiện nghi. Phân loại: ROOM/BATHROOM/TECH/SERVICE. Đánh dấu `is_filterable`. Endpoint lấy danh sách filter options. | Normal | 20/08 | 20/08 | 2 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-3.1 | Admin — Room Types Management | Trang quản lý loại phòng: bảng danh sách (phân trang, tìm kiếm), modal tạo/sửa (form với beds config, amenities multi-select, giá, mô tả). Upload ảnh loại phòng (MinIO). Soft delete với confirm dialog. | Urgent | 20/08 | 20/08 | 5 |
| FE-3.2 | Admin — Rooms Management + Floor Map | Trang quản lý phòng: bảng + filters (loại phòng, tầng, view, trạng thái HK). Component "sơ đồ tầng" hiển thị grid phòng, màu theo `housekeeping_status` (xanh=CLEAN, đỏ=DIRTY, cam=CLEANING). Click phòng → side panel chi tiết. Modal tạo/sửa phòng. | Urgent | 20/08 | 20/08 | 5 |
| FE-3.3 | Admin — Room Maintenance Scheduling | Trang đặt lịch bảo trì: calendar view (ngày × phòng), tạo block với ngày bắt đầu/kết thúc, loại block, ghi chú. Validate: không cho tạo block trùng ngày trên cùng phòng. | Normal | 20/08 | 20/08 | 4 |

**Checkpoint cuối ngày 3:** Admin tạo loại phòng Deluxe → gán 10 phòng → upload ảnh → đặt bảo trì phòng 301 ngày 25-27/08.

---

## Ngày 4 — Pricing, Policies & Rate Engine
**Mục tiêu:** Hệ thống giá hoàn chỉnh, policy hủy nhiều bậc, API tính giá cho booking.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-4.1 | Rate Engine — Giá theo ngày | `RateEngineService`: tính giá phòng cho một khoảng ngày. Logic: `rooms.price_override` → `rate_overrides` (priority cao nhất, đúng ngày) → `room_types.base_price`. Xử lý `weekdays` array (T7, CN). Trả về danh sách `{date, price}` per night. Đây là pricing snapshot tầng 1. | Urgent | 21/08 | 21/08 | 5 |
| BE-4.2 | RateOverride CRUD | `RateOverrideService` + `RateOverrideController`: tạo/quản lý giá theo mùa/cuối tuần. Fields: `room_type_id`/`room_id`, date range, price, weekdays, priority. Validate: đúng 1 trong 2 room identifiers khác null. List overrides đang active. | Normal | 21/08 | 21/08 | 3 |
| BE-4.3 | CancellationPolicy + Rules | `CancellationPolicyService`: CRUD policy + rules. Validate: mỗi policy có rule `min_hours_before=0`. Seed 3 policies (FLEXIBLE/MODERATE/NON_REFUND) + rules. Snapshot policy + rules vào JSONB → gửi lên booking. | Urgent | 21/08 | 21/08 | 4 |
| BE-4.4 | Booking Price Calculator API | `BookingCalculatorService`: nhận `{room_type_id, check_in, check_out, adults, children}`, gọi RateEngine, tính rooms_total, áp dụng `room_tax_percent_snapshot`, trả về tổng tiền dự kiến (không lưu DB). API: `POST /api/bookings/calculate-price`. Dùng cho trang xem giá trước khi đặt. | Urgent | 21/08 | 21/08 | 4 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-4.1 | Admin — Pricing Management | Trang quản lý giá: bảng rate_overrides (date range, price, priority). Form tạo override: chọn loại phòng, ngày, giá, weekdays checkboxes (T2-T7, CN). Calendar view hiển thị giá theo ngày. | High | 21/08 | 21/08 | 4 |
| FE-4.2 | Admin — Cancellation Policies | Trang quản lý chính sách hủy: danh sách policy, expand → xem các bậc hoàn tiền (table: "trước X giờ → hoàn Y%"). Form tạo/sửa policy (code, name, no_show_charge_percent), thêm/bớt rule (dynamic rows). Validate: luôn có bậc 0 giờ. | High | 21/08 | 21/08 | 4 |
| FE-4.3 | Customer — Room Listing & Price Display | Trang danh sách loại phòng (Customer): grid card hiển thị ảnh, tên, giá/đêm (từ `base_price`), tiện nghi icons, nút "Xem chi tiết". Chi tiết phòng: gallery ảnh, mô tả, bed config, amenities, giá theo ngày (từ RateEngine). Date picker chọn check-in/check-out → gọi `/calculate-price` → hiển thị tổng tiền dự kiến. | High | 21/08 | 21/08 | 5 |

**Checkpoint cuối ngày 4:** Chọn Deluxe, 18-21/08 → hiển thị đúng giá cuối tuần 21/08 cao hơn ngày thường. Tạo FLEXIBLE policy với 3 bậc hoàn tiền.

---

## Ngày 5 — Booking Engine
**Mục tiêu:** Tạo booking, check availability, gán phòng, quản lý trạng thái booking.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-5.1 | Availability Engine | `AvailabilityService`: query kiểm tra phòng khả dụng cho `[check_in, check_out)`. Logic: `rooms.operational_status='ACTIVE'` + không overlap `booking_rooms` (status IN RESERVED/OCCUPIED) + không overlap `room_status_blocks`. Trả về `{room_type_id: [available_room_ids]}` để FE hiển thị phòng cụ thể có thể đặt. BR-002: dùng trigger MySQL thay cho PostgreSQL EXCLUDE. | Urgent | 22/08 | 22/08 | 5 |
| BE-5.2 | Booking Creation — Snapshot Pricing | `BookingService.createBooking()`: tạo booking ở PENDING. Tính giá từng đêm → ghi `booking_room_nights` rows. Snapshot `room_tax_percent_snapshot`, `cancellation_policy_snapshot` (JSONB full policy + rules), `source_commission_percent_snapshot`. Tạo `booking_code` (BK-2026-XXXXXX). Setup `hold_expires_at` (30 phút nếu không thanh toán ngay). Ghi `booking_status_history` dòng đầu. | Urgent | 22/08 | 22/08 | 6 |
| BE-5.3 | Booking State Machine + Triggers | `BookingStateMachineService`: implement đúng các transition (8.1). BR-010: chỉ CONFIRMED → CHECKED_IN. BR-011: chỉ CHECKED_IN → CHECKED_OUT. BR-005: trigger kiểm tra customer_id + status. Mỗi transition ghi `booking_status_history`. Trigger đồng bộ `booking_room_status` (8.2): CHECKED_IN → OCCUPIED, CHECKED_OUT → COMPLETED, CANCELLED → RELEASED. | Urgent | 22/08 | 22/08 | 5 |
| BE-5.4 | Booking Room Assignment + Room Change | `BookingRoomService`: gán phòng cụ thể khi check-in. BR-009: `assigned_at`, `assigned_by`. Room change giữa kỳ: tạo dòng `booking_rooms` mới với `moved_from_booking_room_id`, chuyển dòng cũ → `MOVED_OUT`, re-price không thay đổi giá đã snapshot. | High | 22/08 | 22/08 | 4 |
| BE-5.5 | BookingGuest + ID Document Storage | `BookingGuestService`: thêm khách lưu trú vào booking. ID document: mã hóa AES-256-GCM trước khi lưu (key từ config/KMS), `id_document_lookup_hash` = HMAC-SHA256 để search. BR-014: RBAC `guest:read_id` để xem CCCD (cần log vào `audit_logs`). | High | 22/08 | 22/08 | 4 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-5.1 | Customer — Booking Flow | Multi-step booking wizard: B1 chọn phòng & ngày (Availability API → hiển thị phòng khả dụng), B2 nhập thông tin liên hệ + khách lưu trú, B3 chọn cancellation policy (hiển thị refund tiers), B4 review + đặt cọc (chọn payment method → redirect gateway). Progress indicator. | Urgent | 22/08 | 22/08 | 6 |
| FE-5.2 | Customer — My Bookings | Trang "Đơn đặt của tôi": danh sách booking của customer đăng nhập, filter theo status. Chi tiết booking: thông tin phòng, ngày, tổng tiền, trạng thái, timeline (từ `booking_status_history`). Nút "Hủy booking" (nếu PENDING/CONFIRMED → BR-005), nút "Xem hóa đơn". | Urgent | 22/08 | 22/08 | 4 |
| FE-5.3 | Staff — Booking Management | Trang quản lý booking (Staff): bảng tất cả booking, filter: ngày đến, ngày đi, nguồn, trạng thái. Tìm nhanh theo booking_code, tên khách, SĐT. Click row → drawer chi tiết. Actions: "Xác nhận booking", "Check-in", "Check-out", "Hủy", "Gán phòng". | Urgent | 22/08 | 22/08 | 5 |

**Checkpoint cuối ngày 5:** Customer đặt 2 đêm Deluxe → tạo booking PENDING → Staff confirm → gán phòng 301 → check-in → xem invoice.

---

## Ngày 6 — Billing, Folio & Invoicing
**Mục tiêu:** Phát sinh dịch vụ, tạo hóa đơn, in/tải hóa đơn.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-6.1 | Folio Charge — Service Consumption | `FolioChargeService`: ghi khoản phát sinh vào booking (minibar, giặt ủi, late check-out, penalty). Snapshot `unit_price`, `description` tại thời điểm phát sinh. Validate: quantity > 0. Void khoản ghi sai (set `is_voided=true`, ghi `voided_at`, `voided_by`, `void_reason`). Trigger cập nhật `bookings.services_total` + `bookings.tax_total`. BR-008: không xóa, chỉ void. | Urgent | 23/08 | 23/08 | 5 |
| BE-6.2 | Invoice Generation (DRAFT) | `InvoiceService`: tạo hóa đơn khi check-out. Logic: collect `booking_room_nights` → dòng ROOM (group by room type, per night), collect `folio_charges` (chưa void) → dòng SERVICE, tính `subtotal`, `discount_total`, `tax_total`, `total_amount`. Lưu DRAFT — Staff có thể thêm ADJUSTMENT rows (dương hoặc âm) trước khi issue. | Urgent | 23/08 | 23/08 | 5 |
| BE-6.3 | Invoice State Machine — Issue & Void (BR-013) | `InvoiceService.issue()`: chuyển DRAFT → ISSUED. Cấp `invoice_number` (BK-2026-XXXX). Ghi `issued_at`, `issued_by`. Trigger: sau ISSUED **không cho UPDATE** các cột chứng từ (buyer info, line items, amounts). Chỉ cho update: `payment_status`, `paid_amount`, `refunded_amount`, `pdf_url`, void fields. VOID: ghi `voided_at`, `voided_by`, `void_reason`, tạo hóa đơn thay thế nếu cần. BR-013 enforced at DB level. | Urgent | 23/08 | 23/08 | 4 |
| BE-6.4 | PDF Invoice Generation | `InvoicePdfService`: generate PDF hóa đơn (dùng iText hoặc OpenPDF). Template: header khách sạn, invoice number, ngày, thông tin buyer, bảng line items (type, description, qty, unit price, subtotal, discount, tax, total), footer. Lưu vào MinIO bucket `invoices`. Ghi `pdf_url` vào invoice. API: `GET /api/invoices/{id}/pdf` → stream PDF hoặc redirect URL MinIO signed. | High | 23/08 | 23/08 | 4 |
| BE-6.5 | Hotel Settings Table | `HotelSettingsService`: bảng cấu hình khách sạn (từ C-4 spec). Key fields: `standard_check_in_time`, `default_checkout_time`, `hotel_timezone`, `default_currency`, `default_room_tax_percent`, `default_no_show_charge_percent`. Đọc từ `hotel_settings` thay vì hard-code. API: chỉ ADMIN đọc/sửa. Seed default values. | Normal | 23/08 | 23/08 | 3 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-6.1 | Staff — Folio Panel | Component "Folio" hiển thị trong trang chi tiết booking: tabs "Tiền phòng" (từ booking_room_nights) + "Dịch vụ" (từ folio_charges). Nút "Thêm khoản phát sinh": chọn loại dịch vụ từ `service_items`, nhập số lượng → auto tính tiền. Bảng charges với nút Void (sau khi void vẫn hiển thị dòng với strikethrough + badge "Đã hủy"). Tổng cộng ở footer. | Urgent | 23/08 | 23/08 | 5 |
| FE-6.2 | Staff — Invoice Issuance | Component trong booking detail: nút "Xuất hóa đơn". Hiển thị preview hóa đơn (read-only, giống PDF). Form: nhập thông tin buyer (tên, địa chỉ, mã số thuế, email) — pre-filled từ booking contact. Có thể thêm dòng ADJUSTMENT. Nút "Phát hành" → gọi issue API → hiển thị invoice number. Sau ISSUED: hiển thị badge, nút "Tải PDF", nút "Hủy hóa đơn". | Urgent | 23/08 | 23/08 | 5 |
| FE-6.3 | Customer — Invoice View | Trang xem hóa đơn: hiển thị thông tin buyer, bảng items, tổng tiền. Nút "Tải PDF" → download từ MinIO signed URL. Không cho sửa. | Normal | 23/08 | 23/08 | 3 |

**Checkpoint cuối ngày 6:** Staff check-out booking → folio hiển thị 2 đêm phòng + 1 khoản minibar → xuất invoice ISSUED → tải PDF → xem đúng số tiền.

---

## Ngày 7 — Payment Integration
**Mục tiêu:** Tích hợp payment gateway, refund, dashboard doanh thu.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-7.1 | Payment Service — Core | `PaymentService`: tạo payment record (PENDING). Sinh `payment_code` = orderId gửi sang gateway. Support: INTERNET_BANKING (mock), CARD, CASH, BANK_TRANSFER, E_WALLET. Trả về payment URL/QR. Idempotency: dùng `idempotency_key` tránh tạo 2 payment khi client retry. | Urgent | 24/08 | 24/08 | 4 |
| BE-7.2 | Payment Gateway Integration (VNPay/MoMo stub) | `PaymentGatewayService` + `PaymentCallbackController`: interface abstraction để plug in VNPay/MoMo/Stripe. Implement mock gateway cho dev (luôn return success). Real gateway: build payment URL, verify callback signature (BR-012: `signature_valid` flag), handle IPN (Instant Payment Notification). Save raw payload vào `payment_events`. Webhook endpoint: `POST /api/payments/callback/{provider}`. BR-012 enforced: không cho SUCCEEDED nếu chưa verified. | Urgent | 24/08 | 24/08 | 6 |
| BE-7.3 | Payment Ledger Sync + Booking Payment Status | Trigger cập nhật `bookings.paid_amount`, `invoices.paid_amount`, `payment_status` khi payment chuyển SUCCEEDED. BR-012: chỉ verified payment mới trigger booking CONFIRMED (tự động hạ PENDING → CONFIRMED sau deposit verified). Refund: cập nhật `refunded_amount` + `payment_status` trên 3 bảng. | Urgent | 24/08 | 24/08 | 4 |
| BE-7.4 | Refund Service — Cancellation Refund | `RefundService`: xử lý hoàn tiền khi hủy booking. Logic tính refund từ `cancellation_policy_snapshot` (JSONB): tìm rule phù hợp (`min_hours_before` lớn nhất ≤ hours_before_cancel). Tính `refund_amount = rooms_total × refund_percent`. Ghi `refunds` record. Workflow: PENDING → APPROVED (Admin) → PROCESSING → COMPLETED. Trigger kiểm tra tổng refund ≤ payment amount. BR-005: chỉ booking owner hoặc ADMIN mới yêu cầu refund. | High | 24/08 | 24/08 | 5 |
| BE-7.5 | Revenue Dashboard Queries | `RevenueService`: các query cho dashboard. ADR = `SUM(booking_room_nights.price) / COUNT(DISTINCT booking_room_nights.stay_date)` (RevPAR: ADR × Occupancy Rate). Doanh thu theo ngày/tháng (SUM `total_amount` WHERE status=CHECKED_OUT). Doanh thu theo nguồn (booking_sources). Commission OTA: `SUM(rooms_total × source_commission_percent_snapshot)`. Occupancy rate: occupied_room_nights / available_room_nights × 100. | High | 24/08 | 24/08 | 4 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-7.1 | Payment Flow UI | Bước thanh toán trong booking wizard: chọn phương thức (Internet Banking, Thẻ, Ví điện tử). Mock payment page hiển thị QR code / redirect. Sau callback: loading state → success/fail. Auto-redirect về booking detail sau 3s. | Urgent | 24/08 | 24/08 | 4 |
| FE-7.2 | Admin/Staff — Payment Management | Trang quản lý thanh toán: danh sách payments, filter theo booking, trạng thái, ngày. Chi tiết payment: số tiền, phương thức, gateway reference, thời điểm. Nút "Xác minh thủ công" (Admin, cho CASH). Nút "Hoàn tiền": mở modal chọn amount (≤ paid), reason, gửi yêu cầu refund. | High | 24/08 | 24/08 | 4 |
| FE-7.3 | Dashboard Overview | Trang dashboard Staff/Admin: cards: "Booking hôm nay" (arrivals/departures count), "Phòng trống" (occupancy bar chart), "Doanh thu tháng này" (với so sánh tháng trước). Arrivals list: bảng khách đến hôm nay (từ `booking_rooms` check-in date). Quick actions: "Check-in", "Check-out", "Tạo booking". Occupancy calendar mini: 7 ngày tới. | Urgent | 24/08 | 24/08 | 5 |
| FE-7.4 | Revenue Reports | Trang báo cáo doanh thu (Admin): date range picker. Biểu đồ: doanh thu theo ngày (bar chart), theo tháng (line chart), theo nguồn (pie chart). Bảng: top 10 loại phòng theo doanh thu. Export CSV. Tính ADR, RevPAR, occupancy rate. Filter theo room type, booking source. | High | 24/08 | 24/08 | 5 |

**Checkpoint cuối ngày 7:** Customer đặt → redirect gateway → callback verified → booking CONFIRMED. Admin xem dashboard: doanh thu tháng 8 = 850 triệu, ADR = 1.2 triệu.

---

## Ngày 8 — Shift Management, Reviews & Polish
**Mục tiêu:** Hoàn thiện shift scheduling, reviews, email system, polish.

### Backend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| BE-8.1 | Shift Calendar & Assignment API | `ShiftAssignmentController`: endpoints CRUD. GET: lịch ca theo ngày/tuần (staff assigned to shifts), lịch ca theo staff (xem ai trực ngày nào). POST: gán staff vào ca. BR-015: trigger kiểm tra overlap (thay GiST EXCLUDE). PUT: đổi ca, đổi ngày. DELETE: hủy assignment. BR-014: RBAC `shift:manage` chỉ ADMIN. | High | 25/08 | 25/08 | 4 |
| BE-8.2 | Review System (BR-006/BR-007) | `ReviewService`: tạo review sau CHECKED_OUT (trigger kiểm tra). Chỉ 1 review/booking (UNIQUE). Stars rating (1-5) cho: overall, room, cleanliness, service, value. Moderate: Admin approve/reject (status: PENDING → PUBLISHED/HIDDEN/REJECTED). Staff reply. Trigger: kiểm tra booking CHECKED_OUT + customer_id khớp. | High | 25/08 | 25/08 | 4 |
| BE-8.3 | Email System — Templates & Queue | `EmailService`: gửi email qua SMTP (dev: log) hoặc SES/SendGrid (prod). Template system: BOOKING_CONFIRMED, PAYMENT_SUCCESS, BOOKING_CANCELLED, PAYMENT_REFUND, ACCOUNT_ACTIVATED. Template engine: thay biến (`{{booking_code}}`, `{{customer_name}}`, v.v.). Queue: `email_messages` table → worker poll (interval 30s) → gửi → retry 3 lần nếu fail. Track `sent_at`, `attempt_count`, `last_error`. | Normal | 25/08 | 25/08 | 4 |
| BE-8.4 | Audit Logging + Background Jobs | `AuditLogService`: hook vào Spring AOP, ghi mọi thao tác update/delete vào `audit_logs` (before/after JSON). Jobs: (1) `HoldExpiryJob`: scan PENDING bookings where `hold_expires_at < now()` → chuyển EXPIRED. (2) `NoShowJob`: scan CONFIRMED bookings where `check_in_date < today()` and not checked in → chuyển NO_SHOW, tính penalty từ `no_show_charge_percent`. (3) `ExpiredTokenCleanupJob`: xóa `auth_tokens` hết hạn quá 30 ngày. | Normal | 25/08 | 25/08 | 4 |

### Frontend

| # | Subject | Description | Priority | Start | Due | Est. (h) |
|---|---------|-------------|----------|-------|-----|----------|
| FE-8.1 | Admin — Shift Management Page | Trang quản lý ca trực (Admin): calendar view (tuần × staff, cell = ca được gán). Drag-drop gán staff vào ca. Modal tạo ca mới (MORNING/AFTERNOON/NIGHT với giờ). Modal phân công: chọn ngày, chọn staff, chọn ca. Badge màu theo trạng thái (SCHEDULED=xanh, COMPLETED=cam, ABSENT=đỏ, CANCELLED=xám). Validate: không gán 2 ca overlap. | High | 25/08 | 25/08 | 5 |
| FE-8.2 | Customer — Review Submission | Trang "Viết đánh giá" (chỉ hiện sau CHECKED_OUT): form stars cho từng category, title, comment. Preview trước khi gửi. Sau gửi: "Cảm ơn bạn đã đánh giá" + link về My Bookings. | Normal | 25/08 | 25/08 | 3 |
| FE-8.3 | Admin — Review Moderation | Trang duyệt đánh giá: danh sách reviews (filter: status, room type, rating). Expand: xem chi tiết, nội dung. Actions: "Phê duyệt" (→ PUBLISHED), "Ẩn" (→ HIDDEN), "Từ chối" (→ REJECTED, có lý do). Reply: nhập phản hồi từ khách sạn. | Normal | 25/08 | 25/08 | 3 |
| FE-8.4 | Staff — Shift Schedule View | Trang lịch ca của Staff: hiển thị ca được phân công (tuần hiện tại + tuần tới). Badge: ngày, ca, trạng thái. Nút "Đánh dấu hoàn thành ca" (khi hết ca → COMPLETED). Nút "Báo vắng" (→ ABSENT, có ghi chú). | Normal | 25/08 | 25/08 | 3 |
| FE-8.5 | Polish — Responsive, Error Handling, SEO | Audit tất cả pages: responsive (mobile sidebar → drawer, table → card list). Error boundaries cho Next.js. Loading skeletons. Toast notifications cho mọi action. Metadata cho SEO (room type pages). Accessibility: aria labels, keyboard navigation. | Normal | 25/08 | 25/08 | 4 |

**Checkpoint cuối ngày 8:** Full system smoke test — tạo account → đặt phòng → thanh toán → check-in → phát sinh dịch vụ → checkout → xuất hóa đơn → gửi review → xem dashboard doanh thu → phân công ca → gửi email xác nhận.

---

## Tổng kết — Deliverables cuối cùng

| Phase | Ngày | Backend deliverables | Frontend deliverables |
|-------|------|----------------------|-----------------------|
| **Foundation** | 1 | Docker compose, Spring Boot project, 39 JPA entities, Flyway migration (MySQL) | Next.js project, base UI components |
| **Auth & Users** | 2 | JWT auth, RBAC (15+ permissions), User/Staff/Customer CRUD, Shift management | Login, Register, Email verification, Profile |
| **Inventory** | 3 | RoomType, Room, Amenity CRUD, Room blocks (BR-003/BR-004), HK status | Admin Room Management + Floor map + Maintenance scheduling |
| **Pricing** | 4 | Rate engine, Cancellation policies (multi-tier), Booking price calculator | Admin Pricing + Policies + Customer room listing + date picker |
| **Booking** | 5 | Availability engine, Booking creation (snapshot pricing), State machine, Room assignment, Guest ID storage | Customer booking wizard + My Bookings + Staff booking management |
| **Billing** | 6 | Folio charges, Invoice generation (DRAFT→ISSUED→VOID), PDF generation (MinIO), Hotel settings | Staff folio panel + Invoice issuance + Customer invoice view |
| **Payment** | 7 | Payment gateway (VNPay/MoMo stub), Callback + verification, Refund engine, Revenue queries | Payment UI + Staff payment management + Dashboard + Revenue reports |
| **Polish** | 8 | Shift calendar API, Reviews (BR-006/BR-007), Email queue + templates, Audit logs + Jobs | Shift management + Reviews + Polish + Responsive + Accessibility |

**Kiến trúc MySQL thay thế PostgreSQL:**

| PostgreSQL feature | MySQL replacement |
|---|---|
| `EXCLUDE USING gist` (BR-002) | Trigger `BEFORE INSERT/UPDATE` kiểm tra overlap query |
| `EXCLUDE` BR-015 (shift) | Trigger kiểm tra `shift_period` overlap |
| `daterange` / `tstzrange` | 2 cột DATE/TIMESTAMP, query `[)` bằng `WHERE a_in < b_out AND a_out > b_in` |
| `CITEXT` | `VARCHAR` + `LOWER()` ở application layer hoặc generated column |
| `JSONB` | `JSON` (MySQL 8+) |
| `UUID` | `CHAR(36)` hoặc `BINARY(16)` |
| `GENERATED COLUMN` | MySQL supports `GENERATED ALWAYS AS` — dùng cho `nights`, `stay_range` |
| GiST index | Trigger-based overlap check (performance acceptable cho < 10k concurrent bookings) |

**MinIO integration points:** Room images, Room type images, Invoice PDFs, User avatars. Bucket naming: `room-images`, `room-type-images`, `invoices`, `avatars`. Presigned URLs với TTL 1 giờ cho upload, 15 phút cho download.

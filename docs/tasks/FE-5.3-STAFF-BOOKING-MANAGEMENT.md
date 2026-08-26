# FE-5.3: Staff — Booking Management

## Task Overview

**Yêu cầu:** Trang quản lý đặt phòng dành cho Staff với đầy đủ chức năng CRUD và các thao tác nghiệp vụ.

## Requirements

### 1. Booking List (Trang chính)
- Bảng tất cả booking
- **Filters:**
  - Trạng thái: PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW
  - Ngày đến: checkInFrom, checkInTo
  - Ngày đi: checkOutFrom, checkOutTo
  - Nguồn: WEBSITE, WALK_IN, PHONE, BOOKING_COM, AGODA, STAFF_MANUAL
  - Tìm kiếm: booking_code, tên khách, SĐT
- Pagination
- Stats cards: Tổng đơn, Chờ xử lý, Đã xác nhận, Đang ở, Đã hoàn thành

### 2. Booking Detail Drawer
Hiển thị đầy đủ thông tin trong các tabs:

#### Tab: Thông tin chung
- Mã đặt phòng, trạng thái, nguồn
- Thông tin khách hàng: tên, email, SĐT
- Số khách: adults, children
- Ngày đến/đi
- Yêu cầu đặc biệt

#### Tab: Phòng
- Danh sách booking_rooms với thông tin:
  - Số phòng (nếu đã gán)
  - Loại phòng
  - Ngày check-in/check-out
  - Số đêm
  - Giá từng đêm (từ booking_room_nights)
  - Tổng tiền phòng
  - Trạng thái phòng: RESERVED, OCCUPIED, COMPLETED, RELEASED, MOVED_OUT
  - Chính sách hủy (từ cancellation_policy_snapshot)
  - Nút "Gán phòng" (nếu chưa gán)

#### Tab: Khách lưu trú
- Danh sách booking_guests
- Thông tin: tên, quốc tịch
- CCCD (chỉ hiển thị nếu có quyền guest:read_id)

#### Tab: Dịch vụ (Folio)
- Danh sách folio_charges
- Thông tin: mã dịch vụ, mô tả, số lượng, đơn giá, thành tiền
- Trạng thái: bình thường / đã hủy (void)
- Nút "Thêm khoản phát sinh"

#### Tab: Thanh toán
- Danh sách payments
- Thông tin: mã thanh toán, phương thức, số tiền, trạng thái, thời gian
- Tổng: đã thanh toán, đã hoàn, còn lại

#### Tab: Hóa đơn
- Thông tin invoice
- Danh sách invoice_items
- Nút "Xuất hóa đơn" (nếu chưa có)

#### Tab: Lịch sử
- Timeline từ booking_status_history
- Thông tin: từ trạng thái → sang trạng thái, thời gian, người thực hiện, lý do

### 3. Staff Actions

#### 3.1. Xác nhận booking (Confirm)
- Endpoint: `POST /api/bookings/{publicId}/confirm`
- Điều kiện: booking PENDING
- Hành động: Chuyển PENDING → CONFIRMED
- Nếu booking chưa thanh toán, đây là luồng xác nhận thu tiền mặt: backend tự tạo payment `CASH`
  đã xác minh trong payment ledger rồi mới chuyển booking sang `CONFIRMED`.
- Ghi `booking_status_history.source = MANUAL`. Thanh toán online chỉ tự xác nhận khi payment
  `SUCCEEDED`, có `verified_at` hợp lệ và tổng tiền đã nhận đủ `total_amount`.

#### 3.2. Gán phòng (Assign Room)
- Modal chọn phòng kiểu "chọn ghế rạp chiếu phim"
- **Filters:**
  - Tầng: dropdown các tầng có phòng
  - Trạng thái HK: CLEAN, DIRTY, CLEANING
  - Loại phòng: filter theo loại phòng của booking_room
  - View: SEA, CITY, GARDEN, POOL, MOUNTAIN, NONE
- **Grid view:**
  - Mỗi phòng là 1 "ghế" có màu theo trạng thái HK
  - Màu: CLEAN=xanh, DIRTY=đỏ, CLEANING=cam
  - Click chọn phòng
  - Hiển thị số phòng, loại phòng, view type
- **Endpoint:** `POST /api/bookings/{publicId}/rooms/{bookingRoomId}/assign`
- **Request body:** `{ "roomId": 123 }`

#### 3.3. Check-in
- Endpoint: `POST /api/bookings/{publicId}/check-in`
- Điều kiện: booking CONFIRMED, tất cả rooms đã được gán
- Hành động: Chuyển CONFIRMED → CHECKED_IN
- Staff/Admin check-in qua `/api/admin/bookings/{publicId}/check-in` có thể gửi danh sách guest theo từng `bookingRoomId`.
- Booking Staff tạo tại quầy đã có guest hợp lệ từ bước tạo; nút xác nhận sẽ tự thu tiền mặt nếu cần, sau đó chuyển tiếp `PENDING → CONFIRMED → CHECKED_IN`.
- Booking online thay thế guest placeholder bằng danh sách khách thực tế trước khi check-in; `checked_in_at`, `checked_in_by`, `booking_status_history` và trạng thái `booking_rooms` phải được cập nhật trong cùng transaction.

#### 3.4. Check-out
- Endpoint: `POST /api/bookings/{publicId}/check-out`
- Điều kiện: booking CHECKED_IN
- Hành động: Chuyển CHECKED_IN → CHECKED_OUT

#### 3.5. Hủy booking (Cancel)
- Modal với lý do hủy (bắt buộc)
- Endpoint: `POST /api/bookings/{publicId}/cancel`
- Request body: `{ "reason": "..." }`
- Điều kiện: booking PENDING hoặc CONFIRMED

#### 3.6. Tạo booking mới
- Endpoint: `POST /api/admin/bookings`
- Permission: `booking:create_staff`
- Form tương tự customer booking wizard nhưng Staff/Admin chọn trực tiếp số phòng trên booking map.
- Booking map: `GET /api/admin/rooms/booking-map?checkInDate=&checkOutDate=` với permission `room:booking_map:read`.
- Phòng chỉ được chọn khi `CLEAN`, `ACTIVE`, không overlap booking hoặc room status block.
- Booking tạo từ Staff/Admin dùng source `STAFF_MANUAL`, trạng thái ban đầu `PENDING` và giữ phòng theo hold hiện tại.
- Flow tạo booking gồm 2 bước: chọn phòng trước, sau đó nhập contact và danh sách khách theo từng phòng. Booking Staff/Admin mặc định dùng `NON_REFUND`; `contactPhone` bắt buộc; khách chính và khách phụ đều phải có họ tên, loại giấy tờ và số giấy tờ, còn quốc tịch/ngày sinh là tùy chọn. `guestCount` hiện được tính là số người lớn vì chưa hỗ trợ trẻ em.
- Chi tiết contract được mô tả tại `docs/flows/be_5_4_staff_booking_room_timeline.md` và giao diện tại `docs/flows/fe_5_4_staff_booking_room_selection.md`.

---

## Backend Prerequisites

### Đã có ✅
1. `GET /api/bookings/me` - Lấy bookings của customer
2. `GET /api/bookings/{publicId}` - Chi tiết 1 booking
3. `POST /api/bookings/{publicId}/check-in` - Check-in
4. `POST /api/bookings/{publicId}/check-out` - Check-out
5. `POST /api/bookings/{publicId}/cancel` - Hủy
6. `POST /api/bookings/{publicId}/rooms/{id}/assign` - Gán phòng
7. `POST /api/bookings` - Tạo booking customer
8. `POST /api/admin/bookings` - Tạo booking Staff/Admin với phòng cụ thể
9. `RoomService.getRooms()` - Lấy phòng với filters
10. `RoomRepository.findAvailableRooms()` - Tìm phòng trống

### Cần tạo/thêm 🔧

#### BE-5.3.1: API List All Bookings
```
GET /api/bookings
  ?status=PENDING|CONFIRMED|...
  &checkInFrom=2026-08-01
  &checkInTo=2026-08-31
  &checkOutFrom=...
  &checkOutTo=...
  &source=BOOKING_COM|AGODA|...
  &search=keyword
  &page=0
  &size=20
```
- Response: Paginated list với summary
- Permission: `booking:read_any` hoặc `booking:check_in`

**Files cần tạo:**
- `BookingController.java` - Thêm endpoint `GET /`
- `BookingService.java` - Method `getAllBookings(filters)`
- `BookingRepository.java` - Query với filters
- DTOs:
  - `BookingListFilterRequest.java`
  - `BookingListItemResponse.java`
  - `BookingListResponse.java` (paginated)

#### BE-5.3.2: RBAC Update
- Thêm permission `booking:read_any`
- Gán cho Staff role
- Kiểm tra Staff có đủ permissions:
  - `booking:check_in` ✅
  - `booking:check_out` ✅
  - `booking:assign_room` ✅
  - `booking:cancel_any` ⚠️ Cần kiểm tra
  - `booking:read_any` ❌ Cần tạo

#### BE-5.3.3: Mở rộng Booking Detail cho Staff
Cần trả về thêm thông tin:
- Folio charges
- Payments
- Invoices
- Booking guests

**Có 2 cách:**
1. Mở rộng `BookingDetailResponse` hiện tại (thêm fields)
2. Tạo endpoint riêng `GET /api/bookings/{publicId}/staff-detail`

**Đề xuất:** Cách 2 - tách riêng vì customer và staff cần data khác nhau

#### BE-5.3.4: API Assign Room với Room Filters
Endpoint hiện tại đã có nhưng cần API để lấy danh sách phòng có thể gán:
```
GET /api/bookings/{publicId}/rooms/{bookingRoomId}/available-rooms
  ?floor=1
  &housekeepingStatus=CLEAN
  &roomTypeCode=DLX
```
- Response: List rooms phù hợp với booking_room dates
- Filter theo:
  - Tầng
  - Housekeeping status
  - Room type (phải match với booking_room)
  - View type
  - Operational status = ACTIVE
  - Không có booking overlap

#### BE-5.3.5: Confirm Booking API
```
POST /api/bookings/{publicId}/confirm
```
- Chuyển PENDING → CONFIRMED
- Tự động gửi email xác nhận (nếu có email service)

---

## Frontend Implementation

### Files cần tạo/sửa

```
frontend/
├── app/admin/bookings/
│   └── page.tsx                    # Sửa: kết nối API, thêm filters
├── components/
│   ├── booking/
│   │   ├── BookingTable.tsx        # Component bảng booking
│   │   ├── BookingFilters.tsx      # Component filters
│   │   ├── BookingDetailDrawer.tsx # Drawer chi tiết
│   │   ├── BookingRoomTab.tsx      # Tab phòng
│   │   ├── BookingGuestTab.tsx      # Tab khách lưu trú
│   │   ├── BookingFolioTab.tsx      # Tab dịch vụ
│   │   ├── BookingPaymentTab.tsx    # Tab thanh toán
│   │   ├── BookingInvoiceTab.tsx    # Tab hóa đơn
│   │   ├── BookingHistoryTab.tsx    # Tab lịch sử
│   │   ├── RoomAssignmentModal.tsx  # Modal gán phòng (chọn ghế)
│   │   ├── RoomGrid.tsx             # Grid phòng kiểu rạp phim
│   │   ├── RoomSeat.tsx            # Component "ghế" phòng
│   │   └── BookingActions.tsx      # Các nút hành động
│   └── ui/
│       └── timeline.tsx            # Component timeline (cho history)
├── lib/
│   └── api/
│       └── booking-api.ts          # API client functions
├── types/
│   └── booking.ts                  # Mở rộng types
```

### Component Details

#### RoomAssignmentModal (Chọn ghế rạp phim)
- Header: "Chọn phòng cho {bookingRoomTypeName}"
- Filters sidebar:
  - Dropdown Tầng
  - Dropdown Trạng thái HK
  - Dropdown View
- Main area: Grid 2D
  - Mỗi cell = 1 phòng
  - Màu theo housekeeping status:
    - CLEAN: bg-green-100, border-green-500
    - DIRTY: bg-red-100, border-red-500
    - CLEANING: bg-orange-100, border-orange-500
  - Hover: hiện tooltip với thông tin phòng
  - Selected: border-primary, ring
  - Occupied: grayed out, cursor-not-allowed
- Footer:
  - Selected room info
  - Buttons: Hủy, Gán phòng

#### BookingDetailDrawer
- Width: 800px
- Tabs: Thông tin | Phòng | Khách | Dịch vụ | Thanh toán | Hóa đơn | Lịch sử
- Header: Mã booking, status badge, action buttons
- Action buttons sticky ở bottom:
  - Confirm (nếu PENDING)
  - Check-in (nếu CONFIRMED + đã gán phòng)
  - Check-out (nếu CHECKED_IN)
  - Cancel (nếu PENDING/CONFIRMED)

---

## UI/UX Design

### Color Palette cho Room Grid
| Status | Background | Border | Text |
|--------|------------|--------|------|
| CLEAN | green-50 | green-500 | green-800 |
| DIRTY | red-50 | red-500 | red-800 |
| CLEANING | orange-50 | orange-500 | orange-800 |
| Selected | primary-100 | primary-500 | primary-900 |
| Disabled | gray-100 | gray-300 | gray-400 |

### Booking Status Colors (Badge)
| Status | Variant | Label |
|--------|---------|-------|
| PENDING | warning | Chờ xử lý |
| CONFIRMED | info | Đã xác nhận |
| CHECKED_IN | success | Đã nhận phòng |
| CHECKED_OUT | secondary | Đã trả phòng |
| CANCELLED | destructive | Đã hủy |
| NO_SHOW | outline | Không đến |

---

## Dependencies

### Backend
- Spring Boot Security
- JPA Repository với Specifications
- Permission checks

### Frontend
- React Query / SWR cho data fetching
- shadcn/ui components
- Tailwind CSS
- date-fns cho date formatting
- zustand hoặc React Context cho state

---

## Implementation Order

### Phase 1: Backend (2-3h)
1. Tạo `GET /api/bookings` với filters
2. Tạo `GET /api/bookings/{publicId}/staff-detail`
3. Tạo `GET /api/bookings/{publicId}/rooms/{id}/available-rooms`
4. Thêm permission `booking:read_any`
5. Test APIs

### Phase 2: Frontend - List Page (2h)
1. Kết nối booking list với API
2. Implement filters
3. Stats cards với real data
4. Pagination

### Phase 3: Frontend - Detail Drawer (3h)
1. Tạo BookingDetailDrawer component
2. Implement all tabs
3. Connect với API

### Phase 4: Frontend - Actions (2h)
1. RoomAssignmentModal với room grid
2. Check-in, Check-out, Cancel actions
3. Confirm action
4. Create booking form

---

## Estimates

- Backend APIs: 3-4h
- Frontend List Page: 2h
- Frontend Detail Drawer: 3h
- Frontend Actions: 2h
- **Total: 10-11h** (vượt 5h estimate ban đầu)

---

## Notes

1. Room assignment grid cần responsive - trên mobile có thể hiển thị list thay vì grid
2. Cần xử lý loading states cho từng tab riêng
3. Error handling cho từng action riêng
4. Toast notifications cho success/error
5. Confirm dialog trước khi check-in/check-out/cancel

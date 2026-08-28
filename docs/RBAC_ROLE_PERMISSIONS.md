# Manage Hotel — Quyền Customer, Staff và Admin

Tài liệu này mô tả permission RBAC hiện tại theo migration database và cách các quyền được sử dụng ở API/FE.

## 1. Cách đọc tài liệu

Để tránh lặp lại:

- **Customer**: liệt kê nhóm quyền dành cho Customer.
- **Staff**: liệt kê các quyền vận hành Staff có thêm/được cấp trong hệ thống.
- **Admin**: mặc định có toàn bộ quyền của Staff; bên dưới chỉ liệt kê các quyền Admin được bổ sung so với Staff.

Admin được cấp toàn bộ permission nền ở migration baseline và các permission Admin được thêm ở những migration sau. Đây là cách trình bày rút gọn trong tài liệu; database vẫn lưu từng bản ghi `role_permissions` riêng, không phải cơ chế kế thừa role động.

## 2. Customer

### Quyền được cấp

| Permission | Ý nghĩa |
| --- | --- |
| `booking:create` | Tạo booking Customer và sử dụng các API tìm/tính giá phục vụ booking |
| `booking:read_own` | Xem booking của chính mình |
| `booking:cancel_own` | Hủy booking của chính mình |
| `review:create` | Gửi review sau khi booking đã `CHECKED_OUT` |
| `room:read` | Xem dữ liệu phòng/Room Type được phép dùng trong luồng booking |

### Chức năng Customer có thể thực hiện

- Đăng ký, xác thực email, đăng nhập, refresh token, logout.
- Yêu cầu reset password và đặt lại password.
- Đăng nhập Google OAuth.
- Cập nhật Customer profile và avatar của chính mình.
- Tìm phòng theo ngày và số người lớn.
- Xem Room Type, ảnh, giường, sức chứa, amenities và giá theo đêm.
- Tạo booking online, giữ phòng tạm thời và thanh toán online/QR.
- Xem danh sách và chi tiết booking của mình.
- Xem lịch sử booking/phòng đã ở của mình.
- Hủy booking của mình khi booking đang ở trạng thái cho phép hủy.
- Xem trạng thái thanh toán, refund và invoice của booking của mình.
- Xem invoice đã phát hành và tải PDF ở chế độ read-only.
- Gửi một review cho booking sau checkout, gồm overall rating, category rating, title và comment.

### Customer không có

- Không xem booking của Customer khác.
- Không có đăng nhập social bằng Facebook hoặc Twitter; hệ thống hiện chỉ có Google OAuth.
- Không check-in/check-out, assign phòng hoặc xem giấy tờ lưu trú nội bộ.
- Không quản lý phòng, Room Type, amenities, giá, maintenance, Staff, Customer account hay ca trực.
- Không moderation hoặc reply review của khách khác.
- Không verify cash, approve refund, issue/void invoice hoặc compose email cho booking khác.

## 3. Staff

Staff **không được lặp lại danh sách quyền của Customer** vì Staff không nhận nhóm permission Customer như `booking:create`, `booking:read_own`, `booking:cancel_own`, `review:create`. Staff xử lý nghiệp vụ back-office bằng các quyền dưới đây.

### Quyền Staff hiện có

| Permission | Ý nghĩa và phạm vi |
| --- | --- |
| `room:read` | Xem danh sách phòng, Room Type và dữ liệu inventory được phép đọc |
| `booking:read_any` | Xem booking trong phạm vi vận hành |
| `booking:cancel_any` | Hủy booking thay mặt khách khi nghiệp vụ cho phép |
| `booking:check_in` | Xác nhận booking Staff và check-in booking |
| `booking:check_out` | Check-out booking |
| `booking:assign_room` | Gán/đổi phòng và quản lý thông tin guest phục vụ check-in |
| `guest:read_id` | Xem giấy tờ tùy thân theo quyền nghiệp vụ |
| `payment:manage` | Xem/quản lý payment và verify cash theo flow hiện có |
| `invoice:issue` | Xem/chỉnh DRAFT, thêm adjustment, issue invoice và tải PDF |
| `invoice:void` | Void invoice đã phát hành theo quy trình |
| `email:send` | Gửi email tới booking contact và xem lịch sử gửi |
| `review:reply` | Xem review và tạo/cập nhật staff reply |
| `dashboard:read` | Permission API xem dashboard overview; menu Dashboard hiện được ẩn khỏi Staff |
| `shift:read_own` | Xem lịch ca của chính mình |
| `shift:update_own` | Đánh dấu ca hoàn thành hoặc báo vắng ca của chính mình |
| `maintenance:manage` | Tạo, kéo dài và hủy room status block nếu được cấp quyền |
| `room:housekeeping:update` | Chuyển housekeeping status của phòng |
| `room:occupancy:read` | Xem trạng thái booking occupancy của phòng |
| `booking:create_staff` | Tạo booking trực tiếp cho khách từ Staff portal |
| `room:booking_map:read` | Xem booking map/timeline để chọn phòng cho booking Staff |

### Chức năng Staff có thể thực hiện

#### Booking và lưu trú

- Xem danh sách booking, tìm kiếm/lọc và mở booking detail.
- Tạo booking trực tiếp bằng số phòng từ `/manager/bookings`.
- Xem giá từng đêm, tổng giá, beds, sức chứa, booking event và maintenance event.
- Nhập contact và danh sách khách lưu trú, gồm thông tin CCCD/hộ chiếu.
- Tạo thanh toán cho booking Staff bằng cash, online hoặc Mock Wallet.
- Xác nhận booking chưa thanh toán như một giao dịch tiền mặt; backend tạo và verify payment `CASH`.
- Check-in booking Staff ngay sau khi xác nhận.
- Check-in booking Customer đã thanh toán, nhập guest thực tế theo từng phòng.
- Assign hoặc change room.
- Hủy booking theo quyền `booking:cancel_any`.
- Thêm/void Folio charge, issue/void invoice và gửi email cho khách.
- Check-out booking, ghi nhận thời điểm/người thực hiện và tạo invoice DRAFT.

#### Phòng và vận hành

- Xem danh sách phòng, Room Type, tầng, view, operational status, housekeeping status.
- Xem sơ đồ tầng và occupancy `HELD`, `RESERVED`, `OCCUPIED`.
- Chuyển housekeeping theo chu trình bắt buộc:

```text
CLEAN → DIRTY → CLEANING → CLEAN
```

- Xem/tạo/kéo dài/hủy lịch maintenance nếu có `maintenance:manage`.
- Xem booking map/timeline theo khoảng ngày để tránh chọn phòng đã đặt hoặc bị block.

#### Review và ca trực

- Xem review, lọc theo status/Room Type/rating.
- Reply review; Staff không approve, hide hoặc reject review.
- Xem lịch ca của mình trong tuần hiện tại và tuần kế tiếp.
- Đánh dấu ca hoàn thành sau khi ca kết thúc.
- Báo vắng kèm lý do.

### Staff không có

- Không tạo/sửa/xóa phòng. Đặc biệt Staff không có `room:create` và `room:update`.
- Không tạo/sửa/xóa Room Type, beds, amenities hoặc rate override.
- Không quản lý Customer account.
- Không hire, edit, terminate hoặc reset password Staff khác.
- Không quản lý định nghĩa ca hoặc phân assignment cho Staff khác.
- Không moderation review (`review:moderate`).
- Không quản lý RBAC, hotel settings, audit hoặc báo cáo doanh thu theo permission hiện tại.
- Không có menu Dashboard trong FE hiện tại dù token Staff vẫn có `dashboard:read` để phục vụ API overview.

## 4. Admin

Admin có **toàn bộ quyền Staff** và các quyền Customer/base permission ở tầng database. Phần này chỉ liệt kê các quyền Admin bổ sung, không lặp lại danh sách Staff.

### Quyền Admin bổ sung so với Staff

| Permission | Ý nghĩa và phạm vi |
| --- | --- |
| `booking:create` | Permission booking Customer/base; Admin có trong role nhưng flow `/manager` dùng `booking:create_staff` cho booking tại quầy |
| `booking:read_own` | Permission base xem booking theo owner; không phải quyền xem toàn bộ booking vận hành |
| `booking:cancel_own` | Permission base hủy booking theo owner |
| `review:create` | Permission base tạo review; không phải action moderation trong Manager |
| `room:create` | Tạo phòng mới |
| `room:update` | Sửa thông tin inventory và operational status |
| `room:delete` | Soft-delete phòng |
| `pricing:manage` | Tạo/quản lý rate override |
| `policy:manage` | Xem và quản lý cancellation policy |
| `refund:approve` | Approve/complete refund; flow trạng thái là `PENDING → PROCESSING → COMPLETED` |
| `review:moderate` | Approve, hide, reject và xử lý lại review |
| `staff:manage` | Hire, edit, đổi trạng thái, reset password và resend Staff invitation |
| `shift:manage` | Tạo/sửa/vô hiệu hóa ca và phân công Staff |
| `rbac:read` | Xem role và permission |
| `rbac:manage` | Quản lý permission của role không phải Admin theo RBAC service |
| `settings:manage` | Đọc/cập nhật hotel settings |
| `revenue:read` | Xem báo cáo occupancy/doanh thu |
| `audit:read` | Quyền đọc audit log được seed trong hệ thống |

### Chức năng Admin bổ sung

#### Inventory và pricing

- Tạo/sửa/soft-delete phòng.
- Cập nhật Room Type, view, tầng, giá riêng, operational status và dữ liệu inventory.
- Tạo/sửa/soft-delete Room Type, cấu hình beds, amenities và ảnh MinIO.
- Tạo rate override theo Room Type, weekday, khoảng ngày và priority.
- Xem lịch giá và giá hiệu lực từng ngày.
- Quản lý cancellation policy theo API/UI hiện có.

#### Nhân sự và phân quyền

- Tạo tài khoản Staff mới qua invitation email.
- Sửa thông tin Staff, gồm SĐT, chức danh, phòng ban và lương.
- Chuyển Staff giữa `ACTIVE`, `ON_LEAVE`, `TERMINATED`.
- Reset password và gửi lại invitation.
- Tạo/sửa/vô hiệu hóa ca trực.
- Phân công Staff theo ngày và xử lý assignment.
- Xem role/permission và cập nhật permission role theo RBAC rules.

#### Customer và review

- Xem danh sách Customer, tìm/lọc và mở Customer detail.
- Xem lịch sử booking Customer ở chế độ read-only.
- Deactivate hoặc activate lại Customer account.
- Approve review → `PUBLISHED`.
- Hide review → `HIDDEN`.
- Reject review → `REJECTED`, bắt buộc moderation reason.
- Reply review như Staff.

#### Báo cáo và cài đặt

- Xem Dashboard overview: booking đến/đi, phòng trống, occupancy, doanh thu tháng.
- Xem báo cáo occupancy, doanh thu ngày/tháng, theo source, theo Room Type và OTA commission.
- Export CSV báo cáo doanh thu.
- Đọc/cập nhật hotel billing/settings theo API permission.

## 5. Quyền không đồng nghĩa với menu FE

Có hai lớp kiểm soát:

1. **Backend:** `@PreAuthorize`, service authorization và kiểm tra nghiệp vụ là lớp bảo vệ bắt buộc.
2. **Frontend:** `ManagerLayout` lọc menu theo role/permission và route Staff bị redirect nếu không được phép.

Vì vậy:

- Staff có thể có một permission API nhưng không thấy menu tương ứng nếu UI chưa mở chức năng đó, ví dụ `dashboard:read`.
- Có permission xem Phòng không có nghĩa là Staff được sửa Phòng; sửa cần `room:update`, và permission này đã bị gỡ khỏi Staff ở migration `V36`.
- Có `review:reply` không có nghĩa là Staff được moderation; moderation cần `review:moderate`, chỉ Admin có.
- Có `invoice:issue` không đồng nghĩa được void invoice; void cần thêm `invoice:void`.
- Có quyền xem booking không đồng nghĩa được xem dữ liệu giấy tờ; giấy tờ cần `guest:read_id`.

## 6. Lưu ý khi kiểm tra quyền sau migration

- Permission được đưa vào JWT/user summary khi đăng nhập. Sau khi thay đổi role permission trong database, nên logout/login lại để lấy token mới.
- Không nên chỉ kiểm tra việc menu có hiển thị hay không; cần gọi endpoint bằng đúng account để xác nhận `401/403` và business validation.
- Các role là độc lập: Customer không được chuyển thành Staff. Admin tạo User + StaffProfile mới qua invitation.
- Staff profile cá nhân dùng role `STAFF`; Customer profile cá nhân dùng role `CUSTOMER`.

## 7. Nguồn đối chiếu

- `backend/src/main/resources/db/migration/V1__baseline_schema.sql`
- `backend/src/main/resources/db/migration/V6__add_rbac_permissions.sql`
- `backend/src/main/resources/db/migration/V15__add_hotel_settings_permission.sql`
- `backend/src/main/resources/db/migration/V21__add_revenue_read_permission.sql`
- `backend/src/main/resources/db/migration/V27__add_review_reply_permission.sql`
- `backend/src/main/resources/db/migration/V28__add_dashboard_read_permission.sql`
- `backend/src/main/resources/db/migration/V34__add_staff_own_shift_permissions.sql`
- `backend/src/main/resources/db/migration/V35__grant_admin_staff_shift_permissions.sql`
- `backend/src/main/resources/db/migration/V36__separate_staff_maintenance_permission.sql`
- `backend/src/main/resources/db/migration/V39__remove_inspected_housekeeping_and_add_room_status_permissions.sql`
- `backend/src/main/resources/db/migration/V40__add_staff_booking_permissions.sql`
- `backend/src/main/java/com/example/hotelmanagement/security/PermissionExpressions.java`

# Manage Hotel — Thứ tự demo chức năng

Tài liệu này là kịch bản demo các chức năng đang có trong project Manage Hotel/TripStay. Thứ tự được sắp từ đăng nhập, tìm phòng, đặt phòng, thanh toán, lưu trú, checkout đến vận hành của Staff và Admin.

## 1. Quy ước và phạm vi

| Ký hiệu | Ý nghĩa                                               |
| --------- | ------------------------------------------------------- |
| ✅        | Đã có luồng FE + BE và có thể demo               |
| ⚠️      | Có một phần implementation, cần nói rõ giới hạn |
| ⛔        | Chưa có trong implementation hiện tại               |

Khu vực giao diện:

- Customer: `/`, `/booking`, `/profile`, `/profile/bookings`.
- Staff/Admin: dùng chung `/manager`, đăng nhập tại `/manager/login`.
- Staff và Admin không dùng hai bộ trang lặp lại. Backend vẫn kiểm tra permission; frontend chỉ hiển thị menu/action phù hợp role.

## 2. Chuẩn bị trước khi demo

### Môi trường

- Khởi động MySQL, Redis, MinIO, backend Spring Boot và frontend Next.js.
- Kiểm tra Flyway migrate thành công.
- Chuẩn bị email dev để xem email xác thực, reset password, invitation và email nghiệp vụ trong log/provider.
- Nếu demo thanh toán local, bật Mock Wallet. Nếu demo Internet Banking thật, chuẩn bị SePay sandbox và callback URL.

### Tài khoản

Chuẩn bị:

1. Customer đã xác thực email.
2. Staff đang `ACTIVE`.
3. Admin đang `ACTIVE`.

Nên có các booking riêng:

- Booking online đã thanh toán, `CONFIRMED`, để Staff check-in.
- Booking đã `CHECKED_OUT`, để Customer gửi review.
- Một booking Staff tại quầy để demo thu tiền mặt/check-in ngay.

Nếu demo refund, dùng booking Customer đã thanh toán và có policy cho phép hoàn tiền. Booking Staff mặc định `NON_REFUND`, nên thường không dùng booking này để minh họa refund.

### Dữ liệu

- Room Type có giá, mô tả, sức chứa, beds, amenities và ảnh.
- Nhiều phòng ở các tầng với housekeeping `CLEAN`, `DIRTY`, `CLEANING`.
- Phòng có booking `PENDING/HELD`, `CONFIRMED/RESERVED`, `CHECKED_IN/OCCUPIED`.
- Một maintenance block, một rate override, các ca trực và assignment.
- Service item như minibar/laundry để demo Folio.

## 3. Thứ tự demo tổng thể

```text
1. Authentication và phân quyền
2. Customer tìm phòng và xem giá
3. Customer tạo booking và thanh toán
4. Customer xem/quản lý booking
5. Staff xem booking, check-in, Folio và checkout
6. Staff/Admin quản lý phòng, sơ đồ tầng và trạng thái phòng
7. Admin quản lý Room Type, giá và lịch bảo trì
8. Admin quản lý Customer và Staff
9. Staff/Admin xử lý review
10. Ca trực và lịch làm việc
11. Payment, refund, invoice và email
12. Dashboard và báo cáo
```

---

## 4. Luồng 1 — Authentication và phân quyền

### Customer đăng ký và xác thực email — ✅

1. Mở `/register`, nhập họ tên, email, số điện thoại và mật khẩu.
2. Submit; hệ thống tạo Customer chờ xác thực và gửi email verification.
3. Mở link/token tại `/verify-email`.
4. Đăng nhập tại `/login` và kiểm tra Customer được đưa về khu vực Customer.
5. Customer không nhìn thấy menu Manager.

API: `POST /api/auth/register`, `POST /api/auth/verify-email`, `POST /api/auth/verify-email/resend`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/	auth/logout`.

### Quên và reset password — ✅

1. Tại `/forgot-password`, nhập email.
2. Mở email reset và đặt mật khẩu mới tại `/reset-password`.
3. Đăng nhập lại bằng mật khẩu mới.
4. Có thể minh họa thêm cơ chế khóa tạm sau nhiều lần login sai.

API: `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`.

### OAuth — ⚠️

- Google OAuth đã có authorize/callback và liên kết tài khoản social.
- Facebook và Twitter hiện chưa có controller/service/frontend flow tương ứng; không nên giới thiệu là đã hoàn thiện.

API Google: `/api/auth/oauth/google/authorize`, `/api/auth/oauth/google/callback`.

### Staff/Admin login và RBAC — ✅

1. Mở `/manager/login` bằng Staff.
2. Staff chỉ thấy các menu được cấp quyền: Đặt phòng, Thanh toán, Phòng, Loại phòng, Đánh giá, Lịch ca và Hồ sơ.
3. Staff không thấy Dashboard, Nhân viên, Khách hàng, Quản lý giá, Chính sách hủy hoặc Báo cáo nếu không có permission.
4. Staff chỉ xem Phòng/Loại phòng, không tạo/sửa/xóa inventory.
5. Đăng xuất, đăng nhập Admin và kiểm tra các menu/action quản trị xuất hiện.
6. Có thể truy cập thử route bị giới hạn để chứng minh backend cũng trả `403`/frontend redirect, không chỉ ẩn menu.

---

## 5. Luồng 2 — Customer tìm phòng và xem giá

### Tìm theo ngày và sức chứa — ✅

1. Mở `/booking`.
2. Chọn ngày nhận, ngày trả và số người lớn tối thiểu.
3. Bấm **Tìm kiếm**. Việc chọn xong chưa tải lại kết quả; chỉ nút tìm mới áp dụng tiêu chí.
4. Hệ thống lọc Room Type theo sức chứa và số phòng khả dụng trong khoảng `[checkInDate, checkOutDate)`.
5. Hiển thị số phòng còn trống.

Ngày checkout không tính là một đêm: `01/09 → 03/09` là 2 đêm.

### Xem Room Type — ✅

Trên card có thể xem ảnh, tên/mã, beds, sức chứa, giá theo đêm, giá theo số đêm và amenities như Wi-Fi, TV, minibar, điều hòa, ban công.

### Kiểm tra rate override — ✅

1. Admin tạo override cho một Room Type vào ngày/weekday cụ thể.
2. Customer tìm kỳ lưu trú chứa ngày đó.
3. So sánh `dailyRates`, giá đêm và tổng tiền với ngày không có override.

Thứ tự lấy giá cuối cùng:

```text
Rate override hiệu lực theo Room Type và ngày
    → room.priceOverride nếu có
    → roomType.basePrice
```

Sau đó hệ thống cộng giá từng đêm và tính tax theo cấu hình khách sạn.

### Giới hạn hiện tại của Customer search — ⚠️

Backend có lọc inventory theo view, tầng và tiện nghi, nhưng màn hình `/booking` hiện chưa có đầy đủ bộ lọc riêng cho số giường/loại giường, điều hòa và view. Customer chỉ đang lọc được ngày và số người lớn; trẻ em chưa có control trong flow này.

### Xem đánh giá khách — ✅

Ngay sau danh sách chọn phòng trên `/booking`, Customer xem điểm tổng, điểm theo các tiêu chí có dữ liệu và các review đã được duyệt. Danh sách tải theo trang, có thể xem phản hồi của khách sạn nếu review có staff reply; khi chưa có review, giao diện hiển thị empty state thay vì dữ liệu mẫu.

API: `GET /api/reviews/published?page=0&size=5` (yêu cầu `room:read`). Backend chỉ trả review `PUBLISHED`; tổng số và các điểm trung bình được tính trên toàn bộ review đã được duyệt, độc lập với trang hiện tại. Response public không chứa email, booking identifier, moderation reason hoặc staff actor ID.

---

## 6. Luồng 3 — Customer tạo booking và thanh toán

### Tạo booking — ✅

1. Chọn số lượng phòng/option.
2. Kiểm tra tóm tắt số phòng, số đêm, beds, sức chứa, giá từng đêm, thuế và tổng tiền.
3. Nhập tên, email, số điện thoại và yêu cầu đặc biệt.
4. Chuyển sang thanh toán. Backend tạo booking `PENDING` và giữ phòng theo `hold_expires_at`.

### Thanh toán online/QR — ✅

1. Chọn Mock Wallet, Internet Banking/SePay hoặc Card tùy cấu hình.
2. Hệ thống tạo payment với `Idempotency-Key`.
3. Hiển thị QR/checkout.
4. Hoàn tất Mock Wallet hoặc thanh toán sandbox.
5. Callback được xác minh, cập nhật payment ledger.
6. Booking chuyển `PENDING → CONFIRMED`, payment thành `PAID`.

API/chặng UI: `POST /api/bookings/{publicId}/payments`, `GET /api/bookings/{publicId}/payments/{paymentCode}`, `POST /api/payments/callback/{provider}`, `/payment/{publicId}`, `/payment/mock-wallet/{paymentCode}`.

### Không thanh toán đến khi hold hết hạn — ✅

Job backend chuyển booking `PENDING → EXPIRED`, expire payment `PENDING/PROCESSING` và giải phóng phòng. Có thể giảm TTL ở dev hoặc xem dữ liệu/job thay vì chờ thật.

---

## 7. Luồng 4 — Customer xem và quản lý booking

### Danh sách và chi tiết — ✅

Tại `/profile/bookings`, Customer lọc nhóm đang giữ/sắp tới, đang ở, đã hoàn tất và đã hủy/hết hạn. Chi tiết `/profile/bookings/{publicId}` hiển thị booking code, status, contact, Room Type/số phòng, ngày, số đêm, nightly rates, tiền phòng, thuế, payment, refund và status history.

### Hủy booking — ✅

1. Mở booking đủ điều kiện, thường `PENDING` hoặc `CONFIRMED`.
2. Nhập lý do và xác nhận.
3. Backend kiểm tra Customer là người liên hệ của booking.
4. Booking chuyển `CANCELLED`, ghi thời gian/người/lý do và history.
5. Booking đã thanh toán có thể xem preview/request refund nếu policy cho phép.

API: `POST /api/bookings/{publicId}/cancel`, `GET/POST /api/bookings/{publicId}/refunds`, `GET /api/bookings/{publicId}/refunds/preview`.

### Invoice Customer — ✅

Sau khi Staff checkout và issue invoice, Customer mở `/profile/bookings/{publicId}/invoice` để xem invoice read-only và tải PDF. Customer không sửa buyer/item/adjustment/status.

### Review — ✅

1. Chỉ review booking `CHECKED_OUT` tại `/profile/bookings/{publicId}/review`.
2. Nhập overall rating bắt buộc; các rating Phòng, Vệ sinh, Dịch vụ, Giá trị nhận được là tùy chọn.
3. Nhập title/comment và submit.
4. Review tạo ở `PENDING`, không thể gửi trùng booking.
5. Customer xem lại trạng thái và staff reply nếu đã có.

---

## 8. Luồng 5 — Staff booking, check-in, Folio và checkout

Mở `/manager/bookings`.

### Danh sách booking — ✅

Staff tìm/lọc theo booking code, tên, email, SĐT, status, source và ngày check-in/check-out; click dòng để mở Sheet chi tiết.

### Tạo booking trực tiếp tại quầy — ✅

1. Bấm **Tạo đơn mới**, chọn ngày nhận/trả.
2. Xem booking map theo tầng.
3. Chỉ chọn phòng `CLEAN`, `ACTIVE`, Room Type active, không overlap booking/block.
4. Xem beds, sức chứa, giá một đêm, giá từng đêm và tổng tiền.
5. Nhập contact: tên, SĐT bắt buộc, email tùy chọn.
6. Nhập số khách từng phòng; khách chính/phụ đều có họ tên, loại giấy tờ, số CCCD/hộ chiếu; quốc tịch/ngày sinh tùy chọn.
7. Tính giá, tạo booking.
8. Booking có `source=STAFF_MANUAL`, `PENDING`, policy `NON_REFUND`, guest gắn với từng phòng.

Trẻ em chưa tách riêng; `guestCount` hiện là số người lớn.

API: `GET /api/admin/rooms/booking-map`, `POST /api/admin/bookings/calculate-price`, `POST /api/admin/bookings`.

### Thanh toán booking Staff — ✅

Với booking `UNPAID`, Staff mở tab Thanh toán, chọn cash/online/Mock Wallet, tạo payment và hiển thị QR/checkout cho khách. Callback hoặc Mock Wallet cập nhật booking thành `PAID`.

### Xác nhận và check-in booking Staff — ✅

- Booking đã thanh toán: bấm **Xác nhận & check-in**.
- Booking chưa thanh toán: cùng action được hiểu là khách trả tiền mặt; backend tạo payment `CASH`, verify ledger, chuyển qua `CONFIRMED` rồi `CHECKED_IN`.
- Backend ghi `checked_in_at`, `checked_in_by`, status history và trạng thái booking room.

### Check-in booking Customer đã thanh toán — ✅

1. Staff mở booking `CONFIRMED` và bảo đảm mọi booking room đã được assign số phòng.
2. Bấm **Check-in**.
3. Nhập số người, khách chính/phụ, họ tên, loại và số giấy tờ, quốc tịch/ngày sinh.
4. Submit để thay placeholder bằng guest thực tế.
5. Tab Khách hiển thị guest theo phòng; giấy tờ được bảo vệ bằng mã hóa/lookup hash.
6. Backend ghi `checked_in_at`, `checked_in_by`, history và `booking_rooms.status` trong transaction.

### Gán/đổi phòng — ✅

Với booking `CONFIRMED`, Staff mở booking room chưa gán, lọc tầng, chọn phòng khả dụng và assign. Trường hợp đổi phòng dùng action change-room và kiểm tra lại overlap/timeline.

### Folio — ✅

Trong tab Folio, Staff chọn service item, nhập quantity/ghi chú, thêm khoản phát sinh. Charge sai được `Void` thay vì xóa lịch sử; tổng tiền cập nhật.

API: `GET /api/service-items`, `POST /api/bookings/{publicId}/folio-charges`, `PATCH /api/bookings/{publicId}/folio-charges/{chargeId}/void`.

### Checkout và invoice — ✅

1. Mở booking `CHECKED_IN`, kiểm tra guest, phòng, Folio, payment.
2. Bấm **Check-out**.
3. Backend chuyển `CHECKED_IN → CHECKED_OUT`, ghi `checked_out_at`, `checked_out_by`, tự tạo invoice `DRAFT`.
4. Trong tab Hóa đơn, sửa buyer, thêm adjustment, issue invoice, tải PDF hoặc void invoice theo quyền.

```text
Checkout → DRAFT → ISSUED → VOID
```

Lưu ý: code hiện kiểm tra booking đang `CHECKED_IN` nhưng chưa chặn rõ checkout sớm trước ngày `check_out_date`; không nên giới thiệu rằng hệ thống đã khóa checkout theo đúng ngày lịch.

### Hủy booking Staff — ✅

Staff/Admin có quyền `booking:cancel_any` nhập lý do và hủy booking đủ điều kiện; backend ghi status history và gửi email nghiệp vụ nếu có contact email.

---

## 9. Luồng 6 — Phòng, sơ đồ tầng và trạng thái

Mở `/manager/rooms`.

### Danh sách phòng — ✅

Hiển thị số phòng, Room Type, tầng/view, operational status, housekeeping status, tìm kiếm, filter Room Type/tầng/view/housekeeping và phân trang.

- Admin: tạo, sửa Room Type/view/tầng/giá riêng và soft-delete.
- Staff: chỉ xem, không tạo/sửa/xóa inventory.

### Sơ đồ tầng — ✅

1. Chọn phòng để xem housekeeping, operational status và occupancy.
2. Occupancy gồm Trống, `HELD`, `RESERVED`, `OCCUPIED`.
3. Staff/Admin có `room:housekeeping:update` đổi trạng thái theo chu trình:

```text
CLEAN → DIRTY → CLEANING → CLEAN
```

Không được nhảy cóc/đi ngược; gửi lại status hiện tại là idempotent. Housekeeping và booking occupancy độc lập: phòng có thể sạch nhưng đã đặt.

API: `GET /api/rooms`, `GET /api/rooms/occupancy`, `PATCH /api/rooms/{roomNumber}/housekeeping-status`, `PATCH /api/rooms/{roomNumber}/operational-status`.

---

## 10. Luồng 7 — Maintenance và timeline phòng

### Lịch bảo trì — ✅

Từ tab **Lịch bảo trì** trong `/manager/rooms`:

1. Chọn tháng, xem ma trận phòng × ngày.
2. Tạo block với phòng, loại block, ngày bắt đầu/kết thúc và lý do.
3. Click block để xem chi tiết.
4. Kéo dài ngày kết thúc hoặc hủy block.

Loại block: `MAINTENANCE`, `RENOVATION`, `OUT_OF_SERVICE`, `INTERNAL_USE`, `DEEP_CLEANING`. Staff/Admin có thể thao tác nếu có `maintenance:manage`; backend chặn overlap booking/block.

API: `GET/POST /api/room-status-blocks`, `PATCH /api/room-status-blocks/{publicId}/extend`, `DELETE /api/room-status-blocks/{publicId}`.

### Timeline trong Staff booking — ✅

Booking map hiển thị booking và maintenance event theo ngày. Booking có booking code/public id để mở booking cần kiểm tra; phòng có event overlap không được chọn.

---

## 11. Luồng 8 — Admin Room Type, amenities và giá

### Room Type — ✅

Tại `/manager/room-types`, Admin:

1. Xem/tìm kiếm/phân trang Room Type.
2. Tạo với code, tên, mô tả, base price, currency, sức chứa, diện tích, beds, amenities, active.
3. Sửa thông tin, beds, amenities.
4. Upload ảnh qua presigned URL MinIO.
5. Soft-delete/disable và xem thống kê active/disabled.

Staff chỉ đọc.

### Amenities — ✅

Amenities như Wi-Fi, TV, minibar, điều hòa, ban công, pool, spa được gán cho Room Type và làm dữ liệu lọc inventory. Admin có thể quản lý danh mục theo permission; Staff chỉ đọc.

### Quản lý giá — ✅

Tại `/manager/pricing`, Admin xem rule active, lọc Room Type, tạo rate override theo Room Type với tên, khoảng ngày, giá, weekday và priority; tab lịch giá hiển thị giá từng ngày. Rule priority cao nhất thắng, ngày không có override dùng base price.

UI hiện tập trung vào xem/tạo override; không nên giới thiệu edit/delete override nếu màn hình chưa có action đó.

### Cancellation policy — ⚠️

Trang `/manager/cancellation-policies` vẫn có thể xem/quản lý theo permission. Booking Staff/Admin tại quầy luôn dùng `NON_REFUND`; đây không phải bước chọn policy trong Staff booking. Customer online phụ thuộc các policy option active của Room Type.

---

## 12. Luồng 9 — Admin Customer và Staff

### Customer accounts — ✅

Tại `/manager/guests`, Admin tìm/lọc Customer, xem profile và booking history read-only, chuyển `ACTIVE ↔ DEACTIVATED`. Deactivate không xóa profile hoặc lịch sử booking.

API: `/api/users?role=CUSTOMER...`, `GET /api/users/{publicId}`, `GET /api/users/{publicId}/bookings`, `PATCH /api/users/{publicId}/status`.

### Hire Staff — ✅

1. Tại `/manager/staff`, bấm tạo tài khoản Staff.
2. Nhập email, tên, SĐT, chức danh, phòng ban, ngày vào làm, lương và mật khẩu tạm.
3. Backend tạo User role `STAFF` + StaffProfile độc lập.
4. Invitation email đưa Staff tới `/staff-invitation` để xác thực/kích hoạt.
5. Staff đăng nhập tại `/manager/login`.

### Quản lý Staff — ✅

Admin xem mã nhân viên, tên, email, SĐT, chức danh, phòng ban, lương, ngày vào làm; sửa hồ sơ, đổi `ACTIVE/ON_LEAVE/TERMINATED`, reset password và gửi lại invitation. Lương chỉ Admin được đọc.

### Staff profile — ✅

Staff mở `/manager/profile` để xem thông tin công việc, cập nhật SĐT và upload avatar của mình. Admin có profile riêng trong cùng khu vực Manager.

---

## 13. Luồng 10 — Review, moderation và reply

### Staff reply — ✅

1. Staff mở `/manager/reviews`.
2. Lọc status/Room Type/overall rating, mở detail panel.
3. Xem Customer, booking, phòng, ratings, title/comment.
4. Nhập/cập nhật staff reply.
5. Customer xem lại reply trong review của mình.

Staff có `review:reply` nhưng không approve/hide/reject.

### Admin moderation — ✅

Admin có thêm:

- Phê duyệt → `PUBLISHED`.
- Ẩn → `HIDDEN`.
- Từ chối → `REJECTED`, bắt buộc moderation reason.
- Có thể xử lý lại review đã xử lý theo rule backend.
- Chuyển `PUBLISHED/HIDDEN` sẽ xóa moderation reason hiện tại.

API: `GET /api/admin/reviews`, `GET /api/staff/reviews`, `POST /api/bookings/{publicId}/review/moderate`, `POST /api/bookings/{publicId}/review/reply`.

---

## 14. Luồng 11 — Ca trực và lịch làm việc

### Admin quản lý ca — ✅

Tại `/manager/shifts`, Admin tạo/sửa/vô hiệu hóa ca, phân công Staff theo ngày, sửa/hủy assignment và kiểm tra không overlap. Ca đêm `22:00 → 06:00` tính sang ngày hôm sau; assignment cũ giữ giờ đã snapshot.

API: `/api/shifts`, `/api/shift-assignments`.

### Staff xem lịch cá nhân — ✅

Staff xem lịch tuần hiện tại và tuần kế tiếp tại `/manager/shifts`, đánh dấu ca đã hoàn thành sau giờ kết thúc hoặc báo vắng kèm lý do. Staff chỉ cập nhật assignment của mình.

API: `GET /api/staff/shift-assignments?from=&to=`, `POST /api/staff/shift-assignments/{publicId}/complete`, `POST /api/staff/shift-assignments/{publicId}/absent`.

---

## 15. Luồng 12 — Payment, refund, invoice và email

### Payment management — ✅

Tại `/manager/payments`, Admin/Staff có quyền xem payment, mở detail, verify cash và xem trạng thái/payment events theo permission.

### Refund — ✅

1. Booking đã hủy và có refund hợp lệ tạo request `PENDING`.
2. Người có `refund:approve` chuyển `PENDING → PROCESSING`.
3. Hoàn tất chuyển `PROCESSING → COMPLETED`, đồng bộ payment ledger/booking/invoice và gửi email.

Implementation hiện không có enum `APPROVED` riêng; approve chính là bước `PENDING → PROCESSING`.

### Invoice — ✅

Trong booking detail: checkout tạo DRAFT; buyer/adjustment chỉ sửa ở DRAFT; issue thành ISSUED; ISSUED tải PDF hoặc void thành VOID. Invoice Customer là read-only.

### Email tự động và compose — ✅

Email hệ thống gồm xác thực Customer, reset password, Staff invitation, booking confirmed/cancelled và refund completed.

Trong booking detail, Staff/Admin có quyền `email:send` có thể nhập subject/body, queue email tới booking contact và xem lịch sử `QUEUED`, `SENDING`, `SENT`, `FAILED`, `BOUNCED`.

API: `GET/POST /api/bookings/{bookingPublicId}/emails`.

---

## 16. Luồng 13 — Dashboard và báo cáo

### Dashboard — ✅

Admin mở `/manager` để xem booking đến/đi trong ngày, phòng trống, occupied rooms, occupancy percentage, doanh thu tháng, khách đến hôm nay và quick actions.

API: `GET /api/admin/dashboard`.

### Báo cáo — ✅ với Admin / ⚠️ với Staff

Admin mở `/manager/reports` để xem occupancy theo khoảng ngày, doanh thu ngày/tháng, theo nguồn booking, hoa hồng OTA, theo Room Type, biểu đồ và export CSV.

API: `/api/revenue/occupancy`, `/api/revenue/daily`, `/api/revenue/monthly`, `/api/revenue/by-source`, `/api/revenue/ota-commission`, `/api/revenue/by-room-type`.

Permission seed hiện tại cấp `revenue:read` cho Admin. Staff xem được occupancy/phòng theo quyền nhưng chưa có menu báo cáo doanh thu riêng. Nếu cần demo Staff có thống kê khách và doanh thu, cần bổ sung permission/API/UI trước.

---

## 17. Ma trận chức năng theo role

| Chức năng                         |     Customer     |     Staff     |      Admin      |
| ----------------------------------- | :---------------: | :------------: | :--------------: |
| Đăng ký, login, reset password   |        ✅        |     Login     |      Login      |
| Google OAuth                        |        ✅        |       —       |        —        |
| Tìm phòng theo ngày/sức chứa   |        ✅        |       —       |        —        |
| Tạo booking online                 |        ✅        |       —       |        —        |
| Tạo booking bằng số phòng       |        —        |       ✅       |        ✅        |
| Thanh toán booking                 |        ✅        |       ✅       |        ✅        |
| Xem/hủy booking của mình         |        ✅        |       —       |        —        |
| Xem/xử lý booking                 |        —        |       ✅       |        ✅        |
| Xem review đã được duyệt          |        ✅        |       —        |        —        |
| Gán/đổi phòng, check-in/out     |        —        |       ✅       |        ✅        |
| Guest và giấy tờ lưu trú       |        —        |       ✅       |        ✅        |
| Folio/invoice                       |    Xem invoice    |       ✅       |        ✅        |
| Review                              | Gửi sau checkout |     Reply     | Moderate + reply |
| Xem phòng/sơ đồ/occupancy       |        —        |       ✅       |        ✅        |
| Đổi housekeeping status           |        —        |       ✅       |        ✅        |
| Tạo/sửa/xóa phòng và Room Type |        —        |       ⛔       |        ✅        |
| Tạo rate override                  |        —        |       ⛔       |        ✅        |
| Maintenance                         |        —        | ✅ theo quyền |        ✅        |
| Lịch ca cá nhân                  |        —        |       ✅       |        ✅        |
| Quản lý ca/assignment             |        —        |       ⛔       |        ✅        |
| Customer/Staff management           |        —        |       ⛔       |        ✅        |
| Dashboard/báo cáo doanh thu       |        —        | ⛔ hiện tại |        ✅        |
| Gửi email Customer                 |        —        | ✅ theo quyền |        ✅        |

## 18. Nội dung chưa nên giới thiệu là hoàn thiện

1. Facebook/Twitter login chưa có; hiện chỉ có Google OAuth.
2. Bộ lọc Customer theo số giường, điều hòa và view chưa có đầy đủ trên `/booking`.
3. Project xử lý hotel room booking, chưa có module tour.
4. Staff chưa có dashboard doanh thu/số khách riêng theo permission hiện tại.
5. Checkout chưa chặn rõ việc checkout sớm trước ngày dự kiến.
6. Customer settings như 2FA/notification đang có phần UI nhưng chưa phải backend flow hoàn chỉnh.
7. Moderation reason lưu trên review; chưa có bảng moderation history riêng.

## 19. Tài liệu kỹ thuật tham chiếu

- [Authentication và OAuth](./flows/auth_jwt_oauth.md)
- [Room CRUD và housekeeping](./flows/be_3_2_room_crud_housekeeping.md)
- [Room status block](./flows/be_3_3_room_status_blocks.md)
- [Availability engine](./flows/be_5_1_availability_engine.md)
- [Staff booking room map/timeline](./flows/be_5_4_staff_booking_room_timeline.md)
- [Staff booking UI](./flows/fe_5_4_staff_booking_room_selection.md)
- [Folio](./flows/fe_6_1_staff_folio_panel.md)
- [Invoice](./flows/fe_6_2_staff_invoice_issuance.md)
- [Customer invoice](./flows/fe_6_3_customer_invoice_view.md)
- [Shifts](./flows/be_2_5_shifts_and_shift_assignments.md)
- [Admin Customer management](./flows/be_2_6_admin_customer_account_management.md)
- [Database design](./database/DATABASE_DESIGN.md)

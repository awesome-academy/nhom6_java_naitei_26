# BE-2.6 — Admin quản lý tài khoản khách hàng

## Business rules

- Admin được xem toàn bộ tài khoản có role `CUSTOMER`, tìm theo họ tên, email hoặc số điện thoại và lọc theo trạng thái.
- Admin chỉ được chuyển tài khoản customer giữa `ACTIVE` và `DEACTIVATED`. Staff không có quyền gọi các endpoint này.
- Vô hiệu hóa tài khoản không phải là xóa: không set `deleted_at`, không xóa customer profile và không thay đổi, hủy hoặc xóa booking hiện có.
- User `DEACTIVATED` bị chặn đăng nhập, refresh token và các API customer ở request tiếp theo. Có thể kích hoạt lại thành `ACTIVE`.
- Trang customer detail của Admin chỉ đọc booking. Các action thanh toán, hủy, xóa booking vẫn thuộc flow booking riêng.

## API contract

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET | `/api/users?role=CUSTOMER&page=0&search=&status=` | Danh sách customer, cố định 20 dòng/trang |
| GET | `/api/users/{publicId}` | Account và customer profile của customer |
| GET | `/api/users/{publicId}/bookings` | Booking summary mới nhất, chỉ dữ liệu hiển thị |
| PATCH | `/api/users/{publicId}/status` | Body `{ "status": "ACTIVE" | "DEACTIVATED" }` |

Danh sách trả về `page`, `size`, `totalItems`, `totalPages` cùng `items`. Booking summary chỉ gồm mã booking, ngày lưu trú, số đêm, số phòng, tổng khách, tổng tiền, trạng thái booking và trạng thái payment.


# FE-3.3 — Admin Room Maintenance Scheduling

Tài liệu này mô tả flow trang `/admin/maintenance`: xem lịch block phòng theo ma trận ngày × phòng, lọc phòng, xem chi tiết block và tạo lịch mới. Trang dùng lại `AdminLayout`, Card, Button, Dialog, Select, Sheet, Badge, Skeleton và design token của các trang Admin Inventory trước đó.

## 1. Phạm vi

FE-3.3 gồm:

- Calendar toàn bộ ngày trong tháng, mỗi hàng là một phòng.
- Điều hướng tháng trước, tháng sau và quay về tháng hiện tại.
- Tìm kiếm/lọc phòng theo số phòng, loại phòng và tầng.
- Hiển thị block theo loại và cắt đúng phần giao với tháng đang xem.
- Click ô trống để tạo block đã điền sẵn phòng/ngày; click block để xem chi tiết.
- Modal tạo block gồm phòng, loại, ngày bắt đầu/kết thúc và ghi chú.
- Kiểm tra overlap phía frontend trước khi tạo và xử lý conflict từ backend.

Task không triển khai kéo thả, sửa, kéo dài hoặc hủy block. Các API extend/delete của BE-3.3 không được gọi trong flow này.

## 2. Route, API và permission

| Chức năng | API | Permission |
| --- | --- | --- |
| Tải phòng | `GET /api/rooms` | `room:read` |
| Tải block overlap tháng | `GET /api/room-status-blocks?startDate=&endDate=` | `room:read` |
| Kiểm tra overlap trước khi tạo | `GET /api/room-status-blocks?startDate=&endDate=` | `room:read` |
| Tạo block | `POST /api/room-status-blocks` | `room:update` |

Người chưa đăng nhập được chuyển tới `/login?redirect=%2Fadmin%2Fmaintenance`. Người dùng thiếu `room:read` thấy trạng thái không có quyền truy cập. Nút tạo và khả năng click ô trống chỉ bật khi có `room:update`; backend vẫn kiểm tra permission tại controller và service.

## 3. Khoảng tháng và quy tắc ngày

Calendar mặc định mở tháng hiện tại. Khi hiển thị một tháng, frontend gọi API bằng khoảng nửa mở:

```text
[ngày đầu tháng, ngày đầu tháng kế tiếp)
```

Ví dụ tháng 08/2026:

```http
GET /api/room-status-blocks?startDate=2026-08-01&endDate=2026-09-01
```

Block cũng dùng khoảng nửa mở `[startDate, endDate)`. Block `2026-08-20 → 2026-08-22` chiếm ngày 20 và 21, không chiếm ngày 22. Hai block có ngày cuối/đầu tiếp giáp được phép.

Block bắt đầu trước tháng hoặc kết thúc sau tháng vẫn được API trả về vì có overlap. Calendar chỉ tô các ngày nằm trong tháng đang xem; cạnh block được bo ở biên tháng để thể hiện phần bị cắt.

## 4. Flow calendar

1. Sau khi Auth Context hoàn tất, frontend tải danh sách phòng và block của tháng.
2. Trong lúc tải, trang hiển thị skeleton. Lỗi API hiển thị error state và nút “Thử lại”.
3. Header calendar và cột thông tin phòng được giữ cố định khi cuộn.
4. Ngày hiện tại được highlight xanh; thứ Bảy/Chủ nhật dùng nền trung tính.
5. Bộ lọc phòng được áp dụng phía client:
   - Tìm không phân biệt hoa thường theo `roomNumber` hoặc `roomTypeName`.
   - Lọc chính xác theo `roomTypeCode`.
   - Lọc theo tầng, gồm option “Chưa gán tầng”.
6. Điều hướng tháng đóng panel block hiện tại và refetch đúng khoảng tháng mới.
7. Phòng có operational status khác `ACTIVE` vẫn xuất hiện và có biểu tượng cảnh báo; trạng thái dài hạn không thay thế block theo ngày.

Màu block:

| Loại | Màu |
| --- | --- |
| `MAINTENANCE` | Vàng hổ phách |
| `RENOVATION` | Tím |
| `OUT_OF_SERVICE` | Đỏ |
| `INTERNAL_USE` | Xanh dương |
| `DEEP_CLEANING` | Xanh teal |

Click một đoạn block mở side panel chỉ đọc, hiển thị UUID public, phòng, loại block, operational status, ngày bắt đầu, ngày kết thúc không bao gồm, thời lượng và ghi chú. Frontend không nhận hoặc hiển thị khóa BIGINT.

## 5. Flow tạo block

Admin có thể mở modal theo hai cách:

- Nút “Tạo lịch bảo trì”: chọn sẵn phòng đầu tiên theo kết quả lọc, ngày hiện tại nếu đang xem tháng hiện tại hoặc ngày đầu tháng đang xem.
- Click ô trống: chọn sẵn phòng và ngày của ô; `endDate` mặc định là ngày kế tiếp.

Request mẫu:

```json
{
  "roomNumber": "A-301",
  "blockType": "MAINTENANCE",
  "startDate": "2026-08-20",
  "endDate": "2026-08-22",
  "reason": "Bảo dưỡng hệ thống điều hòa"
}
```

Frontend validate bằng React Hook Form và Zod:

- Phòng, loại block, `startDate`, `endDate` là bắt buộc.
- Hai ngày phải đúng định dạng ISO và `endDate > startDate`.
- `reason` được trim, rỗng chuyển thành `null`, tối đa 10.000 ký tự.
- Không giới hạn ngày trong quá khứ vì database/business rule hiện tại không đặt giới hạn.

Trước POST, frontend gọi GET đúng khoảng vừa nhập. Nếu response có block cùng `roomNumber` thỏa:

```text
existing.startDate < new.endDate && existing.endDate > new.startDate
```

modal hiển thị lỗi overlap và không gửi POST. Block cùng khoảng trên phòng khác hoặc block tiếp giáp không bị chặn.

Nếu preflight không thấy conflict, frontend gọi POST. Thành công sẽ đóng modal, toast và refetch; nếu block nằm ở tháng khác, calendar chuyển tới tháng của `startDate` trước khi tải lại.

## 6. BR-003/BR-004 và race condition

Preflight phía frontend chỉ giúp phản hồi sớm, không phải lớp bảo vệ cuối. Dữ liệu có thể thay đổi giữa GET và POST.

Backend khóa dòng phòng, kiểm tra overlap block và booking `RESERVED`/`OCCUPIED`, sau đó `saveAndFlush()`. Trigger MySQL tiếp tục chặn race condition hoặc thao tác ghi trực tiếp DB. Vì vậy:

- Trùng block trên cùng phòng trả `409`.
- Trùng booking hiệu lực trả `409` dù frontend không có API đọc booking trong flow này.
- Block ở hai phòng khác nhau được phép.
- Tạo block không tự thay đổi `rooms.operational_status`.

## 7. Bảng lỗi UI

| HTTP/tình huống | Xử lý frontend |
| --- | --- |
| `400 Bad Request` | Hiển thị validation từ backend và giữ modal mở |
| `401 Unauthorized` | Phiên không hợp lệ; route guard chuyển về login khi Auth Context mất phiên |
| `403 Forbidden` | Backend từ chối; action tạo đã được ẩn khi thiếu `room:update` |
| `404 Not Found` | Phòng đã xóa/vô hiệu hóa hoặc không còn tồn tại; thông báo lỗi và cho phép tải lại |
| `409 Conflict` | Hiển thị phòng đã có block hoặc booking hiệu lực trong khoảng đã chọn |
| Danh sách phòng rỗng | Vô hiệu hóa nút tạo và hiển thị empty state |
| Bộ lọc không có kết quả | Hiển thị empty state nhưng vẫn giữ điều hướng tháng và bộ lọc |

## 8. Kiểm thử

- Kiểm tra query tháng dùng `[firstDay, firstDayOfNextMonth)` và refetch khi điều hướng.
- Kiểm tra block một ngày, nhiều ngày, xuyên tháng, highlight hôm nay/cuối tuần và sticky header/cột phòng.
- Kiểm tra tìm kiếm, loại phòng, tầng và xóa bộ lọc.
- Kiểm tra click ô trống prefill đúng phòng, start và end kế tiếp.
- Kiểm tra ngày thiếu/sai/ngược, reason quá dài, overlap cùng phòng, adjacency và phòng khác.
- Kiểm tra side panel chi tiết và RBAC `room:read`/`room:update`.
- Chạy `npm run lint`, `npm run build` và full `mvn test`.

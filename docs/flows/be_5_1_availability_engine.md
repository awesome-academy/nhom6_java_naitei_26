# BE-5.1 — Availability Engine

Tài liệu này mô tả flow backend kiểm tra phòng khả dụng cho một kỳ lưu trú. Availability là kết quả tính tại thời điểm truy vấn, không phải trạng thái được lưu trên `rooms`. Endpoint yêu cầu Bearer access token và permission được kiểm tra tại cả controller lẫn service.

## 1. API contract

| Method | Endpoint | Permission | Kết quả thành công |
| --- | --- | --- | --- |
| `GET` | `/api/rooms/availability?checkInDate=&checkOutDate=` | `booking:create` | `200`, map Room Type sang các room ID khả dụng |

Hai query parameter dùng định dạng ISO `YYYY-MM-DD`:

```http
GET /api/rooms/availability?checkInDate=2026-09-10&checkOutDate=2026-09-12
Authorization: Bearer <access-token>
```

Response:

```json
{
  "1": [101, 102],
  "2": [205]
}
```

- Key biểu diễn `room_type_id`; JSON luôn biểu diễn object key dưới dạng chuỗi.
- Value là các `rooms.id` khả dụng thuộc Room Type đó.
- Chỉ Room Type có ít nhất một phòng khả dụng mới xuất hiện.
- Không có phòng phù hợp trả object rỗng `{}`, không trả `404`.
- Room Type và room ID được sắp xếp tăng dần để response ổn định.

BE-5.1 không thêm pagination và không lọc theo giá, sức chứa, view, tầng, tiện nghi hoặc housekeeping status.

## 2. Quy tắc availability

Một phòng khả dụng cho `[checkInDate, checkOutDate)` khi thỏa mãn toàn bộ điều kiện:

1. `rooms.deleted_at IS NULL`.
2. `rooms.is_active = TRUE`.
3. `rooms.operational_status = 'ACTIVE'`.
4. Room Type chưa soft-delete và có `room_types.is_active = TRUE`.
5. Không có `booking_rooms` trạng thái `RESERVED` hoặc `OCCUPIED` overlap kỳ lưu trú.
6. Không có bất kỳ `room_status_blocks` nào overlap kỳ lưu trú.

Các trạng thái booking room `COMPLETED`, `RELEASED` và `MOVED_OUT` không giữ phòng. `housekeeping_status` không tham gia Availability Engine vì database design chỉ định availability theo trạng thái vận hành, booking hiệu lực và status block.

Ngày quá khứ được phép truy vấn. Request không hợp lệ nếu thiếu ngày hoặc `checkOutDate <= checkInDate`.

## 3. Khoảng ngày nửa mở

Kỳ lưu trú dùng khoảng nửa mở:

```text
[checkInDate, checkOutDate)
```

Ngày check-in được tính, ngày check-out không được tính. Một booking hoặc block overlap request khi:

```text
existing.start < requested.end
AND existing.end > requested.start
```

Hai khoảng tiếp giáp không overlap:

```text
Booking cũ: [2026-09-08, 2026-09-10)
Request mới: [2026-09-10, 2026-09-12)  -> phòng vẫn khả dụng

Request mới: [2026-09-10, 2026-09-12)
Block sau:   [2026-09-12, 2026-09-14)  -> phòng vẫn khả dụng
```

Ví dụ overlap:

```text
Request: [2026-09-10, 2026-09-12)
Booking: [2026-09-09, 2026-09-11)  -> overlap, loại phòng
Block:   [2026-09-11, 2026-09-13)  -> overlap, loại phòng
```

## 4. Flow xử lý request

1. Spring Security xác thực access token.
2. Controller kiểm tra authority `booking:create`.
3. Spring parse `checkInDate` và `checkOutDate` thành `LocalDate`; thiếu hoặc sai định dạng trả `400`.
4. `AvailabilityService` kiểm tra lại permission và xác nhận `checkOutDate > checkInDate`.
5. Repository chạy một JPQL query parameterized, chỉ lấy projection `roomTypeId` và `roomId`.
6. Hai subquery `NOT EXISTS` loại booking hiệu lực và room status block overlap.
7. Kết quả được sắp xếp theo Room Type ID rồi room ID.
8. Service gom các projection vào `LinkedHashMap<Long, List<Long>>` và controller trả `200`.

Query có cấu trúc tương đương:

```sql
SELECT r.room_type_id, r.id
FROM rooms r
JOIN room_types rt ON rt.id = r.room_type_id
WHERE r.deleted_at IS NULL
  AND r.is_active = TRUE
  AND r.operational_status = 'ACTIVE'
  AND rt.deleted_at IS NULL
  AND rt.is_active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM booking_rooms br
      WHERE br.room_id = r.id
        AND br.status IN ('RESERVED', 'OCCUPIED')
        AND br.check_in_date < :checkOutDate
        AND br.check_out_date > :checkInDate
  )
  AND NOT EXISTS (
      SELECT 1
      FROM room_status_blocks block
      WHERE block.room_id = r.id
        AND block.start_date < :checkOutDate
        AND block.end_date > :checkInDate
  )
ORDER BY r.room_type_id, r.id;
```

Các ngày được bind qua query parameter, không nối chuỗi SQL. Index trên `booking_rooms(room_id, check_in_date, check_out_date, status)` và `room_status_blocks(room_id, start_date, end_date)` phục vụ hai phép kiểm tra overlap.

## 5. BR-002 và race condition

Availability API là pre-check phục vụ hiển thị. Kết quả có thể cũ ngay sau khi trả về nếu request khác vừa giữ cùng phòng. Vì vậy BE-5.1 không thay thế ràng buộc khi ghi booking.

Flyway V7 hiện có giữ vai trò bảo vệ cuối bằng các trigger MySQL:

- `trg_booking_rooms_before_insert`
- `trg_booking_rooms_before_update`
- `trg_room_status_blocks_before_insert`
- `trg_room_status_blocks_before_update`

Trigger booking thực hiện:

1. Khóa dòng `rooms` tương ứng bằng `SELECT ... FOR UPDATE`.
2. Kiểm tra `check_out_date > check_in_date`.
3. Với trạng thái `RESERVED` hoặc `OCCUPIED`, yêu cầu phòng active, chưa soft-delete và có `operational_status = ACTIVE`.
4. Từ chối booking overlap booking hiệu lực khác.
5. Từ chối booking overlap room status block.
6. Phát `SIGNAL SQLSTATE '45000'` khi vi phạm.

Trigger thay thế PostgreSQL `EXCLUDE USING gist` trong MySQL 8 và bảo vệ cả request đồng thời lẫn thao tác ghi trực tiếp vào database. BE-5.1 không tạo migration mới vì V7 đã chứa đầy đủ cơ chế BR-002/BR-004.

```text
Availability request -> phòng 101 đang trống
                         |
Request A tạo booking ---+--> lock rooms/101 -> insert thành công
Request B tạo booking ------> chờ lock -> trigger thấy overlap -> từ chối
```

## 6. RBAC

- Không có hoặc access token không hợp lệ: `401 Unauthorized`.
- Token hợp lệ nhưng thiếu `booking:create`: `403 Forbidden`.
- `CUSTOMER` hiện được seed `booking:create`, phù hợp luồng chọn phòng và tạo booking.
- Permission được khai báo trên cả `AvailabilityController` và `AvailabilityService`; gọi service từ entry point khác vẫn phải qua method security.

## 7. Bảng lỗi API

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Thiếu ngày, ngày sai định dạng hoặc `checkOutDate <= checkInDate` |
| `401 Unauthorized` | Không có hoặc access token không hợp lệ |
| `403 Forbidden` | Thiếu permission `booking:create` |
| `500 Internal Server Error` | Lỗi hệ thống hoặc database ngoài dự kiến |

Không có phòng khả dụng là kết quả nghiệp vụ bình thường và trả `200 {}`.

## 8. Kiểm thử

- Unit test service: validate ngày, map rỗng và gom nhóm theo thứ tự.
- Repository test: phòng/Room Type inactive hoặc soft-delete, các trạng thái booking blocking/non-blocking, room status block, overlap và adjacency.
- Security/controller test: `401`, `403`, response map, ngày thiếu/sai định dạng và khoảng ngày không hợp lệ.
- Full suite chạy bằng `mvn clean test`.
- Trigger cần smoke test trên MySQL 8 vì H2 test profile không chạy Flyway migration MySQL.

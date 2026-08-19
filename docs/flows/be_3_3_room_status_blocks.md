# BE-3.3 — Room Status Blocks (BR-003/BR-004)

Tài liệu này mô tả flow backend của BE-3.3: tra cứu, tạo, kéo dài và hủy khoảng khóa phòng; cập nhật trạng thái vận hành dài hạn của phòng; và cơ chế chống race condition giữa booking với room status block. Tất cả endpoint yêu cầu Bearer access token hợp lệ. Permission được kiểm tra tại cả controller và service.

## 1. Phạm vi và mô hình trạng thái

Room Status Block là khoảng ngày mà một phòng không thể nhận booking. Block dùng cho các mục đích:

- `MAINTENANCE`
- `RENOVATION`
- `OUT_OF_SERVICE`
- `INTERNAL_USE`
- `DEEP_CLEANING`

`operational_status` là trạng thái vận hành dài hạn của phòng và nhận một trong bốn giá trị:

- `ACTIVE`
- `MAINTENANCE`
- `OUT_OF_SERVICE`
- `RENOVATION`

Hai khái niệm độc lập với nhau. Tạo, kéo dài hoặc hủy block không tự đổi `operational_status`; một block có thể được ghi cho phòng đang ở bất kỳ trạng thái vận hành nào. Ngược lại, đổi `operational_status` không tự tạo hoặc xóa block.

BE-3.3 không thêm pagination, không cấm khoảng ngày trong quá khứ, không gộp block theo phòng và không tự đưa phòng non-`ACTIVE` vào kết quả nếu phòng không có block.

## 2. API contract

| Method | Endpoint | Permission | Kết quả thành công |
| --- | --- | --- | --- |
| `GET` | `/api/room-status-blocks?startDate=&endDate=` | `room:read` | `200`, các block overlap khoảng tìm kiếm |
| `POST` | `/api/room-status-blocks` | `room:update` | `201`, block vừa tạo và header `Location` |
| `PATCH` | `/api/room-status-blocks/{publicId}/extend` | `room:update` | `200`, block sau khi kéo dài |
| `DELETE` | `/api/room-status-blocks/{publicId}` | `room:update` | `204`, block đã bị hard-delete |
| `PATCH` | `/api/rooms/{roomNumber}/operational-status` | `room:update` | `200`, trạng thái vận hành mới |

API không lộ khóa `BIGINT`. Block được định danh bên ngoài bằng UUID `publicId`.

### Tạo block

```json
{
  "roomNumber": "A101",
  "blockType": "MAINTENANCE",
  "startDate": "2026-09-10",
  "endDate": "2026-09-12",
  "reason": "Bảo trì điều hòa"
}
```

`roomNumber` được trim và uppercase, tối đa 20 ký tự, chỉ nhận chữ, số, `_`, `-`. `createdBy` không nhận từ client mà lấy từ `UserPrincipal.id`. `reason` được trim; chuỗi trống được lưu thành `null`.

Response:

```json
{
  "publicId": "e1f8e2e2-8090-4ad0-a213-b5543da39d04",
  "roomNumber": "A101",
  "operationalStatus": "ACTIVE",
  "blockType": "MAINTENANCE",
  "startDate": "2026-09-10",
  "endDate": "2026-09-12",
  "reason": "Bảo trì điều hòa",
  "createdAt": "2026-08-19T10:00:00+07:00",
  "updatedAt": "2026-08-19T10:00:00+07:00"
}
```

Header trả về khi tạo thành công:

```http
Location: /api/room-status-blocks/e1f8e2e2-8090-4ad0-a213-b5543da39d04
```

### Kéo dài block

```json
{
  "newEndDate": "2026-09-15"
}
```

`newEndDate` bắt buộc lớn hơn `endDate` hiện tại. API không cho đổi ngày bắt đầu, loại block hoặc lý do.

### Cập nhật trạng thái vận hành

```json
{
  "status": "MAINTENANCE"
}
```

Response chỉ trả định danh phòng và trạng thái hiện tại:

```json
{
  "roomNumber": "A101",
  "operationalStatus": "MAINTENANCE"
}
```

## 3. Quy tắc khoảng ngày nửa mở

Mọi khoảng ngày của block và booking dùng dạng nửa mở:

```text
[startDate, endDate)
```

Ngày bắt đầu được tính, ngày kết thúc không được tính. Điều kiện overlap là:

```text
newStart < existingEnd AND newEnd > existingStart
```

Vì vậy hai khoảng tiếp giáp được phép:

```text
Block A: [2026-09-10, 2026-09-12)
Block B: [2026-09-12, 2026-09-14)  -> hợp lệ
```

Mọi request khoảng ngày yêu cầu `endDate > startDate`. Với API danh sách, một block được trả về khi overlap `[startDate, endDate)` của query. Kết quả được sắp xếp theo `block.startDate`, sau đó theo `roomNumber`.

## 4. Flow tạo block

1. Security xác thực access token và permission `room:update`.
2. Controller chạy Bean Validation cho JSON.
3. Service kiểm tra khoảng ngày và chuẩn hóa `roomNumber`.
4. Service khóa pessimistic dòng `rooms` tương ứng bằng `SELECT ... FOR UPDATE`.
5. Phòng phải chưa soft-delete và có `is_active = true`; nếu không, trả `404`.
6. Service kiểm tra block hiện có theo điều kiện overlap nửa mở. Nếu trùng, trả `409`.
7. Service kiểm tra `booking_rooms` có trạng thái `RESERVED` hoặc `OCCUPIED` trong khoảng yêu cầu. Nếu trùng, trả `409`.
8. Service sinh UUID, lấy `createdBy` từ principal và gọi `saveAndFlush()`.
9. Trigger `BEFORE INSERT` khóa lại cùng dòng phòng và kiểm tra BR-003/BR-004 ở tầng database.
10. Nếu dữ liệu hợp lệ, API trả `201`. Nếu trigger phát hiện race condition, service log room/UUID cùng root cause và chuyển lỗi thành `409`.

Trạng thái vận hành hiện tại không hạn chế việc tạo block. Điều này cho phép lập lịch bảo trì tương lai trong khi phòng đang `ACTIVE`, hoặc lưu thêm một khoảng công việc cho phòng đang `RENOVATION`.

## 5. Flow kéo dài block

1. Service tìm block bằng UUID; không tồn tại trả `404`.
2. Service khóa dòng phòng của block và xác nhận phòng vẫn active về mặt bản ghi (`is_active = true`, chưa soft-delete).
3. `newEndDate` phải sau `endDate` hiện tại; bằng hoặc nhỏ hơn trả `400`.
4. Khi kiểm tra block overlap, service loại trừ chính block đang sửa.
5. Service kiểm tra lại toàn bộ khoảng mới `[startDate, newEndDate)` với booking `RESERVED`/`OCCUPIED`.
6. Service chỉ cập nhật `endDate` rồi gọi `saveAndFlush()`; trigger `BEFORE UPDATE` kiểm tra lại trong database.
7. Thành công trả `200`; conflict do pre-check hoặc trigger trả `409`.

## 6. Flow hủy block

1. Service tìm block bằng UUID và khóa dòng phòng.
2. Block được hard-delete, áp dụng giống nhau cho block quá khứ, hiện tại và tương lai.
3. API trả `204` và không có body.

BE-3.3 không có trạng thái cancel và không giữ row block sau khi hủy. Dữ liệu phòng hoặc booking không bị xóa theo.

## 7. Flow cập nhật `operational_status`

Các trạng thái có thể chuyển trực tiếp và idempotent:

```text
ACTIVE <-> MAINTENANCE
ACTIVE <-> OUT_OF_SERVICE
ACTIVE <-> RENOVATION
MAINTENANCE <-> OUT_OF_SERVICE <-> RENOVATION
```

Flow xử lý:

1. Service chuẩn hóa số phòng và khóa dòng `rooms`.
2. Phòng soft-delete hoặc `is_active = false` được xem là không tồn tại đối với API này và trả `404`.
3. Nếu trạng thái yêu cầu bằng trạng thái hiện tại, API trả `200` mà không ghi DB lại.
4. Khi phòng đang `ACTIVE` và trạng thái đích khác `ACTIVE`, service từ chối bằng `409` nếu phòng còn bất kỳ booking `RESERVED` hoặc `OCCUPIED` nào.
5. Khi chuyển về `ACTIVE`, service luôn cho phép và không kiểm tra booking.
6. Service cập nhật trạng thái và flush trong transaction đang giữ khóa phòng.

## 8. BR-003/BR-004 và chống race condition

V7 thêm `room_status_blocks.public_id CHAR(36)`, backfill UUID cho dữ liệu cũ và tạo unique constraint. Migration đồng thời tạo lại bốn trigger:

- `trg_booking_rooms_before_insert`
- `trg_booking_rooms_before_update`
- `trg_room_status_blocks_before_insert`
- `trg_room_status_blocks_before_update`

Mỗi trigger chạy `SELECT rooms.id ... FOR UPDATE` trước khi kiểm tra. Booking và block cạnh tranh trên cùng một phòng vì thế phải tuần tự hóa theo một row lock chung:

```text
Transaction tạo booking ----┐
                            ├── lock rooms/{roomId} ── kiểm tra overlap ── ghi dữ liệu
Transaction tạo block ------┘
```

Sau khi transaction thứ nhất ghi và commit, transaction thứ hai mới tiếp tục và nhìn thấy dữ liệu vừa tạo. Cơ chế này đóng race window mà hai transaction có thể cùng pre-check thành công rồi cùng ghi dữ liệu xung đột.

Các trigger bảo vệ hai chiều:

- BR-004 booking: `RESERVED`/`OCCUPIED` không được overlap bất kỳ block nào và phòng phải có `operational_status = ACTIVE`, `is_active = true`, chưa soft-delete.
- BR-003/BR-004 block: block không được overlap block khác hoặc booking `RESERVED`/`OCCUPIED`.
- Trigger UPDATE loại trừ chính row đang cập nhật.
- Khoảng tiếp giáp vẫn hợp lệ do dùng công thức nửa mở.

Service pre-check giúp trả lỗi nghiệp vụ rõ ràng; trigger là lớp bảo vệ cuối cho request đồng thời hoặc thao tác ghi trực tiếp vào database.

## 9. Bảng lỗi API

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Thiếu/sai DTO, UUID/enum/ngày sai định dạng, `endDate <= startDate`, hoặc extend không tăng ngày kết thúc |
| `401 Unauthorized` | Không có hoặc access token không hợp lệ |
| `403 Forbidden` | Thiếu `room:read` hoặc `room:update` theo endpoint |
| `404 Not Found` | Không tìm thấy block UUID; phòng không tồn tại, đã soft-delete hoặc `is_active = false` |
| `409 Conflict` | Block overlap block/booking; chuyển phòng khỏi `ACTIVE` khi còn booking hiệu lực; trigger/constraint phát hiện race condition |

## 10. Kiểm thử

- Unit test `RoomStatusBlockService`: chuẩn hóa phòng, UUID, trim reason, khoảng sai, phòng inactive, overlap block/booking, adjacency, lỗi trigger, extend, hard-delete và list.
- Unit test trạng thái vận hành: idempotent, chuyển trực tiếp, chặn rời `ACTIVE` khi còn booking và luôn cho phép quay lại `ACTIVE`.
- Repository test: query overlap nửa mở, thứ tự kết quả, adjacency và loại trừ block hiện tại khi extend.
- Security/controller test: `401`, `403`, `room:read`, `room:update`, `201 + Location`, `200`, `204` và response không lộ BIGINT.
- Full suite chạy bằng `mvn test`. Flyway V7 cần MySQL 8 để kiểm tra cú pháp trigger và hành vi khóa dòng thực tế; H2 unit test không chạy migration MySQL.

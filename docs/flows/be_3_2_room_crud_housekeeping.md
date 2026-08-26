# BE-3.2 — Room CRUD + Housekeeping

Tài liệu này mô tả flow backend của BE-3.2: quản lý phòng, cập nhật trạng thái housekeeping, lọc phòng và upload/sắp xếp ảnh phòng qua MinIO. Tất cả endpoint đều cần Bearer access token hợp lệ và được kiểm tra permission tại cả controller lẫn service.

## 1. Phạm vi

BE-3.2 gồm:

- Tạo, xem, sửa và soft-delete phòng.
- Gán Room Type, view, tầng và giá riêng của phòng.
- Lọc phòng theo Room Type, view, tầng và tập tiện nghi hiệu lực.
- Chuyển trạng thái housekeeping theo chu trình cố định.
- Cấp presigned URL để client upload ảnh trực tiếp lên MinIO, xác nhận upload và sắp xếp ảnh.

BE-3.2 không thay đổi `operational_status`, không quản lý Room Status Block, không thêm/xóa tiện nghi riêng của phòng, không xóa hoặc sửa metadata ảnh và chưa tích hợp checkout. Checkout sau này sẽ gọi lại `RoomService` để chuyển phòng từ `CLEAN` sang `DIRTY`.

## 2. API contract

| Method | Endpoint | Permission | Kết quả thành công |
| --- | --- | --- | --- |
| `GET` | `/api/rooms` | `room:read` | `200`, danh sách phòng chưa soft-delete |
| `GET` | `/api/rooms/{roomNumber}` | `room:read` | `200`, chi tiết phòng và ảnh |
| `POST` | `/api/rooms` | `room:create` | `201`, phòng vừa tạo |
| `PUT` | `/api/rooms/{roomNumber}` | `room:update` | `200`, phòng sau cập nhật |
| `DELETE` | `/api/rooms/{roomNumber}` | `room:delete` | `204`, không có body |
| `PATCH` | `/api/rooms/{roomNumber}/housekeeping-status` | `room:housekeeping:update` | `200`, trạng thái mới |
| `GET` | `/api/rooms/occupancy` | `room:occupancy:read` | `200`, trạng thái booking hiệu lực theo ngày |
| `POST` | `/api/rooms/{roomNumber}/images/upload-url` | `room:update` | `200`, presigned PUT URL |
| `POST` | `/api/rooms/{roomNumber}/images/confirm` | `room:update` | `201`, metadata ảnh đã lưu |
| `PUT` | `/api/rooms/{roomNumber}/images/order` | `room:update` | `200`, danh sách ảnh theo thứ tự mới |

### Request tạo phòng

```json
{
  "roomNumber": "A-301",
  "roomTypeCode": "DLX",
  "viewType": "SEA",
  "floor": 3,
  "priceOverride": 1800000.00
}
```

- `roomNumber` được trim, uppercase, dài tối đa 20 ký tự và chỉ nhận chữ, số, `_`, `-`.
- `roomTypeCode` được trim và uppercase.
- `viewType` nhận `SEA`, `CITY`, `GARDEN`, `POOL`, `MOUNTAIN`, `NONE`; khi tạo và bỏ trống thì mặc định `NONE`.
- `priceOverride` nullable, không âm, tối đa 12 chữ số phần nguyên và 2 chữ số thập phân. `null` nghĩa là dùng `room_types.base_price`.

Request sửa phòng không nhận `roomNumber`; định danh phòng lấy từ path. Các trường được sửa chỉ gồm `roomTypeCode`, `viewType`, `floor`, `priceOverride`.

### Response ảnh

```json
{
  "imageId": "adff950e-0b93-493e-a821-29386dabcc55",
  "downloadUrl": "https://minio.example/...",
  "downloadUrlExpiresAt": "2026-08-19T09:15:00Z",
  "altText": "Phòng Deluxe hướng biển",
  "isPrimary": true,
  "sortOrder": 0
}
```

API không trả `room_images.id` hoặc `storageKey`. `imageId` là UUID do server sinh khi cấp upload URL; download URL có hiệu lực 15 phút.

## 3. Flow tạo, sửa và xóa phòng

### Tạo phòng

1. Spring Security xác thực access token và permission `room:create`.
2. Controller chạy Bean Validation cho request.
3. Service chuẩn hóa `roomNumber` và `roomTypeCode`.
4. Nếu có phòng chưa soft-delete trùng `roomNumber` không phân biệt hoa thường, trả `409`.
5. Service tìm Room Type chưa soft-delete. Không tồn tại trả `404`; không active trả `400`.
6. Service tạo phòng với mặc định:
   - `operationalStatus = ACTIVE`
   - `housekeepingStatus = CLEAN`
   - `isActive = true`
   - `createdBy = UserPrincipal.id`
7. `saveAndFlush()` ghi phòng và trả `201` cùng header `Location: /api/rooms/{roomNumber}`.

### Sửa phòng

1. Permission yêu cầu là `room:update`.
2. Service tìm phòng theo `roomNumber`, chỉ nhận phòng chưa soft-delete.
3. Nếu giữ nguyên Room Type hiện tại, service vẫn cho sửa view, tầng và giá dù Room Type đó đã bị vô hiệu hóa sau khi phòng được tạo.
4. Nếu đổi sang Room Type khác, Room Type mới phải tồn tại, chưa soft-delete và active.
5. Service cập nhật đúng bốn trường thuộc phạm vi BE-3.2 và trả phòng sau khi flush.

### Soft-delete phòng

1. Permission yêu cầu là `room:delete`.
2. Service đặt `isActive = false` và `deletedAt = thời điểm hiện tại theo UTC`.
3. Phòng không còn xuất hiện trong list/detail thông thường; ảnh và dữ liệu lịch sử không bị xóa.

## 4. Flow danh sách và bộ lọc

Ví dụ:

```http
GET /api/rooms?roomTypeCode=DLX&viewType=SEA&floor=3&amenityCodes=WIFI&amenityCodes=BALCONY
```

Các điều kiện Room Type, view và tầng được kết hợp bằng `AND`. Danh sách chỉ lấy dòng có `deleted_at IS NULL`, sau đó sắp xếp tăng dần theo `floor`, rồi `roomNumber`.

Tiện nghi hiệu lực của một phòng là:

```text
room_type_amenities ∪ room_amenities
```

Nhiều `amenityCodes` cũng dùng semantics `AND`. Với ví dụ trên, phòng phải có cả `WIFI` và `BALCONY`; mỗi tiện nghi có thể đến từ Room Type hoặc được gán trực tiếp cho phòng. Service kiểm tra toàn bộ code trước khi query:

- Code không tồn tại: `400`.
- Tiện nghi có `is_filterable = false`: `400`.
- Code được trim, uppercase và loại trùng trước khi lọc.

## 5. Housekeeping state machine

Request:

```json
{
  "status": "DIRTY"
}
```

Chu trình duy nhất được phép trong BE-3.2:

```text
CLEAN ──checkout/mark dirty──> DIRTY ──start cleaning──> CLEANING ──finish──> CLEAN
```

Quy tắc:

- Gửi lại đúng trạng thái hiện tại là idempotent: trả `200` và không ghi DB lại.
- Nhảy cóc hoặc đi ngược chu trình trả `400`.
- Migration V39 chuẩn hóa dữ liệu housekeeping legacy về `CLEAN` trước khi thu hẹp enum database còn ba giá trị.
- Checkout chưa nằm trong task này; integration tương lai phải gọi cùng logic chuyển trạng thái thay vì cập nhật DB trực tiếp.

## 6. Room occupancy theo ngày

`GET /api/rooms/occupancy?date=YYYY-MM-DD` trả các phòng đang có booking hiệu lực trong khoảng nửa mở `[checkInDate, checkOutDate)`. Nếu bỏ `date`, backend dùng ngày hiện tại theo timezone khách sạn.

- `PENDING + RESERVED` ánh xạ thành `HELD`.
- `CONFIRMED + RESERVED` ánh xạ thành `RESERVED`.
- `CHECKED_IN + OCCUPIED` ánh xạ thành `OCCUPIED`.
- Phòng không có booking hiệu lực vẫn được trả về với `bookingStatus: null`.

Occupancy chỉ mô tả booking trong ngày và không thay đổi logic availability hoặc housekeeping.

## 7. Flow upload ảnh qua MinIO

Bucket mặc định là `room-images`. Backend tự kiểm tra và tạo bucket khi có thao tác storage đầu tiên; application context không cần kết nối MinIO lúc khởi động.

### Bước 1 — Cấp upload URL

Request:

```json
{
  "fileName": "deluxe-sea.jpg",
  "contentType": "image/jpeg",
  "fileSize": 5242880
}
```

1. Service kiểm tra phòng tồn tại và chưa đủ giới hạn 20 ảnh.
2. Chỉ nhận JPEG, PNG, WebP; phần mở rộng file phải khớp MIME.
3. Kích thước khai báo phải từ 1 byte đến 10 MB.
4. Server sinh `uploadId` UUID và object key nội bộ `rooms/{roomId}/{uploadId}.{ext}`.
5. MinIO trả presigned PUT URL hiệu lực 1 giờ. Client phải gửi đúng các header trong `requiredHeaders`, bao gồm `Content-Type`.

### Bước 2 — Client upload trực tiếp

Client PUT bytes ảnh vào `uploadUrl`. File không đi qua Spring Boot, giúp tránh giữ file lớn trong bộ nhớ backend. Client không được tự gửi đường dẫn object cho backend.

### Bước 3 — Xác nhận upload

Request chỉ gửi lại UUID và alt text:

```json
{
  "uploadId": "adff950e-0b93-493e-a821-29386dabcc55",
  "altText": "Phòng Deluxe hướng biển"
}
```

1. Service dựng các object key hợp lệ từ `room.id`, `uploadId` và ba extension được hỗ trợ; không dùng path từ client.
2. Transaction khóa dòng phòng khi confirm/reorder để hai request đồng thời không cùng tạo ảnh primary hoặc vượt giới hạn số ảnh.
3. Service đọc metadata trực tiếp từ MinIO để tìm object vừa upload.
4. MIME thực tế phải khớp extension, kích thước thực tế phải từ 1 byte đến 10 MB.
5. Object sai MIME/kích thước hoặc vượt giới hạn bị xóa best-effort và request trả `400`.
6. Service từ chối xác nhận lặp cùng object bằng `409`.
7. Ảnh được append cuối danh sách. Ảnh đầu tiên của phòng tự có `isPrimary = true`.
8. DB chỉ lưu URI nội bộ và `storageKey`; response chỉ lộ UUID và presigned download URL.

### Sắp xếp ảnh

```json
{
  "imageIds": [
    "adff950e-0b93-493e-a821-29386dabcc55",
    "65d69162-c9e6-43dc-8ad0-32e564127264"
  ]
}
```

- Danh sách phải chứa đúng và đủ mọi ảnh hiện có của phòng.
- Không nhận UUID trùng, thiếu, thừa hoặc vượt giới hạn 20 phần tử.
- Service gán `sortOrder = 0..n-1`.
- Chỉ ảnh đầu tiên có `isPrimary = true`; mọi ảnh còn lại là `false`.

## 8. Cấu hình MinIO

| Biến môi trường | Mục đích | Giá trị mặc định |
| --- | --- | --- |
| `MINIO_ENDPOINT` | Endpoint S3/MinIO | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | Access key backend | lấy từ `MINIO_ROOT_USER` nếu có |
| `MINIO_SECRET_KEY` | Secret key backend | lấy từ `MINIO_ROOT_PASSWORD` nếu có |
| `MINIO_ROOM_IMAGES_BUCKET` | Bucket ảnh phòng | `room-images` |

Credential không được hard-code. Nếu MinIO không khả dụng, adapter log lỗi kèm ngữ cảnh object/room phù hợp, bọc bằng custom exception và API trả `503` với thông báo an toàn.

## 9. Bảng lỗi API

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | DTO/query enum sai; Room Type inactive; giá âm; tiện nghi không tồn tại/không filterable; housekeeping transition sai; ảnh sai MIME, size, extension hoặc order |
| `401 Unauthorized` | Không có hoặc access token không hợp lệ |
| `403 Forbidden` | Principal thiếu permission tương ứng |
| `404 Not Found` | Không tìm thấy phòng, Room Type hoặc object upload |
| `409 Conflict` | Trùng `roomNumber`, xác nhận lặp object hoặc vi phạm constraint dữ liệu |
| `503 Service Unavailable` | MinIO/bucket tạm thời không khả dụng |

## 10. Kiểm thử

- Unit test `RoomService`: chuẩn hóa và mặc định khi tạo, duplicate, Room Type inactive, reassign, soft-delete, filter validation và state machine.
- Repository test: xác nhận bộ lọc tiện nghi dùng hợp Room Type/phòng và semantics AND.
- Unit test `RoomImageService`: presign, MIME/extension/size/count, UUID/key isolation, confirm metadata, cleanup object sai và reorder/primary.
- Security/controller test: `401`, `403`, permission theo endpoint, validation enum, contract và status code.
- Full suite chạy bằng `mvn test`; MinIO được mock trong unit test nên test tự động không phụ thuộc Docker.

# BE-3.4 — Amenity CRUD + Filtering

Tài liệu này mô tả flow backend của BE-3.4: quản lý danh mục tiện nghi và cung cấp danh sách lựa chọn cho bộ lọc phòng. Tất cả endpoint yêu cầu Bearer access token hợp lệ. Permission được kiểm tra tại cả controller và `AmenityService`.

## 1. Phạm vi và mô hình dữ liệu

Amenity là master data dùng chung cho hai cấp:

- `room_type_amenities`: tiện nghi mặc định của một loại phòng.
- `room_amenities`: tiện nghi riêng của một phòng cụ thể.

Tiện nghi hiệu lực của phòng vẫn được BE-3.2 tính bằng hợp của hai tập trên. BE-3.4 chỉ quản lý danh mục `amenities`; không thêm endpoint gán tiện nghi cho Room Type hoặc Room.

Mỗi tiện nghi thuộc đúng một category:

- `ROOM`
- `BATHROOM`
- `TECH`
- `SERVICE`

`isFilterable` quyết định tiện nghi có xuất hiện trong danh sách filter options hay không. Giá trị này không ảnh hưởng việc tiện nghi được gán cho Room Type/Room hoặc xuất hiện trong dữ liệu chi tiết phòng.

Schema `amenities` không có `is_active` hoặc `deleted_at`. Vì vậy thao tác xóa trong BE-3.4 là hard-delete. Hai khóa ngoại từ `room_type_amenities` và `room_amenities` dùng `ON DELETE CASCADE`, nên các liên kết tới tiện nghi bị xóa cũng được gỡ khỏi database.

## 2. API contract

| Method | Endpoint | Permission | Kết quả thành công |
| --- | --- | --- | --- |
| `GET` | `/api/amenities` | `room:read` | `200`, toàn bộ danh mục tiện nghi |
| `GET` | `/api/amenities/filter-options` | `room:read` | `200`, chỉ các tiện nghi có `is_filterable=true` |
| `GET` | `/api/amenities/{code}` | `room:read` | `200`, chi tiết tiện nghi theo code |
| `POST` | `/api/amenities` | `room:create` | `201`, tiện nghi vừa tạo và header `Location` |
| `PUT` | `/api/amenities/{code}` | `room:update` | `200`, tiện nghi sau khi cập nhật |
| `DELETE` | `/api/amenities/{code}` | `room:delete` | `204`, tiện nghi và các liên kết đã bị xóa |

Danh sách được sắp xếp theo `category`, `sortOrder`, `name`, sau đó `code`. BE-3.4 chưa thêm pagination hoặc tìm kiếm vì task không yêu cầu.

## 3. Request và response

### Tạo tiện nghi

```json
{
  "code": "RAIN_SHOWER",
  "name": "Rain shower",
  "icon": "shower-head",
  "category": "BATHROOM",
  "isFilterable": true,
  "sortOrder": 30
}
```

Quy tắc dữ liệu:

- `code` bắt buộc, tối đa 40 ký tự, chỉ nhận chữ, số và `_`.
- Service trim và chuyển `code` thành uppercase trước khi kiểm tra trùng hoặc lưu.
- `code` là định danh public và không đổi qua API update.
- `name` bắt buộc, được trim và tối đa 120 ký tự.
- `icon` nullable, được trim, tối đa 60 ký tự; chuỗi chỉ chứa khoảng trắng được lưu thành `null`.
- `category` bắt buộc và chỉ nhận bốn enum của BE-3.4.
- Khi tạo, bỏ `isFilterable` thì mặc định là `true`, đồng nhất với default MySQL.
- Khi tạo, bỏ `sortOrder` thì mặc định là `0`; nếu có thì phải không âm.

Response chi tiết:

```json
{
  "code": "RAIN_SHOWER",
  "name": "Rain shower",
  "icon": "shower-head",
  "category": "BATHROOM",
  "isFilterable": true,
  "sortOrder": 30,
  "createdAt": "2026-08-20T10:00:00Z",
  "updatedAt": "2026-08-20T10:00:00Z"
}
```

Khi tạo thành công, API trả:

```http
Location: /api/amenities/RAIN_SHOWER
```

### Cập nhật tiện nghi

```json
{
  "name": "Premium rain shower",
  "icon": "shower-head",
  "category": "BATHROOM",
  "isFilterable": false,
  "sortOrder": 40
}
```

`PUT` là cập nhật đầy đủ các trường có thể sửa. `name`, `category`, `isFilterable` và `sortOrder` là bắt buộc; `icon` có thể là `null`. `code` lấy từ path và không nhận trong body.

### Filter options

```http
GET /api/amenities/filter-options
```

Ví dụ response:

```json
[
  {
    "code": "BALCONY",
    "name": "Balcony",
    "icon": "door-open",
    "category": "ROOM",
    "sortOrder": 50
  },
  {
    "code": "WIFI",
    "name": "Wi-Fi",
    "icon": "wifi",
    "category": "TECH",
    "sortOrder": 10
  }
]
```

Response filter options không trả khóa `BIGINT`, timestamps hoặc `isFilterable` vì mọi phần tử trong danh sách đã được đảm bảo có `is_filterable=true`.

## 4. Flow tạo tiện nghi

1. Spring Security xác thực access token và yêu cầu `room:create` tại controller.
2. Controller chạy Bean Validation cho request body.
3. `AmenityService` kiểm tra lại `room:create`, chuẩn hóa `code`, `name` và `icon`.
4. Repository kiểm tra code không phân biệt hoa thường. Code đã tồn tại trả `409 Conflict`.
5. Service áp dụng default `isFilterable=true` và `sortOrder=0` nếu client không gửi.
6. `saveAndFlush()` ghi bản ghi ngay trong request hiện tại. API trả `201 Created`, body chi tiết và header `Location`.

Unique constraint `uk_amenities_code` trong MySQL vẫn là lớp bảo vệ cuối nếu hai request đồng thời cùng tạo một code. Vi phạm constraint được global exception handler trả thành `409 Conflict`.

## 5. Flow cập nhật tiện nghi

1. Endpoint yêu cầu `room:update` tại controller và service.
2. Service trim/uppercase code từ path rồi tìm không phân biệt hoa thường; không tồn tại trả `404 Not Found`.
3. Service cập nhật `name`, `icon`, `category`, `isFilterable` và `sortOrder`; code không thay đổi.
4. `saveAndFlush()` ghi dữ liệu và API trả response chi tiết mới.
5. Nếu đổi `isFilterable` từ `true` sang `false`, tiện nghi biến mất khỏi `/filter-options` ngay sau transaction, nhưng các liên kết Room Type/Room hiện có được giữ nguyên.

## 6. Flow xóa tiện nghi

1. Endpoint yêu cầu `room:delete` tại controller và service.
2. Service tìm tiện nghi bằng code đã chuẩn hóa; không tồn tại trả `404 Not Found`.
3. Repository hard-delete bản ghi và flush trong request hiện tại.
4. MySQL tự xóa các dòng liên kết tương ứng trong `room_type_amenities` và `room_amenities` qua `ON DELETE CASCADE`.
5. API trả `204 No Content`.

Xóa Amenity không xóa Room Type hoặc Room. Vì không có soft-delete trong schema, một code đã xóa có thể được tạo lại sau đó như một master-data record mới.

## 7. Flow lấy filter options

1. Endpoint yêu cầu `room:read`; role `CUSTOMER` hiện có permission này nên có thể lấy lựa chọn bộ lọc sau khi đăng nhập.
2. Repository chỉ query các dòng `is_filterable=true` và áp dụng thứ tự ổn định.
3. Service map sang DTO filter rút gọn gồm `code`, `name`, `icon`, `category`, `sortOrder`.
4. Client có thể nhóm các option theo `category` và gửi các code đã chọn vào `GET /api/rooms?amenityCodes=...` của BE-3.2.

BE-3.2 tiếp tục kiểm tra mọi `amenityCodes` dùng để lọc đều tồn tại và đang filterable. Vì vậy một client dùng option cũ sau khi Admin bỏ cờ `isFilterable` sẽ nhận `400 Bad Request` thay vì lọc bằng dữ liệu đã lỗi thời.

## 8. RBAC

| Thao tác | Permission |
| --- | --- |
| Xem danh mục, chi tiết, filter options | `room:read` |
| Tạo tiện nghi | `room:create` |
| Sửa tiện nghi | `room:update` |
| Xóa tiện nghi | `room:delete` |

Không thêm permission riêng cho Amenity vì đây là master data thuộc module Room/Inventory và bốn permission trên đã được dùng cho Room Type và Room.

## 9. Bảng lỗi API

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Body sai định dạng; code/name trống; code sai pattern; enum category không hỗ trợ; text quá dài; sortOrder âm; thiếu trường bắt buộc khi update |
| `401 Unauthorized` | Không có hoặc access token không hợp lệ |
| `403 Forbidden` | Principal thiếu permission tương ứng |
| `404 Not Found` | Không tìm thấy Amenity theo code |
| `409 Conflict` | Trùng code không phân biệt hoa thường hoặc unique constraint phát hiện request đồng thời |

## 10. Kiểm thử

- Unit test `AmenityService`: chuẩn hóa text/code, default, duplicate, update, filter options, not found và hard-delete.
- Repository test: chỉ lấy tiện nghi filterable và giữ đúng thứ tự `sortOrder` trong mỗi category.
- Security/controller test: `401`, `403`, bốn permission Room, validation request, status code, `Location` và contract filter options.
- Full suite chạy bằng `mvn test`. Không cần MinIO hoặc Flyway migration mới cho BE-3.4 vì task chỉ dùng schema inventory đã có trong V1.

# FE-3.1 — Admin Room Types Management

Tài liệu này mô tả flow quản lý loại phòng tại `/admin/room-types`: tải danh sách, tìm kiếm, phân trang, tạo/sửa cấu hình, gán tiện nghi, upload ảnh qua MinIO và soft-delete. Trang dùng design system và `AdminLayout` hiện có của frontend.

## 1. Phạm vi

FE-3.1 gồm:

- Danh sách Room Type chưa soft-delete, tìm kiếm theo code/tên và phân trang phía client.
- Form tạo/sửa thông tin bán phòng, sức chứa, cấu hình giường và tiện nghi.
- Upload nhiều ảnh trực tiếp lên bucket MinIO `room-type-images` bằng presigned URL.
- Confirm dialog trước khi soft-delete.
- Ẩn/hiện thao tác theo permission trong access token.

Task chưa quản lý xóa ảnh, đổi ảnh chính hoặc kéo thả sắp xếp ảnh. Backend vẫn là lớp kiểm tra authorization và validation cuối cùng.

## 2. Route và permission

| Chức năng | API | Permission |
| --- | --- | --- |
| Xem danh sách Room Type và Amenities | `GET /api/room-types`, `GET /api/amenities` | `room:read` |
| Xem thống kê kể cả Room Type soft-delete | `GET /api/room-types/stats` | `room:read` |
| Tạo Room Type | `POST /api/room-types` | `room:create` |
| Sửa thông tin chung | `PUT /api/room-types/{code}` | `room:update` |
| Thay cấu hình giường | `PUT /api/room-types/{code}/beds` | `room:update` |
| Thay tiện nghi | `PUT /api/room-types/{code}/amenities` | `room:update` |
| Cấp URL/xác nhận upload ảnh | `POST /api/room-types/{code}/images/upload-url`, `POST /api/room-types/{code}/images/confirm` | `room:update` |
| Soft-delete | `DELETE /api/room-types/{code}` | `room:delete` |

Người dùng chưa đăng nhập được chuyển tới `/login?redirect=%2Fadmin%2Froom-types`. Người đã đăng nhập nhưng thiếu `room:read` thấy trạng thái “Không có quyền truy cập”. Các nút tạo, sửa, tải ảnh và xóa chỉ xuất hiện khi có permission tương ứng.

## 3. Flow danh sách, tìm kiếm và phân trang

1. Sau khi Auth Context hoàn tất khởi tạo, trang tải song song Room Type, Amenities và thống kê Room Type.
2. Trong lúc chờ, trang hiển thị skeleton. Lỗi API hiển thị error state và nút “Thử lại”.
3. Backend trả toàn bộ Room Type chưa soft-delete, gồm cả active và inactive.
4. Frontend tìm kiếm không phân biệt hoa thường trên `code` và `name`.
5. Kết quả được chia 10 dòng/trang. Khi nội dung tìm kiếm thay đổi, trang trở về trang 1.
6. Bảng hiển thị ảnh chính, code/tên, giường, sức chứa, diện tích, tiện nghi, giá/đêm, trạng thái và thao tác. Diện tích null hiển thị “Chưa cập nhật”.
7. Ba thẻ tổng quan dùng API thống kê: tổng số gồm cả bản ghi soft-delete, đang hoạt động chỉ gồm bản ghi chưa xóa và active, đã vô hiệu hóa gồm mọi bản ghi `is_active=false`.

Việc tìm kiếm và phân trang chỉ diễn ra trên dữ liệu đã tải; FE-3.1 không thêm query pagination/search cho backend.

## 4. Form tạo và sửa

Modal dùng React Hook Form và Zod. Các nhóm trường gồm:

- Thông tin: code, tên, mô tả, giá cơ bản, currency, giá giường phụ, diện tích, trạng thái và thứ tự hiển thị.
- Sức chứa: tổng khách, người lớn tối đa và trẻ em tối đa.
- Beds: từ 1–6 loại trong `SINGLE`, `DOUBLE`, `QUEEN`, `KING`, `SOFA_BED`, `BUNK`; mỗi loại chỉ xuất hiện một lần và tổng không quá 10 giường.
- Amenities: multi-select toàn bộ danh mục từ `/api/amenities`, nhóm theo `ROOM`, `BATHROOM`, `TECH`, `SERVICE`.
- Images: JPEG/PNG/WebP, tối đa 10 MB/ảnh và tổng tối đa 20 ảnh.

Modal giới hạn chiều cao theo viewport. Header và footer không co lại; chỉ nội dung giữa cuộn nên các nút Hủy/Tạo/Lưu luôn hiển thị đầy đủ, kể cả trên màn hình thấp hoặc khi form có nhiều ảnh.

Migration V8 backfill diện tích cho dữ liệu seed còn null: `STD=24 m²`, `DLX=35 m²`, `SUITE=50 m²`. Dữ liệu đã có diện tích không bị ghi đè và Room Type khác vẫn được phép để trống.

### Tạo mới

1. Admin nhập form; code được chuyển uppercase và chỉ nhận chữ, số, `_`.
2. Frontend validate toàn bộ form trước khi gửi.
3. `POST /api/room-types` gửi thông tin chung, beds và `amenityCodes` trong một request.
4. Sau khi nhận Room Type đã tạo, frontend mới bắt đầu upload các file đã chọn.
5. Thành công hoàn toàn: đóng modal, tải lại bảng và hiển thị toast.

### Chỉnh sửa

Code là định danh public nên bị khóa trên form edit. Khi lưu, frontend gọi tuần tự:

1. `PUT /api/room-types/{code}` cập nhật thông tin chung.
2. `PUT /api/room-types/{code}/beds` đồng bộ cấu hình giường theo `BedType`: cập nhật dòng hiện có, xóa loại không còn dùng và chỉ thêm loại mới. Cách này tránh unique conflict `(room_type_id, bed_type)` khi giữ nguyên loại giường nhưng đổi số lượng.
3. `PUT /api/room-types/{code}/amenities` thay toàn bộ tập tiện nghi.
4. Upload các ảnh mới, nếu có.
5. Tải lại danh sách để đồng bộ trạng thái cuối từ server.

Ba API update không nằm trong cùng transaction HTTP. Nếu một bước sau thất bại, dữ liệu của bước trước có thể đã được lưu; modal được giữ mở, danh sách được tải lại và toast cảnh báo khả năng cập nhật một phần.

## 5. Flow upload ảnh MinIO

Mỗi file thực hiện ba bước:

### Bước 1 — Cấp presigned URL

```json
{
  "fileName": "deluxe-sea.jpg",
  "contentType": "image/jpeg",
  "fileSize": 5242880
}
```

Backend kiểm tra Room Type, MIME, extension, kích thước và giới hạn số ảnh. Server sinh UUID cùng object key nội bộ trong bucket `room-type-images`:

```text
room-types/{roomTypeId}/{uploadId}.{ext}
```

Response trả `uploadId`, `uploadUrl`, `requiredHeaders` và thời điểm hết hạn; không trả object key.

### Bước 2 — Upload trực tiếp

Frontend gửi `PUT uploadUrl` với bytes file và đúng `requiredHeaders`. Request này đi thẳng tới MinIO và không mang JWT/API Authorization header.

### Bước 3 — Confirm

```json
{
  "uploadId": "e36dba15-f1ab-499e-93c8-948950e44546",
  "altText": "Deluxe hướng biển"
}
```

Backend khóa Room Type, tự dựng key từ ID nội bộ và UUID, đọc metadata thật từ MinIO rồi mới lưu `room_type_images`. Ảnh đầu tiên tự thành primary; các ảnh tiếp theo được append theo `sortOrder`.

Response ảnh chỉ gồm UUID, presigned download URL, alt text, primary và sort order. `storageKey` và khóa BIGINT không được lộ ra frontend.

Nếu một số file thất bại, Room Type và các ảnh confirm thành công vẫn được giữ. Modal chuyển sang edit, giữ riêng các file lỗi cùng message theo từng file để Admin retry; không upload lại file đã thành công. Ảnh thứ hai trở đi được append theo thứ tự và không thay đổi ảnh primary đầu tiên.

## 6. Flow soft-delete

1. Admin bấm “Xóa” trên một Room Type.
2. Dialog hiển thị tên/code và giải thích dữ liệu lịch sử vẫn được giữ.
3. Sau khi xác nhận, frontend gọi `DELETE /api/room-types/{code}`.
4. Backend đặt `isActive=false` và `deletedAt`; không xóa vật lý Room Type, ảnh hoặc dữ liệu booking lịch sử.
5. Frontend đóng dialog, hiển thị toast và tải lại cả danh sách lẫn thống kê. Bản ghi đã soft-delete không còn xuất hiện trong bảng nhưng số “Đã vô hiệu hóa” tăng và vẫn đúng sau khi reload trang.

## 7. Trạng thái và lỗi

| HTTP/tình huống | Xử lý frontend |
| --- | --- |
| `400 Bad Request` | Hiển thị message validation từ backend; giữ modal để sửa |
| `401 Unauthorized` | Auth không hợp lệ; backend từ chối request |
| `403 Forbidden` | Hiển thị lỗi permission; các action bình thường đã được ẩn theo quyền |
| `404 Not Found` | Thông báo Room Type/Amenity/object upload không còn tồn tại và refetch |
| `409 Conflict` | Thông báo code trùng hoặc upload đã confirm |
| `503 Service Unavailable` | Thông báo storage tạm thời không khả dụng; giữ file lỗi để retry |
| MinIO PUT thất bại | Không gọi confirm cho file đó; giữ file ở trạng thái chờ tải |
| Danh sách rỗng | Hiển thị empty state thay cho bảng |

## 8. Kiểm thử

- Backend unit/integration test xác nhận đồng bộ beds không vi phạm unique key, thống kê soft-delete, presign, MIME/extension/size/count, UUID/key isolation, confirm metadata, ảnh thứ hai, cleanup object sai và primary duy nhất.
- Security test xác nhận endpoint ảnh yêu cầu authentication và `room:update`.
- Frontend được kiểm tra bằng ESLint và production build của Next.js.
- Smoke test tích hợp khi MinIO khả dụng: tạo Room Type → lấy presigned URL → PUT file → confirm → tải lại danh sách và hiển thị ảnh.

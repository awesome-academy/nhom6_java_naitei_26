# FE-3.2 — Admin Rooms Management + Floor Map

Tài liệu này mô tả flow trang quản lý phòng tại `/admin/rooms`: tải dữ liệu thật từ backend, lọc phòng, xem dạng bảng hoặc sơ đồ tầng, mở side panel chi tiết và tạo/sửa phòng. Trang dùng lại `AdminLayout`, `DataTable`, form, dialog, card, badge, tabs, toast và design token hiện có.

## 1. Phạm vi

FE-3.2 gồm:

- Danh sách phòng và bộ lọc theo loại phòng, tầng, view, trạng thái housekeeping.
- Tìm kiếm theo số phòng hoặc tên loại phòng và phân trang phía client, 10 dòng/trang.
- Sơ đồ tầng dạng grid, dùng cùng kết quả lọc với bảng.
- Side panel chi tiết khi bấm một phòng từ bảng hoặc sơ đồ.
- Modal tạo/sửa `roomTypeCode`, `viewType`, `floor`, `priceOverride`.
- Loading skeleton, empty state, error/retry, toast mutation và ẩn/hiện action theo permission.

Task không thêm thao tác đổi housekeeping, đổi operational status, soft-delete hay upload ảnh phòng. Các API tương ứng đã có ở backend nhưng thuộc flow quản trị khác; side panel FE-3.2 chỉ hiển thị các trạng thái và ảnh hiện có.

## 2. Route, API và permission

| Chức năng | API | Permission |
| --- | --- | --- |
| Tải danh sách phòng | `GET /api/rooms` | `room:read` |
| Tải loại phòng để hiển thị giá và option form | `GET /api/room-types` | `room:read` |
| Tạo phòng | `POST /api/rooms` | `room:create` |
| Sửa phòng | `PUT /api/rooms/{roomNumber}` | `room:update` |

Người dùng chưa đăng nhập được chuyển tới `/login?redirect=%2Fadmin%2Frooms`. Người dùng đã đăng nhập nhưng thiếu `room:read` thấy trang “Không có quyền truy cập”. Nút tạo chỉ hiện với `room:create`; nút sửa trong bảng và side panel chỉ hiện với `room:update`. Backend vẫn là lớp authorization cuối cùng.

## 3. Flow tải dữ liệu và lọc

1. Sau khi Auth Context xác nhận phiên đăng nhập, frontend gọi song song `GET /api/rooms` và `GET /api/room-types`.
2. Trong lúc chờ, trang hiển thị skeleton. Nếu một request lỗi, trang hiển thị message và nút “Thử lại”.
3. Frontend tạo bốn thẻ thống kê: tổng phòng, `CLEAN`, `DIRTY`, `CLEANING`.
4. Bộ lọc được áp dụng phía client trên dữ liệu đã tải:
   - Loại phòng: so khớp `roomTypeCode`.
   - Tầng: so khớp số tầng; có option riêng cho phòng chưa gán tầng.
   - View: `SEA`, `CITY`, `GARDEN`, `POOL`, `MOUNTAIN`, `NONE`.
   - Housekeeping: `CLEAN`, `DIRTY`, `CLEANING`, `INSPECTED`.
5. Các điều kiện kết hợp bằng `AND`. Tìm kiếm không phân biệt hoa thường trên `roomNumber` và `roomTypeName`.
6. Khi thay đổi hoặc xóa bộ lọc, phân trang bảng trở về trang 1.
7. Bảng và sơ đồ tầng dùng cùng `filteredRooms`, do đó kết quả giữa hai tab luôn nhất quán.

FE-3.2 không thêm API pagination/filter mới vì endpoint hiện có trả toàn bộ phòng chưa soft-delete và tập dữ liệu quản trị được xử lý trên client.

## 4. Danh sách phòng

Bảng hiển thị:

- Số phòng.
- Tên/code loại phòng.
- Tầng và view.
- Trạng thái vận hành.
- Trạng thái housekeeping.
- Giá hiệu lực để hiển thị: `priceOverride` nếu có, nếu không dùng `room_types.basePrice`.
- Action sửa khi có `room:update`.

Bấm vào hàng mở side panel chi tiết. Bấm action sửa không mở panel mà đi thẳng vào modal edit. Danh sách được chia 10 phòng/trang; khi bộ lọc không có kết quả, bảng hiển thị empty state.

## 5. Sơ đồ tầng và quy ước màu

Frontend nhóm phòng theo `floor`, sắp tầng tăng dần và đặt nhóm “Chưa gán tầng” ở cuối. Trong mỗi tầng, phòng được hiển thị bằng tile chứa số phòng, loại phòng và trạng thái housekeeping.

| Housekeeping status | Màu tile | Ý nghĩa |
| --- | --- | --- |
| `CLEAN` | Xanh lá | Phòng sạch |
| `DIRTY` | Đỏ | Phòng bẩn, cần dọn |
| `CLEANING` | Cam | Housekeeping đang dọn |
| `INSPECTED` | Xám trung tính | Trạng thái có trong schema nhưng không thuộc chu trình cập nhật BE-3.2 |

Nếu `operationalStatus` khác `ACTIVE`, tile vẫn giữ màu housekeeping để không phá vỡ quy ước màu và hiển thị thêm biểu tượng bảo trì. Click tile mở side panel của đúng phòng.

## 6. Side panel chi tiết

Panel trượt từ bên phải và hiển thị:

- Ảnh primary của phòng; nếu phòng chưa có ảnh riêng thì dùng ảnh primary/đầu tiên của Room Type, và chỉ hiển thị placeholder khi cả hai nguồn đều không có ảnh.
- Số phòng, tên/code loại phòng.
- Operational status và housekeeping status.
- Tầng, view, giá/đêm và nguồn giá.
- Tiện nghi hiệu lực backend trả về từ hợp `room_type_amenities ∪ room_amenities`.
- Nút “Chỉnh sửa phòng” nếu người dùng có `room:update`.

Sau khi lưu edit và refetch thành công, dữ liệu danh sách được lấy lại từ server. Nếu panel đang mở cho một phòng, frontend đồng bộ panel theo `roomNumber`; nếu phòng không còn trong response thì đóng panel.

## 7. Flow tạo phòng

Request mẫu:

```json
{
  "roomNumber": "A-301",
  "roomTypeCode": "DLX",
  "viewType": "SEA",
  "floor": 3,
  "priceOverride": 1800000
}
```

1. Admin bấm “Thêm phòng”; nút chỉ bật khi có ít nhất một Room Type active.
2. Modal chỉ đưa Room Type active vào danh sách chọn.
3. Frontend validate form bằng React Hook Form và Zod:
   - `roomNumber` bắt buộc, tối đa 20 ký tự, chỉ nhận chữ, số, `_`, `-`.
   - `roomTypeCode` bắt buộc.
   - `viewType` thuộc enum cố định; mặc định `NONE`.
   - `floor` nullable nhưng nếu nhập phải là số nguyên.
   - `priceOverride` nullable, không âm và tối đa 2 chữ số thập phân. Input được giữ ở dạng chuỗi để trường rỗng không bị trình duyệt ép thành số `0`.
4. `roomNumber` và `roomTypeCode` được trim/uppercase trước khi gửi.
5. Frontend gọi `POST /api/rooms`. Backend tạo mặc định `operationalStatus=ACTIVE`, `housekeepingStatus=CLEAN`, `isActive=true`.
6. Thành công: refetch dữ liệu, hiển thị toast và đóng modal. Thất bại: giữ modal để sửa và hiển thị message backend.

## 8. Flow sửa phòng

1. Admin bấm “Sửa” trong bảng hoặc từ side panel.
2. `roomNumber` là định danh public nên bị khóa và không có trong request update.
3. Form cho sửa đúng bốn trường backend hỗ trợ: Room Type, view, tầng và giá riêng.
4. Room Type active được phép chọn. Nếu Room Type hiện tại đã bị vô hiệu hóa sau khi phòng được tạo, option hiện tại vẫn được giữ để Admin có thể sửa trường khác mà không bị buộc đổi loại phòng.
5. Frontend gọi `PUT /api/rooms/{roomNumber}` với payload:

```json
{
  "roomTypeCode": "DLX",
  "viewType": "CITY",
  "floor": 3,
  "priceOverride": null
}
```

6. `priceOverride=null` nghĩa là quay lại dùng giá cơ bản của Room Type.
   Form hiển thị giá cơ bản hiện tại bên dưới input nhưng không tự điền giá đó, tránh vô tình tạo price override.
7. Thành công: refetch, toast và đóng modal. Lỗi: giữ nguyên form và hiển thị toast.

## 9. Trạng thái lỗi và UI

| HTTP/tình huống | Xử lý frontend |
| --- | --- |
| `400 Bad Request` | Hiển thị message validation từ backend và giữ modal mở |
| `401 Unauthorized` | Phiên không hợp lệ; backend từ chối request, route guard đưa người dùng về login khi Auth Context mất phiên |
| `403 Forbidden` | Hiển thị lỗi permission; action đã được ẩn theo permission phía client |
| `404 Not Found` | Thông báo phòng hoặc Room Type không còn tồn tại; người dùng có thể retry/refetch |
| `409 Conflict` | Hiển thị lỗi trùng `roomNumber` hoặc conflict dữ liệu |
| Danh sách rỗng | Hiển thị empty state trong bảng và sơ đồ tầng |
| Không có Room Type active | Vô hiệu hóa nút tạo phòng; edit vẫn giữ được Room Type hiện tại |

## 10. Kiểm thử

- Kiểm tra `npm run lint` không sinh lỗi mới.
- Kiểm tra `npm run build` để xác nhận TypeScript và production bundle.
- Kiểm tra thủ công các trạng thái loading/error/empty; bốn bộ lọc; phân trang; chuyển tab bảng/sơ đồ.
- Kiểm tra màu tile cho `CLEAN`, `DIRTY`, `CLEANING` và fallback `INSPECTED`.
- Kiểm tra click row/tile mở đúng panel; permission-based create/edit.
- Kiểm tra create/edit gửi đúng contract, refetch và hiển thị lỗi `400/401/403/404/409`.
- Kiểm tra giá để trống gửi `null`, giá `0` chỉ được gửi khi nhập rõ ràng, và giá tùy chỉnh hợp lệ được chuyển sang number.
- Kiểm tra side panel ưu tiên ảnh phòng, fallback sang ảnh Room Type rồi mới dùng placeholder.

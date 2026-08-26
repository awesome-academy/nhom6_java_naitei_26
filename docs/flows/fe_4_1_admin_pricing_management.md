# FE-4.1 — Admin Pricing Management

Tài liệu này mô tả flow trang `/admin/pricing`: xem các rate override đang hoạt động, tạo rule giá theo loại phòng và xem giá hiệu lực từng ngày trên calendar tháng. Giao diện dùng lại `AdminLayout`, `DataTable`, Dialog, Tabs, Card, Badge, Checkbox, Select, Sheet, Skeleton và design token của các trang Admin trước đó.

## 1. Phạm vi

FE-4.1 gồm:

- Bảng rate override đang hoạt động, tìm kiếm, lọc theo Room Type và phân trang client-side 10 dòng.
- Modal tạo override ở cấp Room Type bằng định danh public `roomTypeCode`.
- Chọn tập ngày áp dụng trong tuần theo ISO weekday `1..7`, tương ứng T2–CN.
- Calendar ma trận Room Type × toàn bộ ngày trong tháng.
- Tính giá hiển thị theo rule Room Type có priority cao nhất; không có rule thì dùng `basePrice`.
- Side panel giải thích rule tạo nên giá của ô calendar.
- Loading, empty, error/retry state và kiểm soát truy cập bằng `pricing:manage`.

Task không triển khai edit/delete override. Mọi rule đều áp dụng ở cấp Room Type.

## 2. Route, API và permission

| Chức năng | API | Permission |
| --- | --- | --- |
| Tải override active | `GET /api/rate-overrides` | `pricing:manage` |
| Tải Room Type | `GET /api/room-types` | `room:read` |
| Tạo override theo Room Type | `POST /api/rate-overrides/room-types/{roomTypeCode}` | `pricing:manage` |

Người chưa đăng nhập được chuyển tới `/login?redirect=%2Fadmin%2Fpricing`. Người đã đăng nhập nhưng thiếu `pricing:manage` thấy trạng thái không có quyền truy cập. Backend tiếp tục kiểm tra permission tại controller và service; việc ẩn/chặn action ở frontend không thay thế authorization phía server.

Theo giả định của task, Admin truy cập trang có cả `pricing:manage` và `room:read` để tải danh mục Room Type.

## 3. Contract public của Rate Override

Frontend không cần biết BIGINT của Room Type hoặc Room. Response dùng thông tin target public:

```json
{
  "id": 88,
  "roomTypeCode": "DLX",
  "roomTypeName": "Deluxe",
  "name": "Cuối tuần mùa hè",
  "startDate": "2026-08-01",
  "endDate": "2026-09-01",
  "price": 1500000.00,
  "weekdays": [6, 7],
  "priority": 10,
  "isActive": true,
  "createdAt": "2026-08-20T10:00:00+07:00",
  "updatedAt": "2026-08-20T10:00:00+07:00"
}
```

`id` là định danh của chính Rate Override và được giữ để định danh resource. Target luôn là Room Type, dùng `roomTypeCode`/`roomTypeName`. Response không trả `roomId` hoặc `roomNumber`.

## 4. Flow tải danh sách

1. Sau khi Auth Context hoàn tất, frontend tải song song active override và Room Type.
2. Trong lúc tải, trang hiển thị skeleton. Lỗi API hiển thị error state và nút “Thử lại”.
3. Ba thẻ tổng quan đếm tổng rule, số Room Type có rule và priority cao nhất.
4. Tab “Danh sách” tìm không phân biệt hoa thường theo tên rule hoặc code/tên Room Type.
5. Bộ lọc Room Type chọn chính xác theo `roomTypeCode`.
6. Kết quả được phân trang client-side 10 dòng và trở về trang 1 khi search/filter thay đổi.
7. Click một dòng mở side panel đọc chi tiết rule.

Bảng hiển thị tên/ID rule, target public, khoảng ngày, weekdays, giá, priority và trạng thái. Endpoint list chỉ trả rule active nên trạng thái trên trang là “Đang hoạt động”.

## 5. Flow tạo override

Request mẫu:

```http
POST /api/rate-overrides/room-types/DLX
Content-Type: application/json
```

```json
{
  "name": "Cuối tuần mùa hè",
  "startDate": "2026-08-01",
  "endDate": "2026-09-01",
  "price": 1500000,
  "weekdays": [6, 7],
  "priority": 10
}
```

Form dùng React Hook Form và Zod:

- Tên bắt buộc, được trim và tối đa 120 ký tự.
- Room Type bắt buộc. Room Type inactive vẫn được hiển thị kèm nhãn vì backend cho phép cấu hình master data chưa soft-delete.
- `startDate`/`endDate` dùng ISO date và bắt buộc `endDate > startDate`.
- Giá không âm, tối đa 12 chữ số phần nguyên và 2 chữ số thập phân.
- Priority là số nguyên, mặc định `0`.
- Phải chọn ít nhất một weekday. Khi chọn đủ T2–CN, frontend gửi `weekdays=null`; null có nghĩa rule áp dụng mọi ngày.

Khoảng ngày dùng quy ước nửa mở `[startDate, endDate)`. Rule `2026-08-01 → 2026-09-01` áp dụng tới hết 31/08 và không áp dụng ngày 01/09. Hai rule có khoảng ngày tiếp giáp không overlap.

Backend chuẩn hóa `roomTypeCode` bằng trim và uppercase, tìm Room Type chưa soft-delete rồi dùng chung validation/conflict logic của endpoint CRUD bằng ID hiện có. Tạo thành công trả `201 Created`, `Location: /api/rate-overrides/{id}` và response public ở trên.

Sau khi tạo thành công, frontend hiển thị toast, đóng modal, refetch dữ liệu và đưa calendar tới tháng chứa `startDate`.

## 6. Conflict preflight

Hai rule chỉ conflict khi đồng thời thỏa cả bốn điều kiện:

1. Cùng target Room Type.
2. Cùng priority.
3. Hai khoảng ngày overlap theo quy tắc nửa mở.
4. Trong phần ngày overlap tồn tại ít nhất một ngày thuộc tập weekday của cả hai rule.

Ví dụ:

- Cùng Room Type, cùng priority, một rule T7 và một rule CN: được phép vì không có ngày áp dụng chung.
- Cùng Room Type, khác priority: được phép overlap; rule priority cao hơn thắng.
- Cùng priority và cùng T7 nhưng khoảng ngày chỉ overlap vào ngày T2: được phép.
- Rule kết thúc đúng ngày rule khác bắt đầu: được phép.

Frontend chạy cùng thuật toán trên danh sách active đã tải để phản hồi sớm. Việc kiểm tra chỉ cần duyệt tối đa bảy ngày đầu trong phần overlap vì lịch weekday lặp lại mỗi bảy ngày. Dữ liệu có thể thay đổi sau preflight, do đó backend vẫn là lớp quyết định cuối và trả `409 Conflict` nếu request thực tế bị trùng.

## 7. Flow calendar giá

Calendar mặc định mở tháng hiện tại và hiển thị toàn bộ 28–31 ngày:

- Mỗi hàng là một Room Type; mỗi cột là một ngày.
- Header ngày và cột Room Type được sticky; vùng dữ liệu cuộn ngang/dọc.
- Ngày hiện tại và cuối tuần được highlight.
- Room Type inactive vẫn xuất hiện với badge để Admin xem/cấu hình dữ liệu master.
- Search và bộ lọc Room Type được áp dụng client-side lên các hàng calendar.

Với mỗi `(roomType, date)`, frontend:

1. Lọc rule có cùng `roomTypeCode`.
2. Giữ rule thỏa `startDate <= date < endDate`.
3. Giữ rule có `weekdays=null` hoặc chứa ISO weekday của ngày.
4. Chọn rule có `priority` lớn nhất.
5. Có rule: hiển thị `rule.price`, highlight màu xanh và priority.
6. Không có rule: hiển thị `roomType.basePrice` bằng màu trung tính.

Click ô override mở panel tên rule, target, khoảng ngày, weekdays, priority và giá. Ô base price chỉ có tooltip và không mở panel.

Đây là lịch giá cấp Room Type, không phải quote cuối cho một phòng cụ thể. Thứ tự tính đầy đủ của Rate Engine là:

```text
rate override hiệu lực → room.priceOverride → roomType.basePrice
```

Rate Override luôn áp dụng cho toàn bộ phòng thuộc Room Type đã chọn.

## 8. Xử lý lỗi

| HTTP/tình huống | Xử lý frontend |
| --- | --- |
| `400 Bad Request` | Hiển thị validation từ backend và giữ modal mở để sửa |
| `401 Unauthorized` | Phiên không hợp lệ; route guard chuyển về login khi Auth Context mất phiên |
| `403 Forbidden` | Hiển thị lỗi quyền; trang yêu cầu `pricing:manage` |
| `404 Not Found` | Room Type đã bị soft-delete/không tồn tại; giữ modal mở và yêu cầu tải lại dữ liệu |
| `409 Conflict` | Hiển thị rule cùng target/priority/ngày áp dụng đã tồn tại |
| List API lỗi | Hiển thị error state cùng nút retry |
| Không có override | Tab danh sách hiển thị empty state; calendar vẫn hiển thị toàn bộ base price |
| Không có Room Type | Vô hiệu hóa nút tạo và calendar hiển thị empty state |

## 9. Kiểm thử

- Backend: authentication/authorization, `201 + Location`, chuẩn hóa code, Room Type không tồn tại, response không lộ target BIGINT.
- Service: tạo bằng code public, weekdays null/tập con, ngày/giá sai, conflict cùng priority và overlap khác priority.
- Frontend: search/filter/pagination, validation form, không bỏ chọn hết weekday, payload null khi chọn đủ bảy ngày và lỗi `409`.
- Calendar: base price, weekday-only rule, priority winner, rule xuyên tháng, adjacency, Room Type inactive và loại trừ room-specific rule.
- Chạy `npm run lint`, `npm run build` và `mvn clean test`.

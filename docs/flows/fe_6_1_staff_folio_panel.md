# FE-6.1 — Staff Folio Panel

Tài liệu này mô tả flow Folio được tích hợp trực tiếp trong Sheet chi tiết booking tại `/admin/bookings`. Component dùng dữ liệu tổng hợp sẵn có của `BookingStaffDetail`, tái sử dụng Card, Tabs, DataTable, Dialog, Select, Form, Badge, Skeleton và Sonner của giao diện Admin hiện tại.

## 1. Phạm vi

FE-6.1 gồm:

- Tab “Tiền phòng” hiển thị từng nightly rate snapshot từ `booking_room_nights`.
- Tab “Dịch vụ” hiển thị toàn bộ `folio_charges`, bao gồm các dòng đã void.
- Dialog thêm khoản phát sinh từ danh mục `service_items` active.
- Dialog void bắt buộc nhập lý do.
- Footer tổng hợp tiền phòng, dịch vụ trước thuế, thuế, giảm giá và tổng cộng từ backend.
- Kiểm soát action theo trạng thái booking và permission `invoice:issue`.

Task không tạo trang booking detail riêng, không thêm API Folio summary, không hỗ trợ khoản nhập tay, edit hoặc hard-delete charge.

## 2. Nguồn dữ liệu và API

| Chức năng | API | Permission |
| --- | --- | --- |
| Xem chi tiết booking và Folio | `GET /api/admin/bookings/{bookingPublicId}` | `booking:check_in` hoặc `booking:check_out` |
| Tải danh mục dịch vụ active | `GET /api/service-items` | `invoice:issue` |
| Thêm khoản phát sinh | `POST /api/bookings/{bookingPublicId}/folio-charges` | `invoice:issue` |
| Void khoản phát sinh | `PATCH /api/bookings/{bookingPublicId}/folio-charges/{chargeId}/void` | `invoice:issue` |

`BookingStaffDetail` là nguồn duy nhất cho phần hiển thị Folio. Response đã chứa:

- `rooms[].nightlyRates` và `roomsTotal`.
- `folioCharges`, gồm cả dòng đã void.
- `servicesTotal`, `taxTotal`, `discountTotal`, `totalAmount` và `currency`.

Danh mục dịch vụ được tải độc lập. Nếu `/api/service-items` lỗi, Sheet booking và dữ liệu Folio vẫn hiển thị; UI báo lỗi kèm nút “Thử lại” và vô hiệu hóa nút thêm khoản phát sinh.

## 3. Contract danh mục dịch vụ

`GET /api/service-items` chỉ trả item có `is_active=true`, sắp xếp theo category, name và code:

```json
[
  {
    "code": "MINIBAR_WATER",
    "name": "Nước suối minibar",
    "category": "MINIBAR",
    "unitPrice": 30000.00,
    "taxPercent": 10.00
  }
]
```

Các category được hỗ trợ: `FNB`, `LAUNDRY`, `SPA`, `TRANSPORT`, `MINIBAR`, `PENALTY`, `OTHER`. API không trả khóa BIGINT của `service_items`.

Authorization được kiểm tra tại cả controller và `ServiceItemService`; việc ẩn action trên frontend không thay thế lớp bảo vệ phía server.

## 4. Flow tải và hiển thị Folio

1. Staff mở một dòng trong `/admin/bookings`; frontend gọi API chi tiết và mở Sheet.
2. Tab ngoài “Folio” render `FolioPanel` bằng `BookingStaffDetail` hiện tại.
3. Tab “Tiền phòng” trải phẳng `rooms[].nightlyRates`; mỗi dòng hiển thị phòng, loại phòng, ngày lưu trú và giá snapshot.
4. Tab “Dịch vụ” hiển thị description snapshot, service code, thời điểm ghi nhận, số lượng, đơn giá, thuế, thành tiền và trạng thái.
5. Dòng đã void không bị xóa: nội dung tiền được gạch ngang, có badge “Đã hủy” và lý do void.
6. Footer dùng trực tiếp aggregate totals từ backend, không tự thay backend tính lại tổng booking.

Nightly rate và charge đều là dữ liệu snapshot. Việc Room Type, rate rule hoặc Service Item thay đổi về sau không làm đổi lịch sử Folio đã ghi nhận.

## 5. Flow thêm khoản phát sinh

Action chỉ xuất hiện khi đồng thời:

- Booking có trạng thái `CHECKED_IN`.
- User có permission `invoice:issue`.

Dialog nhóm Service Item theo category. Request chỉ gửi code public và quantity:

```json
{
  "serviceItemCode": "MINIBAR_WATER",
  "quantity": 2.5
}
```

Validation frontend:

- Service Item bắt buộc được chọn.
- Quantity phải lớn hơn `0`.
- Quantity có tối đa 8 chữ số phần nguyên và 2 chữ số thập phân.

Frontend dùng giá và thuế từ options để preview:

```text
subtotal = unitPrice × quantity
taxAmount = subtotal × taxPercent / 100
previewTotal = subtotal + taxAmount
```

Preview chỉ giúp Staff kiểm tra trước khi gửi. Backend tìm lại Service Item active, snapshot description/đơn giá/thuế và là nguồn tính tiền cuối cùng.

Sau khi POST thành công, dòng mới được append ngay vào state của Sheet. Dialog đóng và hiển thị toast thành công; frontend sau đó refetch chi tiết booking và danh sách booking để đồng bộ aggregate totals.

## 6. Flow void charge

Staff chọn “Void” trên một dòng còn hiệu lực. Dialog hiển thị tên khoản và bắt buộc lý do sau khi trim, tối đa 2.000 ký tự:

```json
{
  "reason": "Ghi nhận trùng khoản minibar"
}
```

Backend kiểm tra charge thuộc đúng booking, booking đang `CHECKED_IN` và charge chưa bị void. Thành công trả lại chính dòng charge với `isVoided=true`, `voidedAt`, `voidedBy` và `voidReason`.

Frontend thay dòng cũ tại chỗ, không xóa khỏi bảng và không hiển thị nút Void lần nữa. Sau đó frontend refetch totals giống flow tạo charge.

## 7. Tổng tiền

Footer hiển thị các trường tổng hợp của `BookingStaffDetail`:

```text
totalAmount = roomsTotal + servicesTotal + taxTotal - discountTotal
```

- `servicesTotal` là tổng `lineSubtotal` của charge chưa void.
- Dòng void vẫn có trong lịch sử nhưng không tham gia aggregate totals.
- `taxTotal` và `discountTotal` dùng trực tiếp kết quả backend.
- Tab dịch vụ có thêm tổng thuế và `lineTotal` của các dòng chưa void để Staff đối chiếu.

Mutation và refresh được tách thành hai giai đoạn. Nếu POST/PATCH thành công nhưng refetch thất bại, UI vẫn báo mutation thành công, giữ dòng vừa cập nhật và chỉ cảnh báo tổng tiền chưa đồng bộ; không báo nhầm rằng thao tác tạo/void thất bại.

## 8. RBAC và trạng thái read-only

| Điều kiện | Xem Folio | Thêm/Void |
| --- | --- | --- |
| Có quyền xem booking, booking `CHECKED_IN`, có `invoice:issue` | Có | Có |
| Có quyền xem booking, booking `CHECKED_IN`, thiếu `invoice:issue` | Có | Không |
| Có quyền xem booking, booking khác `CHECKED_IN` | Có | Không |
| Chưa đăng nhập | Không | Không |

Frontend chỉ tải danh mục Service Item khi user có `invoice:issue`. Backend vẫn kiểm tra `invoice:issue` tại controller và service của danh mục/tạo/void charge.

## 9. Xử lý lỗi

| HTTP/tình huống | Xử lý UI |
| --- | --- |
| `400 Bad Request` khi add | Hiển thị quantity/payload sai hoặc booking không còn `CHECKED_IN`; giữ dialog mở |
| `400 Bad Request` khi void | Hiển thị charge đã void hoặc booking không còn `CHECKED_IN`; giữ dialog mở |
| `401 Unauthorized` | Session hết hạn được API client/Auth Context xử lý |
| `403 Forbidden` | Backend từ chối action; UI vốn đã ẩn action khi thiếu `invoice:issue` |
| `404 Not Found` khi add | Service Item đã inactive/không tồn tại; yêu cầu tải lại danh mục |
| `404 Not Found` khi void | Charge hoặc booking không tồn tại/không khớp |
| API service options lỗi | Folio ở chế độ xem, nút thêm bị khóa và có retry riêng |
| Mutation thành công, refetch lỗi | Giữ dòng cập nhật tại chỗ, toast cảnh báo aggregate totals chưa đồng bộ |
| Không có nightly rate | Tab tiền phòng hiển thị empty state |
| Không có charge | Tab dịch vụ hiển thị empty state |

## 10. Kiểm thử

- Backend: options chỉ gồm Service Item active, đúng thứ tự, mapping category/price/tax và `401/403/200`.
- Service authorization: thiếu `invoice:issue` không truy cập repository.
- Regression: chạy lại test tạo/void Folio hiện có để giữ snapshot và rule trạng thái.
- Frontend: room nights, empty states, footer totals và dòng void vẫn hiển thị.
- Form: quantity nguyên/thập phân, giới hạn 8+2 chữ số, quantity không hợp lệ và preview tiền/thuế.
- Mutation: add/void thành công, lỗi `400/401/403/404`, cập nhật tại chỗ và refresh aggregate totals.
- RBAC: action chỉ có với `CHECKED_IN` và `invoice:issue`; các trường hợp khác read-only.
- Verification: `npm run lint`, `npm run build` và `mvn clean test`.

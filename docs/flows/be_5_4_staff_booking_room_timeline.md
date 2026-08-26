# BE-5.4 — Staff Booking Room Map & Timeline

Tài liệu mô tả API nội bộ cho Staff/Admin tạo booking bằng cách chọn trực tiếp số phòng. Customer booking vẫn dùng room type và API customer hiện tại.

## API

| Method | Endpoint | Permission | Kết quả |
| --- | --- | --- | --- |
| `GET` | `/api/admin/rooms/booking-map?checkInDate=&checkOutDate=` | `room:booking_map:read` | Danh sách phòng và timeline booking/block |
| `POST` | `/api/admin/bookings/calculate-price` | `booking:create_staff` | Tính giá theo ngày với policy mặc định `NON_REFUND` |
| `POST` | `/api/admin/bookings` | `booking:create_staff` | Booking `PENDING` với các phòng đã chọn |

## Booking map

Khoảng ngày dùng semantics nửa mở `[checkInDate, checkOutDate)`. Mỗi phòng trả housekeeping status, operational status, room type, sức chứa, `selectable`, lý do không chọn được và các timeline event.

- Booking event lấy các `booking_rooms` có status `RESERVED` hoặc `OCCUPIED` và overlap khoảng ngày.
- Room status block event lấy các block overlap khoảng ngày.
- Phòng chỉ `selectable` khi đang `CLEAN`, active, operational `ACTIVE`, room type active và không có event overlap.
- Booking event trả `bookingPublicId` và `bookingCode` để Staff/Admin mở lại booking cần kiểm tra.
- API không được cấp cho Customer vì timeline chứa thông tin booking nội bộ.

## Tạo booking Staff/Admin

Request gửi contact information và từng `roomNumber`, `roomTypeCode`, ngày lưu trú, số khách và danh sách khách lưu trú. Backend luôn dùng chính sách `NON_REFUND` cho booking Staff/Admin; client không gửi policy để tránh chọn sai. `contactPhone` bắt buộc với booking do Staff/Admin tạo. `guestCount` hiện được tính hoàn toàn là số người lớn vì flow chưa hỗ trợ trẻ em và không gửi `children`. Mỗi khách gửi `fullName`, `idDocumentType`, `idDocumentNumber`, cùng `nationality` và `dateOfBirth` tùy chọn; danh sách phải có đúng số lượng `guestCount` của phòng. Service khóa phòng theo số phòng, kiểm tra lại housekeeping, operational status, room type, booking overlap và room status block trong transaction trước khi ghi.

Ví dụ payload rút gọn:

```json
{
  "contactName": "Nguyen Van A",
  "contactPhone": "0900000000",
  "rooms": [
    {
      "roomNumber": "A101",
      "roomTypeCode": "DLX",
      "paymentOption": "ONLINE",
      "checkInDate": "2026-09-01",
      "checkOutDate": "2026-09-03",
      "guestCount": 2,
      "guests": [
        {
          "fullName": "Nguyen Van A",
          "nationality": "VN",
          "idDocumentType": "NATIONAL_ID",
          "idDocumentNumber": "012345678901"
        },
        {
          "fullName": "Nguyen Van B",
          "idDocumentType": "PASSPORT",
          "idDocumentNumber": "P1234567"
        }
      ]
    }
  ]
}
```

Booking được tạo với:

- `source = STAFF_MANUAL`;
- `status = PENDING`;
- `hold_expires_at` theo thời lượng hold hiện tại;
- `booking_rooms.room_id` là phòng Staff/Admin đã chọn;
- snapshot policy và từng nightly rate được tạo như customer booking;
- policy snapshot luôn là `NON_REFUND`;
- `assigned_at` và `assigned_by` ghi nhận nhân viên đã chọn phòng.
- Mỗi khách được gắn với `booking_room`; số giấy tờ được mã hóa và lưu lookup hash qua `GuestDocumentCryptoService`, không lưu plaintext.

Hai request đồng thời chọn cùng phòng vẫn được DB trigger/transaction kiểm soát; client không được coi dữ liệu booking map là cam kết cuối cùng.

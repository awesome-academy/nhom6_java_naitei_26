# FE-6.2 — Staff Invoice Issuance

Tài liệu mô tả flow phát hành hóa đơn được tích hợp trong Sheet chi tiết booking tại `/admin/bookings`. Giao diện sử dụng dữ liệu invoice có sẵn trong `BookingStaffDetail` và các API Invoice hiện hữu; không tạo trang chi tiết hay cơ chế tạo DRAFT mới.

## 1. Phạm vi

FE-6.2 gồm:

- Tab “Hóa đơn” trong Sheet chi tiết booking.
- Bản xem trước hóa đơn read-only theo bố cục gần với PDF.
- Form thông tin người mua, được điền trước từ invoice hoặc booking contact.
- Thêm dòng `ADJUSTMENT` dương hoặc âm khi invoice còn `DRAFT`.
- Phát hành invoice `DRAFT`, hiển thị invoice number và badge trạng thái.
- Tải PDF bằng presigned URL.
- Hủy invoice `ISSUED` với lý do, không tạo hóa đơn thay thế.
- Phân tách quyền `invoice:issue` và `invoice:void`.

Task không hỗ trợ tạo DRAFT thủ công, sửa invoice đã phát hành, hóa đơn thay thế, thanh toán hoặc gửi hóa đơn qua email.

## 2. Nguồn dữ liệu và API

| Chức năng | API | Permission |
| --- | --- | --- |
| Xem booking và invoices | `GET /api/admin/bookings/{bookingPublicId}` | Quyền xem booking hiện có |
| Cập nhật buyer của DRAFT | `PUT /api/invoices/{invoicePublicId}/buyer` | `invoice:issue` |
| Thêm ADJUSTMENT | `POST /api/invoices/{invoicePublicId}/adjustments` | `invoice:issue` |
| Phát hành | `POST /api/invoices/{invoicePublicId}/issue` | `invoice:issue` |
| Lấy presigned URL PDF | `GET /api/invoices/{invoicePublicId}/pdf` | `invoice:issue` |
| Hủy hóa đơn | `POST /api/invoices/{invoicePublicId}/void` | `invoice:void` |

Invoice trong booking được truy vấn theo `createdAt`, sau đó `id`; các item được sắp theo `sortOrder`, sau đó `id`. Response không lộ ID nội bộ của invoice, dùng `publicId`; `invoiceNumber` là `null` khi invoice còn DRAFT.

## 3. State machine

```text
Checkout thành công
       │
       ▼
     DRAFT ── phát hành ──► ISSUED ── hủy ──► VOID
       │                       │                 │
       ├─ sửa buyer            ├─ tải PDF        └─ tải PDF
       └─ thêm ADJUSTMENT      └─ không sửa nội dung
```

- DRAFT được backend tạo tự động trong flow checkout.
- Chỉ DRAFT được sửa buyer, thêm/xóa ADJUSTMENT và phát hành.
- Chỉ ISSUED được hủy.
- VOID là trạng thái kết thúc trong FE-6.2; request hủy luôn gửi `createReplacement=false`.
- BR-013 được giữ nguyên: invoice đã phát hành không bị sửa nội dung trực tiếp.

## 4. Flow hiển thị tab Hóa đơn

1. Staff mở một booking trong `/admin/bookings`.
2. Frontend tải `BookingStaffDetail`, bao gồm danh sách `invoices` và `items`.
3. Tab “Hóa đơn” ưu tiên invoice DRAFT mới nhất; nếu không có thì chọn ISSUED mới nhất, sau đó mới đến invoice gần nhất còn lại.
4. Nếu booking chưa checkout và chưa có DRAFT, UI hiển thị empty state giải thích invoice sẽ được tạo sau checkout.
5. DRAFT hiển thị badge “Bản nháp” và action “Xuất hóa đơn” khi có `invoice:issue`.
6. ISSUED hiển thị badge “Đã phát hành”, invoice number, action tải PDF và action hủy theo từng permission.
7. VOID hiển thị badge “Đã hủy” và giữ bản xem trước/PDF ở chế độ chỉ đọc.

Người thiếu permission quản lý vẫn xem được dữ liệu invoice nếu đã có quyền mở booking detail. Việc ẩn nút ở frontend không thay thế authorization tại controller và service.

## 5. Buyer snapshot và preview

Khi mở dialog phát hành, frontend điền theo thứ tự:

1. Giá trị buyer đã lưu trên invoice DRAFT.
2. Nếu thiếu, dùng `booking.contactName`, `booking.contactAddress` và `booking.contactEmail`.
3. Mã số thuế để trống nếu invoice chưa có dữ liệu.

Payload cập nhật buyer:

```json
{
  "buyerName": "Nguyễn Văn A",
  "buyerAddress": "123 Lê Lợi, Đà Nẵng",
  "buyerTaxCode": "0401234567",
  "buyerEmail": "buyer@example.com"
}
```

Validation:

- `buyerName`: bắt buộc sau khi trim, tối đa 150 ký tự.
- `buyerAddress`: tùy chọn, tối đa 2.000 ký tự.
- `buyerTaxCode`: tùy chọn, tối đa 20 ký tự.
- `buyerEmail`: tùy chọn, đúng định dạng, tối đa 255 ký tự.
- Trường tùy chọn rỗng được backend chuẩn hóa thành `null`.

Các field được theo dõi trực tiếp để cập nhật preview nhưng chưa ghi backend cho đến khi Staff phát hành. Preview hiển thị buyer, item snapshot, quantity, unit price, tax, line total và tổng invoice; backend vẫn là nguồn dữ liệu tiền cuối cùng.

## 6. Flow thêm ADJUSTMENT

Trong dialog phát hành, Staff chọn “Thêm dòng điều chỉnh”, nhập mô tả và số tiền:

```json
{
  "description": "Giảm trừ do phòng bàn giao muộn",
  "amount": -100000.00
}
```

- Description bắt buộc, tối đa 200 ký tự.
- Amount khác `0`, tối đa 12 chữ số nguyên và 2 chữ số thập phân.
- Số dương làm tăng tổng; số âm làm giảm tổng.
- Backend khóa invoice DRAFT, thêm item `lineType=ADJUSTMENT`, tính lại totals và `saveAndFlush()`.
- Backend từ ch/k7i payload làm tổng invoice không hợp lệ theo nghiệp vụ hiện có.
- Thành công cập nhật preview tại chỗ và refetch booking; lỗi refetch chỉ tạo cảnh báo đồng bộ, không báo sai rằng mutation thất bại.

## 7. Flow phát hành

1. Staff kiểm tra buyer và preview, sau đó chọn “Phát hành”.
2. Frontend gọi `PUT /buyer` để lưu buyer snapshot đã trim.
3. Nếu cập nhật buyer thành công, frontend gọi `POST /issue`.
4. Backend khóa invoice, xác nhận trạng thái `DRAFT`, sinh invoice number, ghi `issuedAt`, `issuedBy`, chuyển sang `ISSUED` và flush.
5. Frontend cập nhật invoice tại chỗ, đóng dialog, hiển thị invoice number và refetch booking detail/danh sách.
6. Nếu buyer đã lưu nhưng issue lỗi, dialog vẫn mở và thông báo rõ phần buyer đã được lưu; không hoàn tác dữ liệu buyer hợp lệ.

## 8. PDF và hủy hóa đơn

### Tải PDF

1. Staff có `invoice:issue` chọn “Tải PDF”.
2. Backend chỉ nhận invoice `ISSUED` hoặc `VOID`.
3. Nếu chưa có object PDF, backend render, upload MinIO và lưu storage metadata.
4. API trả presigned URL cùng thời điểm hết hạn; frontend mở URL trong tab mới.
5. `503` được hiển thị riêng là lỗi dịch vụ lưu trữ tạm thời.

Frontend không lưu hoặc hiển thị storage key.

### Hủy invoice

Dialog hủy yêu cầu lý do sau khi trim, tối đa 2.000 ký tự:

```json
{
  "reason": "Sai thông tin người mua",
  "createReplacement": false
}
```

Backend yêu cầu độc lập permission `invoice:void`, khóa invoice, chỉ chấp nhận `ISSUED`, ghi thông tin void và chuyển sang `VOID`. Permission `invoice:issue` không tự cấp quyền hủy; ngược lại user chỉ có `invoice:void` có thể hủy mà không cần đồng thời `invoice:issue`.

## 9. Đồng bộ state sau mutation

- Buyer/adjustment/issue/void response thay thế invoice cùng `publicId` trong `selectedBooking` ngay lập tức.
- Sau mutation cần đồng bộ aggregate, frontend tải lại booking detail và danh sách booking song song.
- Mutation thành công không bị báo thành thất bại chỉ vì refetch sau đó lỗi.
- Nếu refetch lỗi, dữ liệu response vẫn được giữ tại chỗ và Sonner hiển thị cảnh báo yêu cầu tải lại.

## 10. Xử lý lỗi

| HTTP/tình huống | Xử lý UI |
| --- | --- |
| `400 Bad Request` | Hiển thị validation buyer/adjustment/reason hoặc điều kiện phát hành không hợp lệ; giữ dialog mở |
| `401 Unauthorized` | Thông báo phiên đăng nhập hết hạn; API client thực hiện refresh/session cleanup hiện có |
| `403 Forbidden` | Thông báo thiếu quyền tương ứng; action cũng được ẩn theo permission |
| `404 Not Found` | Invoice hoặc PDF không còn tồn tại; giữ màn hình hiện tại để Staff tải lại |
| `409 Conflict` | Invoice đã đổi trạng thái bởi request khác; yêu cầu tải lại booking |
| `503 Service Unavailable` | Thông báo MinIO/PDF tạm thời không khả dụng, không đổi trạng thái invoice trên UI |
| Buyer update thành công, issue lỗi | Giữ buyer đã lưu, giữ dialog mở và thông báo lỗi phát hành |
| Mutation thành công, refetch lỗi | Giữ response tại chỗ và hiển thị cảnh báo đồng bộ |

## 11. Kiểm thử

- Backend: normalization/validation buyer, chỉ sửa DRAFT, row lock và `saveAndFlush()`.
- Security: `401/403`, authorization tại service; `invoice:issue` và `invoice:void` hoạt động độc lập.
- Regression: adjustment dương/âm, issue, void với `createReplacement=false` và PDF URL.
- Frontend: empty/DRAFT/ISSUED/VOID, prefill buyer, live preview, adjustment, issue, download PDF, void và state refresh.
- Quality gate: `npm run lint`, `npm run build`, `mvn clean test`.

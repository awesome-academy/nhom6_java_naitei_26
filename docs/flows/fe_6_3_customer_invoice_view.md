# FE-6.3 — Customer Invoice View

Tài liệu mô tả flow Customer xem hóa đơn chính thức của booking tại `/profile/bookings/{bookingPublicId}/invoice`. Trang chỉ đọc dữ liệu snapshot của hóa đơn, không cho phép sửa buyer, item, adjustment hoặc trạng thái hóa đơn.

## 1. Phạm vi

FE-6.3 gồm:

- Nút “Xem hóa đơn” trong trang chi tiết booking đã `CHECKED_OUT`.
- Trang hóa đơn riêng trong khu vực Customer Profile.
- Hiển thị buyer, danh sách item, thuế, giảm giá và tổng tiền.
- Hiển thị hóa đơn `ISSUED` hoặc `VOID`; không công khai bản `DRAFT`.
- Tải PDF chính thức qua presigned URL của MinIO.
- Kiểm tra quyền sở hữu booking tại backend.

Task không hỗ trợ sửa hóa đơn, phát hành, hủy, thêm adjustment, chọn phiên bản, gửi email hoặc thanh toán.

## 2. Route và API

| Chức năng | Route/API | Permission |
| --- | --- | --- |
| Mở trang Customer | `/profile/bookings/{bookingPublicId}/invoice` | Customer đã đăng nhập |
| Tải booking thuộc Customer | `GET /api/bookings/{bookingPublicId}` | Quyền booking hiện có |
| Lấy hóa đơn được phép xem | `GET /api/bookings/{bookingPublicId}/invoice` | `booking:read_own` |
| Lấy signed PDF URL | `GET /api/bookings/{bookingPublicId}/invoices/{invoicePublicId}/pdf` | `booking:read_own` |

Hai API Customer sử dụng `UserPrincipal.id` từ access token. Client không gửi customer ID và không sử dụng các API Staff `/api/invoices/**` vốn yêu cầu `invoice:issue` hoặc `invoice:void`.

## 3. Ownership và lựa chọn hóa đơn

Repository chỉ trả dữ liệu khi đồng thời thỏa mãn:

- `invoice.booking.publicId` khớp `bookingPublicId` trên URL.
- `booking.customerProfile.user.id` khớp user đang đăng nhập.
- `invoice.status` thuộc `ISSUED` hoặc `VOID`.

Danh sách được sắp theo `issuedAt DESC`, sau đó `id DESC`. Service áp dụng quy tắc:

1. Chọn hóa đơn `ISSUED` mới nhất.
2. Nếu không còn `ISSUED`, chọn hóa đơn `VOID` mới nhất.
3. Nếu chỉ có `DRAFT` hoặc không có hóa đơn, trả `404`.

Booking không thuộc Customer, invoice không thuộc booking, invoice thuộc Customer khác và invoice `DRAFT` đều sử dụng cùng response `404`. Cách xử lý này tránh tiết lộ việc resource có tồn tại hay không.

Các item trong response được sắp theo `sortOrder`, sau đó `id`. Item chỉ có ba loại nghiệp vụ `ROOM`, `SERVICE` và `ADJUSTMENT`; thuế và giảm giá là thuộc tính tiền của từng dòng và aggregate ở header hóa đơn.

## 4. Flow tải trang

```text
Customer mở chi tiết booking CHECKED_OUT
                │
                ├── chọn “Xem hóa đơn”
                ▼
GET booking detail ────────┐
GET customer invoice ──────┼── chạy song song
                           ▼
              Kiểm tra ownership/status
                           │
              ┌────────────┴────────────┐
              │                         │
        ISSUED hoặc VOID           Invoice 404
              │                         │
       Hiển thị preview        “Chưa được phát hành”
```

- Lỗi tải booking là lỗi toàn trang vì không thể xác minh context của Customer.
- Nếu booking tải thành công nhưng invoice trả `404`, UI hiển thị trạng thái chưa phát hành thay vì coi booking không tồn tại.
- Lỗi invoice khác `404` hiển thị Alert và nút “Thử lại”.
- Trang có loading skeleton và bỏ qua response đến muộn sau khi component đã unmount.

## 5. Nội dung read-only

Trang sử dụng chung `InvoicePreview` với màn hình Staff FE-6.2 và hiển thị:

- Số hóa đơn, ngày phát hành, booking code.
- Badge `Đã phát hành` hoặc `Đã hủy` và trạng thái thanh toán.
- Buyer name, địa chỉ, mã số thuế và email snapshot.
- Bảng item gồm mô tả, loại dòng, số lượng, đơn giá, thuế và thành tiền.
- Tạm tính, giảm giá, thuế và tổng cộng theo currency của invoice.

Hóa đơn `VOID` có Alert cảnh báo nhưng vẫn giữ toàn bộ nội dung để đối chiếu. Không render input, dialog hay mutation action trên trang Customer.

## 6. Flow tải PDF

1. Customer chọn “Tải PDF”.
2. Frontend gọi endpoint PDF với cả `bookingPublicId` và `invoicePublicId` đang hiển thị.
3. Backend kiểm tra lại ownership, quan hệ invoice–booking và status `ISSUED/VOID`.
4. Nếu PDF chưa được lưu, backend dùng cơ chế hiện có để render, upload vào bucket Invoice và lưu storage metadata.
5. Backend trả `url` và `expiresAt`; frontend mở URL trong tab mới.
6. Request tới signed URL không gửi JWT và không công khai `pdfStorageKey`.

Việc truyền chính xác `invoicePublicId` giúp file tải về luôn khớp bản đang hiển thị, kể cả khi một phiên bản hóa đơn khác được tạo trong lúc Customer đang mở trang.

## 7. RBAC và bảo mật

- Controller và service đều yêu cầu `booking:read_own` theo quy tắc authorization nhiều lớp.
- Ownership được đưa trực tiếp vào JPQL parameterized query; không tải invoice trước rồi mới kiểm tra ở frontend.
- API Staff giữ nguyên permission `invoice:issue`/`invoice:void`; Customer không được cấp quyền quản lý invoice.
- DRAFT không được trả về qua API Customer.
- Public URL dùng UUID của booking/invoice; khóa BIGINT và storage key không được đưa vào URL frontend.

## 8. Xử lý lỗi

| HTTP/tình huống | Xử lý UI |
| --- | --- |
| `401 Unauthorized` | Thông báo phiên đăng nhập hết hạn; API client tiếp tục cơ chế refresh/session cleanup hiện có |
| `403 Forbidden` | Thông báo Customer thiếu `booking:read_own` |
| Booking `404 Not Found` | Hiển thị lỗi toàn trang và nút quay lại danh sách booking |
| Invoice `404 Not Found` sau khi booking hợp lệ | Hiển thị “Hóa đơn chưa được phát hành” |
| PDF `404 Not Found` | Thông báo hóa đơn không tồn tại, không thuộc booking hoặc không còn được phép tải |
| `503 Service Unavailable` | Thông báo dịch vụ lưu trữ PDF tạm thời không khả dụng; giữ nguyên invoice trên màn hình |
| Lỗi mạng/khác | Hiển thị Alert hoặc toast phù hợp và cho phép thử lại |

## 9. Kiểm thử

- Service: ưu tiên `ISSUED` mới nhất, fallback `VOID`, không trả DRAFT và trả `404` khi query ownership rỗng.
- PDF: chỉ dùng invoice khớp booking/Customer/status; tái sử dụng file cache và từ chối resource không thuộc quyền sở hữu.
- Security: `401` khi chưa đăng nhập, `403` khi thiếu `booking:read_own`, controller truyền đúng principal ID và service-layer authorization hoạt động.
- Regression: API Invoice Staff vẫn giữ quyền `invoice:issue`/`invoice:void`; FE-6.2 tiếp tục dùng preview/type Invoice chung.
- Frontend: loading, booking error, invoice error/retry, trạng thái chưa phát hành, `ISSUED`, `VOID` và download PDF.
- Quality gate: `npm run lint`, `npm run build` và `mvn clean test`.

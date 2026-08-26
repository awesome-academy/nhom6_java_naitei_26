# FE-5.4 — Staff/Admin chọn phòng bằng sơ đồ và timeline

Trang `/manager/bookings` có nút `Tạo đơn mới`. Dialog tạo booking dùng lại API catalog và tính giá của luồng customer, nhưng cho phép Staff/Admin chọn trực tiếp số phòng.

## Flow

1. Staff/Admin nhập ngày nhận và trả phòng.
2. Frontend tải booking map theo khoảng ngày.
3. Sơ đồ nhóm phòng theo tầng, hiển thị housekeeping, operational status và các event booking/bảo trì.
4. Chỉ phòng CLEAN không conflict mới click chọn được.
5. Staff chuyển sang bước thông tin khách, nhập người liên hệ (tên, số điện thoại bắt buộc, email tùy chọn).
6. Với mỗi phòng đã chọn, Staff nhập số khách và danh sách khách lưu trú. Trong flow hiện tại booking Staff mặc định dùng chính sách `NON_REFUND` và số khách được tính là người lớn (chưa có trường trẻ em). Mỗi khách cần họ tên, loại giấy tờ và số giấy tờ; quốc tịch và ngày sinh là tùy chọn.
7. Staff xem giá tạm tính rồi tạo booking. Số điện thoại của người liên hệ được dùng làm số điện thoại booking.
8. Sau khi thành công, booking xuất hiện ở trạng thái `PENDING` và danh sách booking được reload.

Mã booking trên timeline có nút mở chi tiết booking hiện có. API lỗi giữ lại dữ liệu đang nhập và lựa chọn hiện tại trong dialog.

Customer `/booking` không hiển thị số phòng hoặc mã booking của phòng khác; customer tiếp tục chọn room type theo availability engine hiện tại.

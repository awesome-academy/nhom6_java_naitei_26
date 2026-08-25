INSERT INTO email_templates (
    code, name, description, subject, body_html, body_text,
    from_name, from_email, reply_to, is_active
) VALUES
(
    'BOOKING_CONFIRMED',
    'Xác nhận đặt phòng',
    'Gửi khi booking chuyển sang CONFIRMED',
    'Xác nhận đặt phòng {{booking_code}}',
    '<h2>Đặt phòng đã được xác nhận</h2><p>Xin chào {{customer_name}},</p><p>Mã đặt phòng <strong>{{booking_code}}</strong> đã được xác nhận.</p><p>Tổng tiền: <strong>{{total_amount}} {{currency}}</strong>.</p>',
    'Xin chào {{customer_name}}, đặt phòng {{booking_code}} đã được xác nhận. Tổng tiền: {{total_amount}} {{currency}}.',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
),
(
    'PAYMENT_SUCCESS',
    'Thanh toán thành công',
    'Gửi khi payment đã được gateway xác minh thành công',
    'Thanh toán thành công cho booking {{booking_code}}',
    '<h2>Thanh toán thành công</h2><p>Xin chào {{customer_name}},</p><p>Giao dịch <strong>{{payment_code}}</strong> cho booking <strong>{{booking_code}}</strong> đã thành công.</p><p>Số tiền: <strong>{{payment_amount}} {{currency}}</strong>.</p>',
    'Xin chào {{customer_name}}, giao dịch {{payment_code}} cho booking {{booking_code}} đã thành công. Số tiền: {{payment_amount}} {{currency}}.',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
),
(
    'BOOKING_CANCELLED',
    'Đặt phòng đã hủy',
    'Gửi khi booking chuyển sang CANCELLED',
    'Booking {{booking_code}} đã được hủy',
    '<h2>Đặt phòng đã được hủy</h2><p>Xin chào {{customer_name}},</p><p>Booking <strong>{{booking_code}}</strong> đã được hủy.</p><p>Lý do: {{cancellation_reason}}</p>',
    'Xin chào {{customer_name}}, booking {{booking_code}} đã được hủy. Lý do: {{cancellation_reason}}.',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
),
(
    'PAYMENT_REFUND',
    'Hoàn tiền thành công',
    'Gửi khi refund chuyển sang COMPLETED',
    'Hoàn tiền cho booking {{booking_code}} đã hoàn tất',
    '<h2>Hoàn tiền đã hoàn tất</h2><p>Xin chào {{customer_name}},</p><p>Khoản hoàn tiền cho booking <strong>{{booking_code}}</strong> đã được xử lý.</p><p>Số tiền hoàn: <strong>{{refund_amount}} {{currency}}</strong>.</p>',
    'Xin chào {{customer_name}}, khoản hoàn tiền cho booking {{booking_code}} đã hoàn tất. Số tiền: {{refund_amount}} {{currency}}.',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
),
(
    'ACCOUNT_ACTIVATED',
    'Tài khoản đã kích hoạt',
    'Gửi sau khi xác thực email lần đầu thành công',
    'Tài khoản TripStay của bạn đã được kích hoạt',
    '<h2>Kích hoạt tài khoản thành công</h2><p>Xin chào {{customer_name}},</p><p>Tài khoản TripStay của bạn đã được kích hoạt và sẵn sàng sử dụng.</p>',
    'Xin chào {{customer_name}}, tài khoản TripStay của bạn đã được kích hoạt và sẵn sàng sử dụng.',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
);

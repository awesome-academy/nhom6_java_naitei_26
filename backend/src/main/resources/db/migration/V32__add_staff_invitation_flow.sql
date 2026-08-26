ALTER TABLE auth_tokens
    MODIFY COLUMN token_type ENUM('EMAIL_VERIFICATION','STAFF_INVITATION','PASSWORD_RESET','EMAIL_CHANGE') NOT NULL;

INSERT INTO email_templates (
    code, name, description, subject, body_html, body_text,
    from_name, from_email, reply_to, is_active
) VALUES (
    'STAFF_INVITATION',
    'Lời mời tài khoản Staff',
    'Email mời nhân viên xác thực và kích hoạt tài khoản Staff',
    'Lời mời tham gia đội ngũ TripStay',
    '<!DOCTYPE html><html lang="vi"><body style="font-family:Arial,sans-serif;line-height:1.6;color:#333"><h2>Xin chào, {{fullName}}!</h2><p>Admin đã tạo tài khoản Staff TripStay cho bạn bằng email này.</p><p>Nhấn vào liên kết bên dưới để xác thực email và đặt mật khẩu đăng nhập chính thức:</p><p><a href="{{invitationLink}}">Kích hoạt tài khoản Staff</a></p><p>Liên kết chỉ dùng một lần và sẽ hết hạn sau 24 giờ.</p><p>Nếu bạn không mong đợi lời mời này, hãy bỏ qua email.</p><p>Trân trọng,<br>Đội ngũ TripStay</p></body></html>',
    'Xin chào, {{fullName}}!\n\nAdmin đã tạo tài khoản Staff TripStay cho bạn.\n\nMở liên kết sau để xác thực email và đặt mật khẩu đăng nhập chính thức:\n{{invitationLink}}\n\nLiên kết chỉ dùng một lần và sẽ hết hạn sau 24 giờ.\n\nTrân trọng,\nĐội ngũ TripStay',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
);

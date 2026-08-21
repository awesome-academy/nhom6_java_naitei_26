-- Email templates for system emails
CREATE TABLE email_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    subject VARCHAR(300) NOT NULL,
    body_html TEXT NOT NULL,
    body_text TEXT NULL,
    from_name VARCHAR(100) NULL,
    from_email VARCHAR(255) NULL,
    reply_to VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_templates_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Index for active templates lookup
CREATE INDEX idx_email_templates_active ON email_templates (is_active);

-- Seed default email templates
INSERT INTO email_templates (code, name, description, subject, body_html, body_text, from_name, from_email, reply_to, is_active) VALUES
(
    'EMAIL_VERIFICATION',
    'Xác thực email',
    'Email gửi khi đăng ký tài khoản mới',
    'Xác thực email của bạn - TripStay',
    '<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác thực email</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f5f5f5; }
        .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
        .card { background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
        .logo { text-align: center; margin-bottom: 32px; }
        .logo h1 { font-size: 28px; font-weight: 700; color: #2563eb; margin: 0; }
        .logo span { color: #1e40af; }
        h2 { font-size: 24px; font-weight: 600; color: #111; margin: 0 0 16px 0; }
        p { margin: 0 0 16px 0; color: #555; }
        .button { display: inline-block; background: #2563eb; color: white !important; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 16px; margin: 24px 0; text-align: center; }
        .button:hover { background: #1d4ed8; }
        .link-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin: 24px 0; word-break: break-all; }
        .link-box code { font-size: 14px; color: #2563eb; }
        .footer { text-align: center; margin-top: 32px; padding-top: 24px; border-top: 1px solid #e5e7eb; color: #888; font-size: 14px; }
        .footer a { color: #2563eb; text-decoration: none; }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="logo">
                <h1>Trip<span>Stay</span></h1>
            </div>
            <h2>Xin chào, {{fullName}}!</h2>
            <p>Cảm ơn bạn đã đăng ký tài khoản tại TripStay. Để hoàn tất đăng ký, vui lòng xác thực địa chỉ email của bạn.</p>
            <p>Nhấp vào nút bên dưới để xác thực email:</p>
            <div style="text-align: center;">
                <a href="{{verificationLink}}" class="button">Xác thực email</a>
            </div>
            <p>Hoặc sao chép và dán liên kết sau vào trình duyệt của bạn:</p>
            <div class="link-box">
                <code>{{verificationLink}}</code>
            </div>
            <p style="font-size: 14px; color: #888;">Liên kết này sẽ hết hạn sau <strong>24 giờ</strong>.</p>
            <p>Nếu bạn không tạo tài khoản tại TripStay, vui lòng bỏ qua email này.</p>
            <div class="footer">
                <p>Trân trọng,<br><strong>Đội ngũ TripStay</strong></p>
                <p>© 2024 TripStay. Mọi quyền được bảo lưu.</p>
            </div>
        </div>
    </div>
</body>
</html>',
    'Xin chào, {{fullName}}!

Cảm ơn bạn đã đăng ký tài khoản tại TripStay. Để hoàn tất đăng ký, vui lòng xác thực địa chỉ email của bạn.

Nhấp vào liên kết sau để xác thực email:
{{verificationLink}}

Liên kết này sẽ hết hạn sau 24 giờ.

Nếu bạn không tạo tài khoản tại TripStay, vui lòng bỏ qua email này.

Trân trọng,
Đội ngũ TripStay',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
),
(
    'PASSWORD_RESET',
    'Đặt lại mật khẩu',
    'Email gửi khi yêu cầu đặt lại mật khẩu',
    'Đặt lại mật khẩu - TripStay',
    '<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f5f5f5; }
        .container { max-width: 600px; margin: 0 auto; padding: 40px 20px; }
        .card { background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
        .logo { text-align: center; margin-bottom: 32px; }
        .logo h1 { font-size: 28px; font-weight: 700; color: #2563eb; margin: 0; }
        .logo span { color: #1e40af; }
        h2 { font-size: 24px; font-weight: 600; color: #111; margin: 0 0 16px 0; }
        p { margin: 0 0 16px 0; color: #555; }
        .warning { background: #fef3c7; border: 1px solid #fcd34d; border-radius: 8px; padding: 16px; margin: 24px 0; }
        .warning p { margin: 0; color: #92400e; }
        .button { display: inline-block; background: #dc2626; color: white !important; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: 600; font-size: 16px; margin: 24px 0; text-align: center; }
        .button:hover { background: #b91c1c; }
        .link-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin: 24px 0; word-break: break-all; }
        .link-box code { font-size: 14px; color: #dc2626; }
        .footer { text-align: center; margin-top: 32px; padding-top: 24px; border-top: 1px solid #e5e7eb; color: #888; font-size: 14px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="logo">
                <h1>Trip<span>Stay</span></h1>
            </div>
            <h2>Đặt lại mật khẩu</h2>
            <p>Xin chào,</p>
            <p>Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Nhấp vào nút bên dưới để đặt lại mật khẩu:</p>
            <div style="text-align: center;">
                <a href="{{resetLink}}" class="button">Đặt lại mật khẩu</a>
            </div>
            <div class="warning">
                <p><strong>Lưu ý bảo mật:</strong> Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này. Liên kết này chỉ có hiệu lực trong <strong>30 phút</strong>.</p>
            </div>
            <p>Hoặc sao chép và dán liên kết sau vào trình duyệt của bạn:</p>
            <div class="link-box">
                <code>{{resetLink}}</code>
            </div>
            <div class="footer">
                <p>Trân trọng,<br><strong>Đội ngũ TripStay</strong></p>
                <p>© 2024 TripStay. Mọi quyền được bảo lưu.</p>
            </div>
        </div>
    </div>
</body>
</html>',
    'Đặt lại mật khẩu - TripStay

Xin chào,

Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.

Nhấp vào liên kết sau để đặt lại mật khẩu:
{{resetLink}}

Lưu ý bảo mật: Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này. Liên kết này chỉ có hiệu lực trong 30 phút.

Trân trọng,
Đội ngũ TripStay',
    'TripStay',
    'noreply@tripstay.vn',
    'support@tripstay.vn',
    TRUE
);

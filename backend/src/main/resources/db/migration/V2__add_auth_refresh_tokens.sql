-- Lưu trạng thái refresh token để backend kiểm soát revoke/rotation ở DB.
-- Không lưu raw refresh JWT; chỉ lưu jwt_id (jti). Nếu DB bị lộ, attacker không có token gốc để sử dụng.
CREATE TABLE auth_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    jwt_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    rotated_to_jti VARCHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_tokens_jti (jwt_id),
    KEY idx_auth_refresh_tokens_user_active (user_id, revoked_at, expires_at),
    KEY idx_auth_refresh_tokens_expires_at (expires_at),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

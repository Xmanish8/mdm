CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reset_expiry(expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

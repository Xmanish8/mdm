

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
);
CREATE TABLE IF NOT EXISTS teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS team_memberships (
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, team_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE TABLE IF NOT EXISTS work_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    team_id BIGINT NOT NULL,
    assigned_to BIGINT NULL,
    created_by BIGINT NOT NULL,
    next_action VARCHAR(255),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES teams(id),
    FOREIGN KEY (assigned_to) REFERENCES users(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_work_filter
        (team_id, status, priority, updated_at)
);

CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_item_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (work_item_id) REFERENCES work_items(id),
    FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS operation_requests (
    actor_id BIGINT NOT NULL,
    request_key CHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    work_item_id BIGINT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_id, request_key),
    FOREIGN KEY (actor_id) REFERENCES users(id),
    FOREIGN KEY (work_item_id) REFERENCES work_items(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    details VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS auth_rate_limits (
    bucket CHAR(64) PRIMARY KEY,
    window_start BIGINT NOT NULL,
    attempts BIGINT NOT NULL DEFAULT 0,
    INDEX idx_rate_expiry(window_start)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS analytics_daily (
    page VARCHAR(32) NOT NULL,
    day DATE NOT NULL,
    views BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(page,day)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reset_expiry(expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

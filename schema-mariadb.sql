-- ==============================================================================
-- PreonsURL MariaDB Schema
-- ==============================================================================
-- You can execute this script directly on your remote MariaDB server.
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS preonsurl
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE preonsurl;

-- 1. Table for storing shortened URLs and their directory mappings
CREATE TABLE IF NOT EXISTS short_urls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(64) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    dir_type VARCHAR(128) NULL,
    full_short_url VARCHAR(512) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_short_code UNIQUE (short_code),
    INDEX idx_short_urls_original_url (original_url(255)),
    INDEX idx_short_urls_dir_type_original (dir_type, original_url(255)),
    INDEX idx_short_urls_dir_code (dir_type, short_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table for logging each access / redirection event
CREATE TABLE IF NOT EXISTS short_urls_access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_url_id BIGINT NOT NULL,
    short_code VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    referer VARCHAR(1024) NULL,
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_access_log_short_url FOREIGN KEY (short_url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    INDEX idx_access_log_short_url_id (short_url_id),
    INDEX idx_access_log_short_code (short_code),
    INDEX idx_access_log_accessed_at (accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

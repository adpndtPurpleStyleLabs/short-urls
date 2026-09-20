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
    user_id BIGINT NULL,
    short_code VARCHAR(64) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    custom_path VARCHAR(256) NULL,
    new_url VARCHAR(512) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    expire_at TIMESTAMP NOT NULL,
    usage_limit BIGINT NULL,
    link_mode VARCHAR(20) NOT NULL DEFAULT 'REDIRECT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_short_code UNIQUE (short_code),
    INDEX idx_short_urls_user_id (user_id),
    INDEX idx_short_urls_original_url (original_url(255)),
    INDEX idx_short_urls_custom_path (custom_path),
    INDEX idx_short_urls_new_url (new_url),
    INDEX idx_short_urls_link_mode (link_mode),
    INDEX idx_short_urls_is_active (is_active),
    INDEX idx_short_urls_expire_at (expire_at)
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

-- 3. Table for storing tags associated with shortened URLs
CREATE TABLE IF NOT EXISTS tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tags_short_url FOREIGN KEY (url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    CONSTRAINT uq_tags_url_tag UNIQUE (url_id, tag),
    INDEX idx_tags_url_id (url_id),
    INDEX idx_tags_user_id (user_id),
    INDEX idx_tags_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

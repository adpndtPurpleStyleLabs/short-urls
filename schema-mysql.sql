-- ==============================================================================
-- PreonsURL MySQL Schema
-- ==============================================================================
-- You can execute this script directly on your remote MySQL server.
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
    domain VARCHAR(255) NULL,
    new_url VARCHAR(512) NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    expire_at TIMESTAMP NOT NULL,
    usage_limit BIGINT NULL,
    link_mode VARCHAR(20) NOT NULL DEFAULT 'REDIRECT',
    is_enable BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_short_code UNIQUE (short_code),
    INDEX idx_short_urls_user_id (user_id),
    INDEX idx_short_urls_original_url (original_url(255)),
    INDEX idx_short_urls_custom_path (custom_path),
    INDEX idx_short_urls_domain (domain),
    INDEX idx_short_urls_new_url (new_url),
    INDEX idx_short_urls_link_mode (link_mode),
    INDEX idx_short_urls_is_enable (is_enable),
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

-- 4. Table for tracking creation and edits to new URLs (CREATED, EDITED, field-level diffs)
CREATE TABLE IF NOT EXISTS new_url_changes_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    field_name VARCHAR(64) NULL,
    old_value VARCHAR(4096) NULL,
    new_value VARCHAR(4096) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_changes_log_url FOREIGN KEY (url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    INDEX idx_changes_log_url_id (url_id),
    INDEX idx_changes_log_user_id (user_id),
    INDEX idx_changes_log_action (action),
    INDEX idx_changes_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table for storing access control policies associated with shortened URLs
CREATE TABLE IF NOT EXISTS access_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_url_id BIGINT NOT NULL,
    mode VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    pin_hash VARCHAR(255) NULL,
    password_hash VARCHAR(255) NULL,
    ip_allowlist TEXT NULL,
    countries VARCHAR(512) NULL,
    device_types VARCHAR(512) NULL,
    referrers TEXT NULL,
    otp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    otp_emails TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_access_policies_short_url FOREIGN KEY (short_url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    CONSTRAINT uq_access_policies_short_url UNIQUE (short_url_id),
    INDEX idx_access_policies_short_url_id (short_url_id),
    INDEX idx_access_policies_mode (mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5b. Table for storing link recipients and tracking lifecycle (email sent, opened via 1px beacon, OTP requested, page opened)
CREATE TABLE IF NOT EXISTS link_recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_url_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    tracking_token VARCHAR(64) NOT NULL,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMP NULL,
    email_opened BOOLEAN NOT NULL DEFAULT FALSE,
    email_opened_at TIMESTAMP NULL,
    otp_requested BOOLEAN NOT NULL DEFAULT FALSE,
    otp_requested_at TIMESTAMP NULL,
    otp_code_hash VARCHAR(255) NULL,
    otp_expires_at TIMESTAMP NULL,
    page_opened BOOLEAN NOT NULL DEFAULT FALSE,
    page_opened_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_link_recipients_short_url FOREIGN KEY (short_url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    CONSTRAINT uq_link_recipients_url_email UNIQUE (short_url_id, email),
    INDEX idx_link_recipients_tracking_token (tracking_token),
    INDEX idx_link_recipients_short_url_id (short_url_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Table for storing usage control policies and tracking usage associated with shortened URLs
CREATE TABLE IF NOT EXISTS usage_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_url_id BIGINT NOT NULL,
    policy_type VARCHAR(32) NOT NULL DEFAULT 'UNLIMITED',
    usage_limit BIGINT NULL,
    current_usage BIGINT NOT NULL DEFAULT 0,
    expire_at TIMESTAMP NULL,
    start_at TIMESTAMP NULL,
    end_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_usage_policies_short_url FOREIGN KEY (short_url_id) REFERENCES short_urls (id) ON DELETE CASCADE,
    CONSTRAINT uq_usage_policies_short_url UNIQUE (short_url_id),
    INDEX idx_usage_policies_short_url_id (short_url_id),
    INDEX idx_usage_policies_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Table for storing tenants
CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Table for storing users with email verification support
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_code VARCHAR(6) NULL,
    verification_code_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username),
    INDEX idx_users_tenant_id (tenant_id),
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Table for custom domains and CNAME verification
CREATE TABLE IF NOT EXISTS custom_domains (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VERIFICATION_REQUIRED',
    cname_target VARCHAR(255) NOT NULL,
    verification_error VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    CONSTRAINT uq_custom_domains_domain UNIQUE (domain),
    INDEX idx_custom_domains_user_id (user_id),
    INDEX idx_custom_domains_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Table for asynchronous data extractions (Link, Link Analytics)
CREATE TABLE IF NOT EXISTS data_extraction_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    report_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    start_date_time DATETIME NULL,
    end_date_time DATETIME NULL,
    target_short_url_id BIGINT NULL,
    target_short_url VARCHAR(512) NULL,
    file_name VARCHAR(255) NULL,
    file_path VARCHAR(512) NULL,
    file_content LONGTEXT NULL,
    file_size_bytes BIGINT DEFAULT 0,
    record_count INT DEFAULT 0,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    INDEX idx_dej_user_created (user_id, created_at DESC),
    INDEX idx_dej_public_id (public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Table for Compliance Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(128) NULL,
    tenant_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255) NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user_created (user_id, created_at DESC),
    INDEX idx_audit_created (created_at DESC),
    INDEX idx_audit_action (action),
    INDEX idx_audit_resource (resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


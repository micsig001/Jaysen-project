-- H2 集成测试 schema（MySQL 兼容模式：MODE=MySQL）
-- 简化版主表，覆盖 @SpringBootTest 所需最小集合：
--   users / departments / tasks / task_status_history
-- 注：H2 AUTO_INCREMENT 用 IDENTITY 关键字；ENUM 用 VARCHAR + CHECK 替代

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dept_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    order_num INT DEFAULT 0,
    leader_user_id VARCHAR(64),
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    mobile VARCHAR(20),
    email VARCHAR(100),
    department_id BIGINT,
    position VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
    status TINYINT NOT NULL DEFAULT 1,
    is_manual_role BOOLEAN DEFAULT FALSE,
    last_sync_time TIMESTAMP,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description CLOB,
    priority TINYINT NOT NULL DEFAULT 3,
    creator_id VARCHAR(64) NOT NULL,
    assignee_id VARCHAR(64) NOT NULL,
    estimated_duration INT,
    actual_start_time TIMESTAMP,
    actual_deadline TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_ACCEPT',
    source_remark VARCHAR(200),
    is_self_assigned BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP,
    withdrawn_at TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    remark VARCHAR(500),
    snapshot_json CLOB,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    operation_type VARCHAR(50) NOT NULL,
    request_hash VARCHAR(64),
    response_data CLOB,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- 审计日志表（AuditAspect 触发 @AuditLog 时写入）
-- 注：operator_id 在 @WithMockUser 测试中可能传入 "org.springframework.security.core.userdetails.User [Username=..., ...]"
-- 完整 toString()（>64 字符），所以这里放宽到 512。
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id VARCHAR(512) NOT NULL,
    operation_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    operation_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    before_snapshot CLOB,
    after_snapshot CLOB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

-- 企微同步日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_type VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    synced_users_count INT DEFAULT 0,
    synced_departments_count INT DEFAULT 0,
    error_message CLOB,
    last_update_timestamp BIGINT,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

-- 历史任务归档表（ArchiveService / ArchiveScheduler 引用）
CREATE TABLE IF NOT EXISTS tasks_history_archive (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description CLOB,
    priority TINYINT NOT NULL,
    creator_id VARCHAR(64) NOT NULL,
    assignee_id VARCHAR(64) NOT NULL,
    estimated_duration INT,
    actual_start_time TIMESTAMP,
    actual_deadline TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    source_remark VARCHAR(200),
    is_self_assigned BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP,
    withdrawn_at TIMESTAMP,
    archived_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    original_created_at TIMESTAMP NOT NULL,
    original_updated_at TIMESTAMP NOT NULL
);

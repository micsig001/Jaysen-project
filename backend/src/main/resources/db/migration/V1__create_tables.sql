-- V1: 创建核心表结构

-- 部门表
CREATE TABLE IF NOT EXISTS departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    dept_id VARCHAR(64) NOT NULL UNIQUE COMMENT '企业微信部门ID',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id BIGINT COMMENT '父部门ID',
    order_num INT DEFAULT 0 COMMENT '排序号',
    leader_user_id VARCHAR(64) COMMENT '部门负责人UserID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    FOREIGN KEY (parent_id) REFERENCES departments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL UNIQUE COMMENT '企业微信UserID',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    mobile VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    department_id BIGINT COMMENT '所属部门ID',
    position VARCHAR(100) COMMENT '职位',
    role ENUM('EMPLOYEE', 'MANAGER', 'ADMIN') NOT NULL DEFAULT 'EMPLOYEE' COMMENT '系统角色',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    is_manual_role BOOLEAN DEFAULT FALSE COMMENT '是否手动分配角色',
    last_sync_time DATETIME COMMENT '最后同步时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_department_id (department_id),
    INDEX idx_role (role),
    INDEX idx_status (status),
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 任务主表
CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_no VARCHAR(32) NOT NULL UNIQUE COMMENT '任务编号',
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    priority TINYINT NOT NULL DEFAULT 3 COMMENT '优先级：1-最高，2-高，3-中，4-低',
    creator_id VARCHAR(64) NOT NULL COMMENT '创建人UserID',
    assignee_id VARCHAR(64) NOT NULL COMMENT '执行人UserID',
    estimated_duration INT COMMENT '预估时长（分钟）',
    actual_start_time DATETIME COMMENT '实际开始时间',
    actual_deadline DATETIME COMMENT '实际截止时间',
    status ENUM('PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_VERIFY', 'COMPLETED', 'WITHDRAWN', 'REJECTED')
        NOT NULL DEFAULT 'PENDING_ACCEPT' COMMENT '任务状态',
    source_remark VARCHAR(200) COMMENT '来源备注',
    is_self_assigned BOOLEAN DEFAULT FALSE COMMENT '是否自己发给自己',
    completed_at DATETIME COMMENT '完成时间',
    withdrawn_at DATETIME COMMENT '撤回时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_creator_id (creator_id),
    INDEX idx_status (status),
    INDEX idx_priority_status (priority, status),
    INDEX idx_actual_deadline (actual_deadline),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (creator_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (assignee_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务主表';

-- 任务状态流转历史表
CREATE TABLE IF NOT EXISTS task_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    from_status VARCHAR(50) COMMENT '变更前状态',
    to_status VARCHAR(50) NOT NULL COMMENT '变更后状态',
    operator_id VARCHAR(64) NOT NULL COMMENT '操作人UserID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    remark VARCHAR(500) COMMENT '操作备注',
    snapshot_json JSON COMMENT '状态变更时的任务快照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_task_id (task_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (operator_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务状态流转历史表';

-- 企业微信同步日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sync_type ENUM('FULL', 'INCREMENTAL') NOT NULL COMMENT '同步类型',
    trigger_type ENUM('SCHEDULED', 'MANUAL') NOT NULL COMMENT '触发类型',
    start_time DATETIME NOT NULL COMMENT '同步开始时间',
    end_time DATETIME COMMENT '同步结束时间',
    status ENUM('SUCCESS', 'FAILED', 'PARTIAL') NOT NULL COMMENT '同步状态',
    synced_users_count INT DEFAULT 0 COMMENT '同步用户数',
    synced_departments_count INT DEFAULT 0 COMMENT '同步部门数',
    error_message TEXT COMMENT '错误信息',
    last_update_timestamp BIGINT COMMENT '增量同步的最后更新时间戳',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_start_time (start_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业微信同步日志表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    operator_id VARCHAR(64) NOT NULL COMMENT '操作人UserID',
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型',
    resource_id VARCHAR(100) NOT NULL COMMENT '资源ID',
    before_snapshot JSON COMMENT '变更前数据快照',
    after_snapshot JSON COMMENT '变更后数据快照',
    ip_address VARCHAR(45) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT '用户代理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_operator_id (operator_id),
    INDEX idx_operation_time (operation_time),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_operation_type (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- 历史任务归档表
CREATE TABLE IF NOT EXISTS tasks_history_archive (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_no VARCHAR(32) NOT NULL UNIQUE COMMENT '任务编号',
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    priority TINYINT NOT NULL COMMENT '优先级',
    creator_id VARCHAR(64) NOT NULL COMMENT '创建人UserID',
    assignee_id VARCHAR(64) NOT NULL COMMENT '执行人UserID',
    estimated_duration INT COMMENT '预估时长（分钟）',
    actual_start_time DATETIME COMMENT '实际开始时间',
    actual_deadline DATETIME COMMENT '实际截止时间',
    status VARCHAR(50) NOT NULL COMMENT '最终状态',
    source_remark VARCHAR(200) COMMENT '来源备注',
    is_self_assigned BOOLEAN DEFAULT FALSE COMMENT '是否自己发给自己',
    completed_at DATETIME COMMENT '完成时间',
    withdrawn_at DATETIME COMMENT '撤回时间',
    archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    original_created_at DATETIME NOT NULL COMMENT '原始创建时间',
    original_updated_at DATETIME NOT NULL COMMENT '原始更新时间',
    INDEX idx_archived_at (archived_at),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_creator_id (creator_id),
    INDEX idx_original_created_at (original_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史任务归档表';

-- 幂等性键值表
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    idempotency_key VARCHAR(64) NOT NULL UNIQUE COMMENT '幂等性Key',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    request_hash VARCHAR(64) COMMENT '请求参数哈希',
    response_data JSON COMMENT '响应数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    INDEX idx_expires_at (expires_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等性键值表';

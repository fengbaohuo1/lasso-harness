-- jnf-harness 幂等建表 + 种子（H2 MODE=MySQL / MySQL 双兼容）
-- R9：本文件及种子不得出现任何明文密钥；密钥只存 api_key_ref 引用。

CREATE TABLE IF NOT EXISTS harness_model_setting (
    channel_code  VARCHAR(64)  PRIMARY KEY,
    display_name  VARCHAR(128),
    provider_code VARCHAR(64),
    model_code    VARCHAR(128),
    base_url      VARCHAR(255),
    api_key_ref   VARCHAR(128),
    protocol      VARCHAR(32)  NOT NULL DEFAULT 'openai',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    active        BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS harness_tool_catalog (
    tool_code         VARCHAR(64)  PRIMARY KEY,
    display_name      VARCHAR(128),
    description       VARCHAR(512),
    category          VARCHAR(32),
    requires_approval BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS harness_tool_profile_binding (
    profile_code VARCHAR(32) NOT NULL,
    tool_code    VARCHAR(64) NOT NULL,
    PRIMARY KEY (profile_code, tool_code)
);

-- R8：任务表预留（第 10 节启用），先建索引友好的结构
CREATE TABLE IF NOT EXISTS harness_task (
    task_id            VARCHAR(64)  PRIMARY KEY,
    session_id         VARCHAR(64),
    prompt             TEXT,
    enabled_tools_json TEXT,
    status             VARCHAR(32)  NOT NULL,
    submitted_at       TIMESTAMP(3)
);
CREATE INDEX IF NOT EXISTS idx_harness_task_session ON harness_task (session_id);

-- 插件三表（第 9 节）
CREATE TABLE IF NOT EXISTS harness_plugin_installation (
    plugin_id      VARCHAR(64)  PRIMARY KEY,
    display_name   VARCHAR(128),
    plugin_version VARCHAR(32),
    runtime_type   VARCHAR(32)  NOT NULL,
    source_path    VARCHAR(512),
    entrypoint     VARCHAR(255),
    status         VARCHAR(32)  NOT NULL,
    installed_at   TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS harness_plugin_runtime_binding (
    plugin_id        VARCHAR(64) PRIMARY KEY,
    package_type     VARCHAR(32),
    manifest_path    VARCHAR(512),
    working_directory VARCHAR(512),
    runtime_command  VARCHAR(512),
    bridge_ready     BOOLEAN NOT NULL DEFAULT FALSE,
    message          TEXT,
    checked_at       TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS harness_plugin_config (
    plugin_id    VARCHAR(64)  NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_layer VARCHAR(32)  NOT NULL DEFAULT 'default',
    config_value TEXT,
    updated_at   TIMESTAMP(3),
    PRIMARY KEY (plugin_id, config_key, config_layer)
);

-- 审批历史（R10：审批可追溯）
CREATE TABLE IF NOT EXISTS harness_approval_record (
    id         VARCHAR(64)  PRIMARY KEY,
    session_id VARCHAR(64),
    tool_name  VARCHAR(128),
    args_json  TEXT,
    verdict    VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP(3),
    decided_at TIMESTAMP(3)
);
CREATE INDEX IF NOT EXISTS idx_harness_approval_session ON harness_approval_record (session_id);

-- 后台作业（R7：持久化，不接受重启即丢）
CREATE TABLE IF NOT EXISTS harness_job (
    job_id      VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(128),
    command     TEXT,
    status      VARCHAR(32)  NOT NULL,
    output      TEXT,
    created_at  TIMESTAMP(3),
    finished_at TIMESTAMP(3)
);

-- 调度记录（R7：持久化）
CREATE TABLE IF NOT EXISTS harness_schedule (
    schedule_id VARCHAR(64)  PRIMARY KEY,
    kind        VARCHAR(32),
    name        VARCHAR(128),
    spec        VARCHAR(255),
    state       VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP(3)
);

-- 目标快照（goal 域）
CREATE TABLE IF NOT EXISTS harness_goal_state (
    session_id     VARCHAR(64) PRIMARY KEY,
    goal_id        VARCHAR(64),
    revision       BIGINT,
    title          VARCHAR(255),
    description    TEXT,
    status         VARCHAR(32),
    blocked_reason VARCHAR(255),
    max_rounds     INT,
    rounds_started INT,
    updated_at     TIMESTAMP(3)
);

-- 数字人注册表（第 13 节：身份与连接解耦）
CREATE TABLE IF NOT EXISTS digital_human (
    id              VARCHAR(64) PRIMARY KEY,
    display_name    VARCHAR(128),
    purpose         VARCHAR(255),
    role_tags       VARCHAR(255),
    approval_policy VARCHAR(32) DEFAULT 'WRITE_REQUIRES_APPROVAL',
    created_at      TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS digital_human_endpoint (
    id               VARCHAR(64) PRIMARY KEY,
    digital_human_id VARCHAR(64),
    endpoint_type    VARCHAR(32),
    base_url         VARCHAR(255),
    credential_ref   VARCHAR(128),
    protocol_version VARCHAR(32),
    agent_card       TEXT,
    health_state     VARCHAR(16) DEFAULT 'unknown',
    last_checked_at  TIMESTAMP(3)
);
CREATE INDEX IF NOT EXISTS idx_dh_endpoint_human ON digital_human_endpoint (digital_human_id);

-- 协作房间（第 13b 节：room/participant/room_event）
CREATE TABLE IF NOT EXISTS collaboration_room (
    id                 VARCHAR(64) PRIMARY KEY,
    workspace_id       VARCHAR(64),
    title              VARCHAR(255),
    objective          TEXT,
    orchestration_mode VARCHAR(32) DEFAULT 'MANUAL',
    run_state          VARCHAR(16) DEFAULT 'idle',
    created_at         TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS room_participant (
    id              VARCHAR(64) PRIMARY KEY,
    room_id         VARCHAR(64),
    digital_human_id VARCHAR(64),
    room_role       VARCHAR(32) DEFAULT 'member',
    presence        VARCHAR(16) DEFAULT 'idle',
    joined_at       TIMESTAMP(3),
    UNIQUE (room_id, digital_human_id)
);

CREATE TABLE IF NOT EXISTS room_event (
    id          VARCHAR(64) PRIMARY KEY,
    room_id     VARCHAR(64),
    seq         BIGINT,
    event_type  VARCHAR(48),
    payload     TEXT,
    occurred_at TIMESTAMP(3),
    UNIQUE (room_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_room_event_room_seq ON room_event (room_id, seq);

-- 智能体画像（A1：步数/预算/工具画像/提示词补丁按场景裁运行开销；default 画像可虚拟存在）
CREATE TABLE IF NOT EXISTS harness_agent_profile (
    profile_code               VARCHAR(64)  PRIMARY KEY,
    display_name               VARCHAR(128),
    max_steps_per_turn         INT,
    max_token_continuations    INT,
    context_token_budget       BIGINT,
    keep_recent_messages       INT,
    compaction_threshold_tokens BIGINT,
    max_tokens                 INT,
    temperature                DOUBLE,
    tool_profile               VARCHAR(64)  DEFAULT 'default',
    prompt_extra               TEXT
);


-- 工具目录种子（11 个内置工具；幂等）
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs_read', '读取文件', '读取文件内容', 'fs', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs_read');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs_write', '写入文件', '写入（覆盖）文件', 'fs', TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs_write');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs_search', '文件搜索', 'glob 搜索文件路径', 'fs', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs_search');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs_grep', '内容搜索', '正则搜索文件内容', 'fs', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs_grep');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs_read_image', '读取图片', '读取图片为 data URI', 'fs', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs_read_image');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'str_replace_editor', '精确替换', '唯一命中的字符串替换', 'fs', TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'str_replace_editor');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'shell_execute', '执行命令', 'shell 命令执行（策略受控）', 'shell', TRUE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'shell_execute');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'web_fetch', '抓取网页', '抓取 URL 内容', 'web', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'web_fetch');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'web_search', '网页搜索', '搜索网页', 'web', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'web_search');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'ask_user_question', '向用户提问', '澄清问题并等待回答', 'ask', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'ask_user_question');
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'todo_write', '待办更新', '整表更新待办清单', 'todo', FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'todo_write');

-- default 画像绑定全部工具（幂等）
INSERT INTO harness_tool_profile_binding (profile_code, tool_code)
SELECT 'default', tool_code FROM harness_tool_catalog
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_profile_binding WHERE profile_code = 'default');

-- 工具目录种子自愈（修复历史 GBK 乱码行：SQL 初始化曾按平台编码解码 UTF-8 脚本）
UPDATE harness_tool_catalog SET display_name = '读取文件', description = '读取文件内容' WHERE tool_code = 'fs_read';
UPDATE harness_tool_catalog SET display_name = '写入文件', description = '写入（覆盖）文件' WHERE tool_code = 'fs_write';
UPDATE harness_tool_catalog SET display_name = '文件搜索', description = 'glob 搜索文件路径' WHERE tool_code = 'fs_search';
UPDATE harness_tool_catalog SET display_name = '内容搜索', description = '正则搜索文件内容' WHERE tool_code = 'fs_grep';
UPDATE harness_tool_catalog SET display_name = '读取图片', description = '读取图片为 data URI' WHERE tool_code = 'fs_read_image';
UPDATE harness_tool_catalog SET display_name = '精确替换', description = '唯一命中的字符串替换' WHERE tool_code = 'str_replace_editor';
UPDATE harness_tool_catalog SET display_name = '执行命令', description = 'shell 命令执行（策略受控）' WHERE tool_code = 'shell_execute';
UPDATE harness_tool_catalog SET display_name = '抓取网页', description = '抓取 URL 内容' WHERE tool_code = 'web_fetch';
UPDATE harness_tool_catalog SET display_name = '网页搜索', description = '搜索网页' WHERE tool_code = 'web_search';
UPDATE harness_tool_catalog SET display_name = '向用户提问', description = '澄清问题并等待回答' WHERE tool_code = 'ask_user_question';
UPDATE harness_tool_catalog SET display_name = '待办更新', description = '整表更新待办清单' WHERE tool_code = 'todo_write';

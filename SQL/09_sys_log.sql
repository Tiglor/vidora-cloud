-- ============================================================
-- 审计日志表（落在 user_service 库）+ 管理端「日志审计」菜单种子
--
-- 为什么在 user_service：操作日志要显示「谁做的」，登录日志要显示「哪个账号」，
-- 两者都要联 sys_user 取昵称。审计表放别的库里就只能写入时冗余昵称，而用户改名后
-- 冗余值就永远停在旧名字上。同库查询侧现场批量补，不落冗余。
--
-- 为什么单独一个 09 文件：表结构属于 common-log（实体类 OperLogEntity / LoginLogEntity
-- 就在那里），菜单种子属于管理端。两者都不是某一业务域的表，塞进 01 会让「用户服务」
-- 看起来像拥有它们，实际写入方是全部业务服务。
--
-- 这张表不提供删除接口，也不做定时清空：能被随手清空的审计表等于没有审计表。
-- 保留期靠分区或归档卷在运维侧解决（滚动压缩的 audit.log 是同一份数据的副本）。
-- ============================================================

USE `user_service`;

-- 操作审计日志
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `trace_id`         VARCHAR(32)     DEFAULT NULL COMMENT '链路ID，与 logs/<服务>/app.log 里的 traceId 同值',
    `title`            VARCHAR(50)     NOT NULL COMMENT '模块标题，取自 @OperLog(title=…)，管理端按它筛选',
    `business_type`    TINYINT         NOT NULL DEFAULT 0 COMMENT '业务类型：0-其他 1-新增 2-修改 3-删除 4-授权 5-导出 6-导入 7-清空 8-审核 9-状态变更',
    `method`           VARCHAR(200)    DEFAULT NULL COMMENT '目标方法，形如 UserController.remove',
    `request_method`   VARCHAR(10)     DEFAULT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    `oper_user_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID；昵称不落库，由查询侧同库批量补',
    `oper_client_key`  VARCHAR(64)     DEFAULT NULL COMMENT '发起端：web/mobile/admin',
    `oper_ip`          VARCHAR(255)    DEFAULT NULL COMMENT '来源IP（取 X-Forwarded-For 第一跳）',
    `oper_url`         VARCHAR(500)    DEFAULT NULL COMMENT '请求URI',
    `oper_param`       VARCHAR(2000)   DEFAULT NULL COMMENT '入参JSON，已脱敏+截断；标了 saveParam=false 的接口为空',
    `json_result`      VARCHAR(2000)   DEFAULT NULL COMMENT '返回值JSON，默认不落，只有明确审计返回值时才有值',
    `status`           TINYINT         NOT NULL DEFAULT 1 COMMENT '结果：0-失败 1-成功',
    `error_msg`        VARCHAR(2000)   DEFAULT NULL COMMENT '失败时的根因异常消息',
    `cost_time`        BIGINT          DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_title` (`title`),
    KEY `idx_business_type` (`business_type`),
    KEY `idx_oper_user_id` (`oper_user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

-- 登录日志：成功与失败都记
CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `trace_id`     VARCHAR(32)     DEFAULT NULL COMMENT '链路ID',
    `username`     VARCHAR(50)     NOT NULL COMMENT '登录账号（本项目即手机号），账号不存在时也照记',
    `user_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '命中的用户ID，登录失败为 NULL',
    `client_key`   VARCHAR(64)     DEFAULT NULL COMMENT '发起端：web/mobile/admin；客户端校验失败时为 NULL',
    `ip`           VARCHAR(255)    DEFAULT NULL COMMENT '来源IP',
    `user_agent`   VARCHAR(500)    DEFAULT NULL COMMENT 'User-Agent 原文，超长截断',
    `status`       TINYINT         NOT NULL DEFAULT 1 COMMENT '结果：0-失败 1-成功',
    `msg`          VARCHAR(200)    DEFAULT NULL COMMENT '失败原因，成功为空',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_username` (`username`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志（成功与失败都记）';

-- ------------------------------------------------------------
-- 管理端菜单：日志审计（顶级目录 + 两个查询页 + 两个权限位）
-- id 从 49 起接 01_user_service.sql 里已用到了 48
-- icon 是 Element Plus 图标组件名，写错不报错、只会静默渲染成空白
-- 页面菜单本身不挂 permission_code，权限位挂在按钮上（与客户端管理 33-37 一致）
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`) VALUES
(49, 0,  '日志审计',     1, '/log',          NULL,                  'Document', 7, NULL,             1),
(50, 49, '操作日志',     2, '/log/oper',     'pages/log/operLog',   'Tickets',  1, NULL,             1),
(51, 49, '登录日志',     2, '/log/login',    'pages/log/loginLog',  'Key',      2, NULL,             1),
(52, 50, '操作日志查询', 3, NULL,            NULL,                  NULL,       1, 'operlog:list',   0),
(53, 51, '登录日志查询', 3, NULL,            NULL,                  NULL,       1, 'loginlog:list',  0);

-- 仅超级管理员可看：审计表里带着入参、返回值和来源IP
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,49),(1,50),(1,51),(1,52),(1,53);

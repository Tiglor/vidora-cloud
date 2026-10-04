-- 视频播放平台 - 用户服务 SQL（user_service）
-- 用途：用户注册/登录、OAuth2 认证、关注/粉丝、用户画像

CREATE DATABASE IF NOT EXISTS `user_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `user_service`;

-- 用户主表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号（登录名）',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
    `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `password_hash` VARCHAR(128) NOT NULL COMMENT '密码哈希（BCrypt）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
    `birthday` DATE DEFAULT NULL COMMENT '生日',
    `bio` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    `follow_count` INT NOT NULL DEFAULT 0 COMMENT '关注数',
    `follower_count` INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
    `region` VARCHAR(50) DEFAULT NULL COMMENT '地区',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

-- 用户关注关系表
CREATE TABLE IF NOT EXISTS `user_follow` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关注者ID',
    `follow_user_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
    KEY `idx_follow_user_id` (`follow_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';

-- OAuth2 第三方登录绑定表
CREATE TABLE IF NOT EXISTS `user_oauth_bind` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `oauth_type` VARCHAR(20) NOT NULL COMMENT '第三方类型：wechat/alipay/apple',
    `oauth_id` VARCHAR(100) NOT NULL COMMENT '第三方唯一标识',
    `oauth_info` JSON DEFAULT NULL COMMENT '第三方原始信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth` (`oauth_type`, `oauth_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方登录绑定表';

-- ============================================================
-- RBAC 权限与菜单控制（角色 / 菜单 / 用户-角色 / 角色-菜单）
-- ============================================================

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码，如 ROLE_ADMIN / ROLE_USER',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 菜单/权限表（目录、菜单、按钮三级；permission_code 用于接口级鉴权）
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单ID，0-顶级',
    `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
    `menu_type` TINYINT NOT NULL DEFAULT 2 COMMENT '类型：1-目录 2-菜单 3-按钮',
    `path` VARCHAR(200) DEFAULT NULL COMMENT '前端路由路径',
    `component` VARCHAR(200) DEFAULT NULL COMMENT '前端组件路径',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `permission_code` VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 video:upload（按钮/接口级鉴权用）',
    `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示：0-隐藏 1-显示',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单与权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- 角色-菜单关联表（授权：某角色能看到哪些菜单/拥有哪些按钮权限）
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单关联表';

-- ---------- 初始数据 ----------
INSERT IGNORE INTO `sys_role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'ROLE_ADMIN', '超级管理员', '拥有全部菜单与按钮权限'),
(2, 'ROLE_USER',  '普通用户',   '仅可浏览视频与发表评论');

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`) VALUES
(1,  0, '视频管理',   1, '/video',        NULL,                 'video',   1, NULL,             1),
(2,  1, '视频列表',   2, '/video/list',   'pages/video/list',   'list',    1, 'video:list',     1),
(3,  1, '视频上传',   2, '/video/upload', 'pages/video/upload', 'upload',  2, 'video:upload',   1),
(4,  1, '视频删除',   3, NULL,            NULL,                 NULL,      3, 'video:delete',   0),
(5,  0, '互动管理',   1, '/interact',     NULL,                 'chat',    2, NULL,             1),
(6,  5, '评论列表',   2, '/interact/comment', 'pages/interact/comment', 'comment', 1, 'comment:list', 1),
(7,  5, '评论删除',   3, NULL,            NULL,                 NULL,      2, 'comment:delete', 0),
(8,  0, '系统管理',   1, '/system',       NULL,                 'setting', 3, NULL,             1),
(9,  8, '用户管理',   2, '/system/user',  'pages/system/user',  'user',    1, 'user:list',      1),
(10, 8, '角色管理',   2, '/system/role',  'pages/system/role',  'role',    2, 'role:list',      1),
(11, 8, '菜单管理',   2, '/system/menu',  'pages/system/menu',  'menu',    3, 'menu:list',      1),
-- 视频转码（按钮：video-service /transcode 接口所需权限；菜单树不展示）
(12, 1,  '视频转码',   3, NULL,            NULL,                 NULL,      4, 'video:transcode', 0),
-- 菜单管理下的按钮权限（MenuController 的 @PreAuthorize 校验）
(13, 11, '新增菜单',   3, NULL,            NULL,                 NULL,      1, 'menu:add',       0),
(14, 11, '编辑菜单',   3, NULL,            NULL,                 NULL,      2, 'menu:edit',      0),
(15, 11, '删除菜单',   3, NULL,            NULL,                 NULL,      3, 'menu:delete',    0),
(16, 11, '分配权限',   3, NULL,            NULL,                 NULL,      4, 'menu:assign',    0),
-- 角色管理下的按钮权限（RoleController 的 @PreAuthorize 校验）
(17, 10, '分配角色',   3, NULL,            NULL,                 NULL,      1, 'role:assign',    0),
-- 互动管理下的审核类按钮权限（CommentController / DanmakuController 的 @PreAuthorize 校验）
-- 发评论、发弹幕、点赞只要登录即可，不占权限位；这里只管「能动别人内容」的操作
(18, 5,  '评论审核',   3, NULL,            NULL,                 NULL,      3, 'comment:audit',  0),
(19, 5,  '弹幕屏蔽',   3, NULL,            NULL,                 NULL,      4, 'danmaku:manage', 0),
-- 站内通知下发（MessageController /messages/notify 的 @PreAuthorize 校验）
-- 这个接口能往任何人收件箱里塞一条「系统」消息，绝不能对普通用户开放
(20, 8,  '通知下发',   3, NULL,            NULL,                 NULL,      4, 'message:send',   0),
-- 内容管理（content-service）：分类/标签是字典维护，流配置和热搜是运营位，安全审核是审核台
-- 读取接口只要登录即可，不占权限位；这里五个权限位全是「能改」的操作
(21, 0,  '内容管理',   1, '/content',      NULL,                 'tag',     4, NULL,             1),
(22, 21, '分类管理',   3, NULL,            NULL,                 NULL,      1, 'content:category:manage', 0),
(23, 21, '标签管理',   3, NULL,            NULL,                 NULL,      2, 'content:tag:manage',      0),
(24, 21, '推荐流配置', 3, NULL,            NULL,                 NULL,      3, 'content:feed:manage',     0),
(25, 21, '热搜运营',   3, NULL,            NULL,                 NULL,      4, 'content:hotsearch:manage', 0),
(26, 21, '安全审核',   3, NULL,            NULL,                 NULL,      5, 'content:audit:manage',    0),
-- 推荐管理（recommend-service）：日常读写和不可逆清理分开授权
-- 算法任务的服务账号只需要 recommend:manage，不该顺手拿到清空候选表的能力
(27, 0,  '推荐管理',   1, '/recommend',    NULL,                 'star',    5, NULL,             1),
(28, 27, '推荐运维',   3, NULL,            NULL,                 NULL,      1, 'recommend:manage', 0),
(29, 27, '推荐数据清理', 3, NULL,          NULL,                 NULL,      2, 'recommend:purge',  0),
-- 搜索管理（search-service）：建议词是运营维护的字典，统计是原始词频
-- 用户自己的搜索历史不占权限位，靠登录态 + user_id 过滤；联想框只要登录
-- 统计连读都要权限：全站词频能反推出用户群体在找什么，包括站内没有的片源
(30, 0,  '搜索管理',   1, '/search',       NULL,                 'search',  6, NULL,             1),
(31, 30, '建议词管理', 3, NULL,            NULL,                 NULL,      1, 'search:suggest:manage', 0),
(32, 30, '搜索统计',   3, NULL,            NULL,                 NULL,      2, 'search:stat:view',      0);

-- 管理员：全部菜单 + 全部按钮权限（含上述 menu:/role:/video:/comment:/danmaku:/message:/content:/recommend:/search: 接口级权限）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),
(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),(1,20),
(1,21),(1,22),(1,23),(1,24),(1,25),(1,26),(1,27),(1,28),(1,29),
(1,30),(1,31),(1,32);

-- 普通用户：视频列表 + 互动（不含删除类按钮、不含系统管理、不含管理端按钮）
-- 注：当前 ROLE_USER 按设计仅"浏览+评论"；若需放开普通用户上传，把菜单ID 3(video:upload) 加入下方即可。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2,1),(2,2),(2,5),(2,6);

-- 用户画像标签表
CREATE TABLE IF NOT EXISTS `user_tag` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `tag_name` VARCHAR(50) NOT NULL COMMENT '标签名',
    `tag_weight` INT NOT NULL DEFAULT 0 COMMENT '标签权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_name`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像标签表';

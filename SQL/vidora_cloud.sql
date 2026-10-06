-- ============================================================
-- vidora 后端主库全量基线 —— SQL/ 下唯一的文件
--
-- 组织方式：一个库、一个文件，文件内按业务功能分节，节序就是依赖序（从账号与
-- 权限开始，到推荐结束）。节名与表名前缀一一对应，找表先看前缀再看节。
--
-- 库名取 vidora_cloud，不叫 vidora：vidora 是产品名，拿它当库名就把 `vidora_{域}`
-- 这套命名空间占死了，将来按业务拆库只能另起一套前缀。cloud 与仓库名对齐，说的就是
-- 「后端这一包共用的主库」。现在 8 节合住一个库是过渡态，真要拆就是一节搬一个库
-- （vidora_user / vidora_video / vidora_content / vidora_interact / vidora_message /
-- vidora_search / vidora_recommend，审计可再单立 vidora_log）；表名前缀已经写好归属，
-- 拆的改动只有各服务 datasource URL 和 CREATE DATABASE 那两行。
--
-- 这个文件只写目标态：改结构就改对应节里的 CREATE TABLE，不在这里追加 ALTER，
-- 也不留迁移脚本。理由是一次改造出的两份文件（基线 + 迁移）必然逐字重复、各改
-- 各的，几周后没人判断得出哪份是准的。
--
-- DDL 用普通写法，不写 IF NOT EXISTS：库或表已存在就直接报错停下，而不是静默跳过。
-- 静默跳过会让人以为改动已经生效，其实存量库一个字都没变。所以改完结构的重建方式是
-- DROP DATABASE vidora_cloud; 后重跑本文件；不方便重建的，手工执行本次变更等价的 ALTER。
-- 只有种子数据用 INSERT IGNORE，让重复导入不炸在已有行上。
--
-- 硬约束：每张表带表级 COMMENT、每一列带列级 COMMENT（含 id / create_time /
-- update_time / is_deleted 这四个看起来不用解释的），枚举列要把档位逐个写出来；
-- 种子数据写死主键 id（三端按 id 引用分区与菜单，自增值受插入删除历史影响会整体错位）；
-- 字符集与排序规则一律 utf8mb4 / utf8mb4_unicode_ci。
--
-- 执行：mysql -u root -p < SQL/vidora_cloud.sql
-- 默认由用户手工执行，AI 不连库跑 DDL（除非用户在当前会话点名）。
-- 规范出处：.code/coding-standards.md 第 12 节、.code/skills/add-a-db-migration.md
-- ============================================================

CREATE DATABASE `vidora_cloud` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `vidora_cloud`;

-- ============================================================
-- 一、用户与权限（system-service）
--     账号与登录、OAuth2 第三方绑定、关注关系、用户画像标签、
--     RBAC 角色/菜单/授权、sys_client 多端客户端配置
-- ============================================================

-- 用户主表
CREATE TABLE `sys_user`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone`          VARCHAR(20)  NOT NULL COMMENT '手机号（登录名）',
    `email`          VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `nickname`       VARCHAR(50)  NOT NULL COMMENT '昵称',
    `avatar_url`     VARCHAR(500)          DEFAULT NULL COMMENT '头像URL',
    `password_hash`  VARCHAR(128) NOT NULL COMMENT '密码哈希（BCrypt）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `gender`         TINYINT               DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
    `birthday`       DATE                  DEFAULT NULL COMMENT '生日',
    `bio`            VARCHAR(500)          DEFAULT NULL COMMENT '个人简介',
    `follow_count`   INT          NOT NULL DEFAULT 0 COMMENT '关注数',
    `follower_count` INT          NOT NULL DEFAULT 0 COMMENT '粉丝数',
    `region`         VARCHAR(50)           DEFAULT NULL COMMENT '地区',
    `theme_key`      VARCHAR(32)  NOT NULL DEFAULT 'light-blue' COMMENT '主题标识，对应前端主题包 key',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`),
    KEY              `idx_status` (`status`),
    KEY              `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

-- 用户关注关系表
CREATE TABLE `user_follow`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '关注者ID',
    `follow_user_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注者ID',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
    KEY              `idx_follow_user_id` (`follow_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';

-- OAuth2 第三方登录绑定表
CREATE TABLE `user_oauth_bind`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `oauth_type`  VARCHAR(20)  NOT NULL COMMENT '第三方类型：wechat/alipay/apple',
    `oauth_id`    VARCHAR(100) NOT NULL COMMENT '第三方唯一标识',
    `oauth_info`  JSON                  DEFAULT NULL COMMENT '第三方原始信息',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth` (`oauth_type`, `oauth_id`),
    KEY           `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方登录绑定表';

-- ============================================================
-- RBAC 权限与菜单控制（角色 / 菜单 / 用户-角色 / 角色-菜单）
-- ============================================================

-- 角色表
CREATE TABLE `sys_role`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码，如 ROLE_ADMIN / ROLE_USER',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255)         DEFAULT NULL COMMENT '角色描述',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 菜单/权限表（目录、菜单、按钮三级；permission_code 用于接口级鉴权）
CREATE TABLE `sys_menu`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单ID，0-顶级',
    `menu_name`       VARCHAR(64) NOT NULL COMMENT '菜单名称',
    `menu_type`       TINYINT     NOT NULL DEFAULT 2 COMMENT '类型：1-目录 2-菜单 3-按钮',
    `path`            VARCHAR(200)         DEFAULT NULL COMMENT '前端路由路径',
    `component`       VARCHAR(200)         DEFAULT NULL COMMENT '前端组件路径',
    `icon`            VARCHAR(100)         DEFAULT NULL COMMENT '菜单图标',
    `sort_order`      INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `permission_code` VARCHAR(100)         DEFAULT NULL COMMENT '权限标识，如 video:upload（按钮/接口级鉴权用）',
    `visible`         TINYINT     NOT NULL DEFAULT 1 COMMENT '是否显示：0-隐藏 1-显示',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY               `idx_parent_id` (`parent_id`),
    KEY               `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单与权限表';

-- 用户-角色关联表
CREATE TABLE `sys_user_role`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role_id`     BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY           `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- 角色-菜单关联表（授权：某角色能看到哪些菜单/拥有哪些按钮权限）
CREATE TABLE `sys_role_menu`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `role_id`     BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `menu_id`     BIGINT UNSIGNED NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY           `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单关联表';

-- ---------- 初始数据 ----------
INSERT
IGNORE INTO `sys_role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'ROLE_ADMIN', '超级管理员', '拥有全部菜单与按钮权限'),
(2, 'ROLE_USER',  '普通用户',   '仅可浏览视频与发表评论');

-- 账号种子：没有这两行，sys_user 是空表，登录链路（auth-service 经 Dubbo 调
-- RemoteUserApi.getLoginUser）查不到任何账号，三端一个都登不进去。
-- 两个账号的密码都是 123456，下面这串是它的 BCrypt 哈希（$2a$10$ 前缀 = strength 10）。
-- BCrypt 自带随机盐，所以每次生成的串都不一样，能用就行，别去和别处的哈希比对字面值。
-- 生产环境必须改掉这两个密码：这份哈希在仓库里是公开的，等于把口令写在明处。
-- theme_key 的合法取值以前端主题清单为准（web src/config/themes.js 的 THEME_OPTIONS：
-- light-blue / pink / green / purple / orange）。后端刻意不校验枚举，写错不会报错，
-- 只会被前端 normalizeThemeKey 静默落回 light-blue，所以这里两个账号故意取不同值。
INSERT
IGNORE INTO `sys_user` (`id`, `phone`, `nickname`, `password_hash`, `status`, `theme_key`) VALUES
(1, '13800138000', '超级管理员', '$2a$10$WxLEve0VjI6CU3AHMt43IeY4N/2HxrzO3GRR3VNY.eEUUOtDINkTm', 1, 'light-blue'),
(2, '13900139000', '普通用户',   '$2a$10$WxLEve0VjI6CU3AHMt43IeY4N/2HxrzO3GRR3VNY.eEUUOtDINkTm', 1, 'pink');

-- id=1 挂 ROLE_ADMIN（全部菜单与按钮权限），id=2 挂 ROLE_USER（浏览 + 评论）。
-- 注册接口给的默认角色也是 2，见 RemoteUserApiImpl.DEFAULT_ROLE_ID。
INSERT
IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1), (2, 2);

-- icon 列存的是 Element Plus 图标组件名（管理端用 <component :is="icon"/> 渲染），
-- 必须是对得上的 PascalCase 名字，写成小写或拼错的图标名不会报错，只是静默渲染成空白
INSERT
IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`) VALUES
(1,  0, '视频管理',   1, '/video',        NULL,                 'VideoCamera',    1, NULL,             1),
(2,  1, '视频列表',   2, '/video/list',   'pages/video/list',   'Film',           1, 'video:list',     1),
(3,  1, '视频上传',   2, '/video/upload', 'pages/video/upload', 'UploadFilled',   2, 'video:upload',   1),
(4,  1, '视频删除',   3, NULL,            NULL,                 NULL,      3, 'video:delete',   0),
(5,  0, '互动管理',   1, '/interact',     NULL,                 'ChatDotRound', 2, NULL,             1),
(6,  5, '评论列表',   2, '/interact/comment', 'pages/interact/comment', 'Comment', 1, 'comment:list', 1),
(7,  5, '评论删除',   3, NULL,            NULL,                 NULL,      2, 'comment:delete', 0),
(8,  0, '系统管理',   1, '/system',       NULL,                 'Setting', 3, NULL,             1),
(9,  8, '用户管理',   2, '/system/user',  'pages/system/user',  'User',    1, 'user:list',      1),
(10, 8, '角色管理',   2, '/system/role',  'pages/system/role',  'Avatar',  2, 'role:list',      1),
(11, 8, '菜单管理',   2, '/system/menu',  'pages/system/menu',  'Menu',    3, 'menu:list',      1),
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
(21, 0,  '内容管理',   1, '/content',      NULL,                 'CollectionTag', 4, NULL,             1),
(22, 21, '分类管理',   3, NULL,            NULL,                 NULL,      1, 'content:category:manage', 0),
(23, 21, '标签管理',   3, NULL,            NULL,                 NULL,      2, 'content:tag:manage',      0),
(24, 21, '推荐流配置', 3, NULL,            NULL,                 NULL,      3, 'content:feed:manage',     0),
(25, 21, '热搜运营',   3, NULL,            NULL,                 NULL,      4, 'content:hotsearch:manage', 0),
(26, 21, '安全审核',   3, NULL,            NULL,                 NULL,      5, 'content:audit:manage',    0),
-- 推荐管理（recommend-service）：日常读写和不可逆清理分开授权
-- 算法任务的服务账号只需要 recommend:manage，不该顺手拿到清空候选表的能力
(27, 0,  '推荐管理',   1, '/recommend',    NULL,                 'Star',    5, NULL,             1),
(28, 27, '推荐运维',   3, NULL,            NULL,                 NULL,      1, 'recommend:manage', 0),
(29, 27, '推荐数据清理', 3, NULL,          NULL,                 NULL,      2, 'recommend:purge',  0),
-- 搜索管理（search-service）：建议词是运营维护的字典，统计是原始词频
-- 用户自己的搜索历史不占权限位，靠登录态 + user_id 过滤；联想框只要登录
-- 统计连读都要权限：全站词频能反推出用户群体在找什么，包括站内没有的片源
(30, 0,  '搜索管理',   1, '/search',       NULL,                 'Search',  6, NULL,             1),
(31, 30, '建议词管理', 3, NULL,            NULL,                 NULL,      1, 'search:suggest:manage', 0),
(32, 30, '搜索统计',   3, NULL,            NULL,                 NULL,      2, 'search:stat:view',      0);

-- 管理员：全部菜单 + 全部按钮权限（含上述 menu:/role:/video:/comment:/danmaku:/message:/content:/recommend:/search: 接口级权限）
INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),
(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),(1,20),
(1,21),(1,22),(1,23),(1,24),(1,25),(1,26),(1,27),(1,28),(1,29),
(1,30),(1,31),(1,32);

-- 普通用户：视频列表 + 互动（不含删除类按钮、不含系统管理、不含管理端按钮）
-- 注：当前 ROLE_USER 按设计仅"浏览+评论"；若需放开普通用户上传，把菜单ID 3(video:upload) 加入下方即可。
INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2,1),(2,2),(2,5),(2,6);

-- -----------------------------------------------------------
-- 客户端配置表（参考 RuoYi-Cloud-Plus sys_client 设计）
-- 不同端可配置独立的 token 过期时间与认证方式
-- -----------------------------------------------------------
CREATE TABLE `sys_client`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '客户端ID',
    `client_id`   VARCHAR(64)  NOT NULL COMMENT '客户端ID(前端写死)',
    `client_key`  VARCHAR(64)  NOT NULL COMMENT '客户端标识: web/mobile/admin',
    `device_type` VARCHAR(32)  NOT NULL COMMENT '设备类型: pc/app',
    `grant_type`  VARCHAR(128) NOT NULL DEFAULT 'password' COMMENT '允许的认证方式',
    `timeout`     INT          NOT NULL DEFAULT 604800 COMMENT 'token固定过期(秒)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_id` (`client_id`),
    UNIQUE KEY `uk_client_key` (`client_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户端配置';

INSERT
IGNORE INTO `sys_client` (`client_id`, `client_key`, `device_type`, `grant_type`, `timeout`) VALUES
('vidora-web-2024',    'web',    'pc',  'password', 604800),   -- 7天
('vidora-mobile-2024', 'mobile', 'app', 'password', 2592000),  -- 30天
('vidora-admin-2024',  'admin',  'pc',  'password', 28800);    -- 8小时

-- 客户端管理菜单权限（挂在"系统管理"目录 id=8 下）
INSERT
IGNORE INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`icon`,`sort_order`,`permission_code`,`visible`) VALUES
(33, 8,  '客户端管理', 2, '/system/client', 'pages/system/client', 'Monitor', 4, NULL,                   1),
(34, 33, '客户端查询', 3, NULL, NULL, NULL, 1, 'system:client:list',   0),
(35, 33, '客户端新增', 3, NULL, NULL, NULL, 2, 'system:client:add',    0),
(36, 33, '客户端修改', 3, NULL, NULL, NULL, 3, 'system:client:edit',   0),
(37, 33, '客户端删除', 3, NULL, NULL, NULL, 4, 'system:client:delete', 0);

INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,33),(1,34),(1,35),(1,36),(1,37);

-- 内容管理下的页面级菜单（原 22-26 均为按钮权限，缺少可导航的菜单页面）
INSERT
IGNORE INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`icon`,`sort_order`,`permission_code`,`visible`) VALUES
(38, 21, '分类管理', 2, '/content/category',  'pages/content/category',  'Folder',      1, NULL, 1),
(39, 21, '标签管理', 2, '/content/tag',       'pages/content/tag',       'PriceTag',    2, NULL, 1),
(40, 21, '热搜运营', 2, '/content/hot-search','pages/content/hotSearch', 'TrendCharts', 3, NULL, 1),
(41, 21, '安全审核', 2, '/content/audit',     'pages/content/audit',     'DocumentChecked', 4, NULL, 1),
-- 推荐管理下的页面级菜单
(42, 27, '推荐流配置', 2, '/recommend/feed-config', 'pages/recommend/feedConfig', 'Setting',  1, NULL, 1),
(43, 27, '算法配置',   2, '/recommend/algo-config', 'pages/recommend/algoConfig', 'Cpu',      2, NULL, 1),
-- 搜索管理下的页面级菜单
(44, 30, '建议词管理', 2, '/search/suggest',  'pages/search/suggest',    'EditPen',     1, NULL, 1),
(45, 30, '搜索统计',   2, '/search/stats',    'pages/search/stats',      'DataLine',    2, NULL, 1);

INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1,38),(1,39),(1,40),(1,41),(1,42),(1,43),(1,44),(1,45);

-- 用户管理下的按钮权限（UserController 的 @PreAuthorize 校验）
-- 读列表用已有的 user:list（菜单 9）；GET /users/{id} 刻意不挂权限位——
-- video-service 的 /videos/{id}/owner 是拿「浏览者自己的 token」Feign 调过去的，
-- 挂上 user:list 会让移动端视频详情页看 UP 主昵称时 403。
INSERT
IGNORE INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`icon`,`sort_order`,`permission_code`,`visible`) VALUES
(46, 9, '用户新增', 3, NULL, NULL, NULL, 1, 'user:add',    0),
(47, 9, '用户修改', 3, NULL, NULL, NULL, 2, 'user:edit',   0),
(48, 9, '用户删除', 3, NULL, NULL, NULL, 3, 'user:delete', 0);

INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,46),(1,47),(1,48);

-- 用户画像标签表
CREATE TABLE `user_tag`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `tag_name`    VARCHAR(50) NOT NULL COMMENT '标签名',
    `tag_weight`  INT         NOT NULL DEFAULT 0 COMMENT '标签权重',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tag` (`user_id`, `tag_name`),
    KEY           `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像标签表';



-- ============================================================
-- 二、审计日志
--     写入方是全部业务服务（common-log 的 @OperLog 切面 → LogSink），
--     查询与管理端页面在 system-service；表结构属于 common-log
--     （OperLogEntity / LoginLogEntity 在那里）。
--
--     操作日志要显示「谁做的」、登录日志要显示「哪个账号」，两者都要联 sys_user
--     取昵称 —— 所以昵称由查询侧现场批量补，不落冗余列：用户改名后冗余值会永远
--     停在旧名字上（OperLogVO 类注释解释了这件事）。
--
--     这两张表不提供删除接口，也不做定时清空：能被随手清空的审计表等于没有审计表，
--     「谁删了视频」和「谁删了那条记录」必须是两件事。保留期靠分区或归档在运维侧解决。
--     logs/<服务名>/audit.log 是同一份数据的本地副本，不是审计的存储位置。
-- ============================================================

-- 操作审计日志
CREATE TABLE `sys_oper_log`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `trace_id`        VARCHAR(32)          DEFAULT NULL COMMENT '链路ID，与 logs/<服务>/app.log 里的 traceId 同值',
    `title`           VARCHAR(50) NOT NULL COMMENT '模块标题，取自 @OperLog(title=…)，管理端按它筛选',
    `business_type`   TINYINT     NOT NULL DEFAULT 0 COMMENT '业务类型：0-其他 1-新增 2-修改 3-删除 4-授权 5-导出 6-导入 7-清空 8-审核 9-状态变更',
    `method`          VARCHAR(200)         DEFAULT NULL COMMENT '目标方法，形如 UserController.remove',
    `request_method`  VARCHAR(10)          DEFAULT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    `oper_user_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID；昵称不落库，由查询侧同库批量补',
    `oper_client_key` VARCHAR(64)          DEFAULT NULL COMMENT '发起端：web/mobile/admin',
    `oper_ip`         VARCHAR(255)         DEFAULT NULL COMMENT '来源IP（取 X-Forwarded-For 第一跳）',
    `oper_url`        VARCHAR(500)         DEFAULT NULL COMMENT '请求URI',
    `oper_param`      VARCHAR(2000)        DEFAULT NULL COMMENT '入参JSON，已脱敏+截断；标了 saveParam=false 的接口为空',
    `json_result`     VARCHAR(2000)        DEFAULT NULL COMMENT '返回值JSON，默认不落，只有明确审计返回值时才有值',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '结果：0-失败 1-成功',
    `error_msg`       VARCHAR(2000)        DEFAULT NULL COMMENT '失败时的根因异常消息',
    `cost_time`       BIGINT               DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY               `idx_create_time` (`create_time`),
    KEY               `idx_title` (`title`),
    KEY               `idx_business_type` (`business_type`),
    KEY               `idx_oper_user_id` (`oper_user_id`),
    KEY               `idx_status` (`status`),
    KEY               `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

-- 登录日志：成功与失败都记
CREATE TABLE `sys_login_log`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `trace_id`    VARCHAR(32)          DEFAULT NULL COMMENT '链路ID',
    `username`    VARCHAR(50) NOT NULL COMMENT '登录账号（本项目即手机号），账号不存在时也照记',
    `user_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '命中的用户ID，登录失败为 NULL',
    `client_key`  VARCHAR(64)          DEFAULT NULL COMMENT '发起端：web/mobile/admin；客户端校验失败时为 NULL',
    `ip`          VARCHAR(255)         DEFAULT NULL COMMENT '来源IP',
    `user_agent`  VARCHAR(500)         DEFAULT NULL COMMENT 'User-Agent 原文，超长截断',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '结果：0-失败 1-成功',
    `msg`         VARCHAR(200)         DEFAULT NULL COMMENT '失败原因，成功为空',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY           `idx_create_time` (`create_time`),
    KEY           `idx_username` (`username`),
    KEY           `idx_user_id` (`user_id`),
    KEY           `idx_status` (`status`),
    KEY           `idx_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志（成功与失败都记）';

-- ------------------------------------------------------------
-- 管理端菜单：日志审计（顶级目录 + 两个查询页 + 两个权限位）
-- id 从 49 起，接本文件上面已用到的 48
-- icon 是 Element Plus 图标组件名，写错不报错、只会静默渲染成空白
-- 页面菜单本身不挂 permission_code，权限位挂在按钮上（与客户端管理 33-37 一致）
-- ------------------------------------------------------------
INSERT
IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`) VALUES
(49, 0,  '日志审计',     1, '/log',          NULL,                  'Document', 7, NULL,             1),
(50, 49, '操作日志',     2, '/log/oper',     'pages/log/operLog',   'Tickets',  1, NULL,             1),
(51, 49, '登录日志',     2, '/log/login',    'pages/log/loginLog',  'Key',      2, NULL,             1),
(52, 50, '操作日志查询', 3, NULL,            NULL,                  NULL,       1, 'operlog:list',   0),
(53, 51, '登录日志查询', 3, NULL,            NULL,                  NULL,       1, 'loginlog:list',  0);

-- 仅超级管理员可看：审计表里带着入参、返回值和来源IP
INSERT
IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,49),(1,50),(1,51),(1,52),(1,53);


-- ============================================================
-- 三、视频与转码（video-service）
--     视频元数据、分片上传与 MD5 秒传、转码调度与 HLS 切片、审核状态
-- ============================================================

-- 视频主表
CREATE TABLE `video_info`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '视频ID',
    `video_key`     VARCHAR(64)  NOT NULL COMMENT '业务唯一Key（UUID）',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '上传用户ID',
    `title`         VARCHAR(200) NOT NULL COMMENT '视频标题',
    `description`   VARCHAR(2000)         DEFAULT NULL COMMENT '视频描述',
    `cover_url`     VARCHAR(500)          DEFAULT NULL COMMENT '封面图URL',
    `source_url`    VARCHAR(500)          DEFAULT NULL COMMENT '源片URL',
    `duration`      INT          NOT NULL DEFAULT 0 COMMENT '视频时长（秒）',
    `width`         INT                   DEFAULT 0 COMMENT '视频宽度',
    `height`        INT                   DEFAULT 0 COMMENT '视频高度',
    `file_size`     BIGINT                DEFAULT 0 COMMENT '源片文件大小（字节）',
    `file_hash`     VARCHAR(64)           DEFAULT NULL COMMENT '源片MD5（秒传校验）',
    `storage_path`  VARCHAR(500)          DEFAULT NULL COMMENT '存储对象名：MinIO objectName 或本地相对路径',
    `hls_url`       VARCHAR(500)          DEFAULT NULL COMMENT '转码后 HLS 播放索引（m3u8）地址',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-上传中 1-转码中 2-审核中 3-已发布 4-已下架',
    `visibility`    TINYINT      NOT NULL DEFAULT 1 COMMENT '可见性：0-私密 1-公开 2-仅粉丝',
    `category_id`   BIGINT                DEFAULT 0 COMMENT '分类ID',
    `tags`          JSON                  DEFAULT NULL COMMENT '标签数组',
    `play_count`    BIGINT       NOT NULL DEFAULT 0 COMMENT '播放数',
    `like_count`    BIGINT       NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '评论数',
    `share_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '分享数',
    `publish_time`  DATETIME              DEFAULT NULL COMMENT '发布时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_video_key` (`video_key`),
    -- 不能对 file_hash 建唯一索引：秒传复用的是存储层 blob，不是视频记录。
    -- 同一文件被不同用户（或同一用户多次）投稿是合法的，各自要有独立的 video_info 行。
    KEY             `idx_file_hash` (`file_hash`),
    KEY             `idx_user_id` (`user_id`),
    KEY             `idx_status_publish` (`status`, `publish_time`),
    KEY             `idx_category_id` (`category_id`),
    KEY             `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频主表';

-- 视频转码任务表（单次转码任务 = 一次多清晰度 HLS 转码，包含全部清晰度档位）
-- 说明：一次 ffmpeg 调用产出 master.m3u8 + 各档位切片，故以“一视频一任务”建模，档位名存于 renditions(JSON)
CREATE TABLE `video_transcode_task`
(
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `video_id`    BIGINT        DEFAULT NULL COMMENT '关联 video_info.id',
    `video_key`   VARCHAR(64)   DEFAULT NULL COMMENT '视频唯一 key',
    `status`      TINYINT       DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-成功 3-失败',
    `progress`    TINYINT       DEFAULT 0 COMMENT '进度 0-100',
    `source_path` VARCHAR(512)  DEFAULT NULL COMMENT '源片存储对象名',
    `hls_path`    VARCHAR(512)  DEFAULT NULL COMMENT '主播放列表 objectName（master.m3u8）',
    `renditions`  VARCHAR(255)  DEFAULT NULL COMMENT '已生成档位(JSON 数组，如 ["1080p","720p"])',
    `error_msg`   VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    `retry_count` TINYINT       DEFAULT 0 COMMENT '已重试次数',
    `finished_at` DATETIME      DEFAULT NULL COMMENT '完成时间',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
    `is_deleted`  TINYINT       DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY           `idx_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频转码任务表';

-- 视频分片上传记录表（断点续传 + MD5 秒传）
CREATE TABLE `video_multipart_upload`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上传ID',
    `upload_id`        VARCHAR(64)  NOT NULL COMMENT '上传任务ID',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '发起上传的用户ID',
    `video_id`         BIGINT UNSIGNED DEFAULT NULL COMMENT '关联视频ID',
    `file_name`        VARCHAR(200) NOT NULL COMMENT '原始文件名',
    `file_hash`        VARCHAR(64)  NOT NULL COMMENT '文件MD5',
    `file_size`        BIGINT UNSIGNED NOT NULL COMMENT '文件总字节数',
    `chunk_size`       INT          NOT NULL COMMENT '分片大小',
    `total_chunks`     INT          NOT NULL COMMENT '总分片数',
    `completed_chunks` INT          NOT NULL DEFAULT 0 COMMENT '已完成分片数',
    `bucket`           VARCHAR(100) NOT NULL COMMENT 'MinIO bucket',
    `object_key`       VARCHAR(500) NOT NULL COMMENT 'MinIO对象Key',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-上传中 1-已完成 2-已合并',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_id` (`upload_id`),
    -- 一次上传尝试一行，不是「一个用户一个文件一行」：同一文件再投一次要能开新会话、建新视频。
    -- 断点续传只是从这个索引里挑最近一条未合并的会话继续传，跨用户越权由代码校验 user_id 拦截
    KEY                `idx_user_hash` (`user_id`, `file_hash`),
    KEY                `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频分片上传记录表';

-- 视频审核记录表
CREATE TABLE `video_audit_record`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审核ID',
    `video_id`    BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `audit_type`  TINYINT  NOT NULL DEFAULT 1 COMMENT '审核类型：1-机器审核 2-人工审核',
    `status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '审核结果：0-待审核 1-通过 2-拒绝',
    `reason`      VARCHAR(500)      DEFAULT NULL COMMENT '拒绝原因',
    `auditor_id`  BIGINT            DEFAULT NULL COMMENT '审核人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY           `idx_video_id` (`video_id`),
    KEY           `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频审核记录表';


-- ============================================================
-- 四、内容运营（content-service）
--     分类与标签字典、首页信息流配置、热搜榜单、内容安全审核台
-- ============================================================

-- 视频分类表
CREATE TABLE `content_category`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名',
    `icon_url`    VARCHAR(500)         DEFAULT NULL COMMENT '分类图标',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一父分类下名字不许重复。两列都是 NOT NULL：MySQL 的唯一索引把 NULL 当作互不相同的值，
    -- parent_id 要是可空，这个约束对「顶级分类」就形同虚设。
    -- utf8mb4_unicode_ci 让重名判断天然不区分大小写，服务层的预检查用 SQL = 而不是 Java 比较，语义才对得上。
    UNIQUE KEY `uk_parent_name` (`parent_id`, `name`),
    KEY           `idx_parent_id` (`parent_id`),
    KEY           `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频分类表';

-- 一级分区种子数据
-- id 必须写死：vidora-web 首页/上传页/顶栏导航至今按 id 1-7 引用分区（见 HomeView.vue、
-- UploadView.vue、DefaultLayout.vue），换库时如果靠 AUTO_INCREMENT 顺序生成，
-- 一旦某行被删过，id 就会整体错位，导航点「音乐」出来的是「游戏」的内容。
-- 首页第一项「推荐」刻意不在这里：它的语义是「不带 categoryId 的全站流」，不是一条分类记录。
-- 二级分区也不预置 —— 那正是要交给运营在管理端按业务增删的部分。
INSERT
IGNORE INTO `content_category` (`id`, `parent_id`, `name`, `sort_order`, `status`) VALUES
(1, 0, '动画', 1, 1),
(2, 0, '番剧', 2, 1),
(3, 0, '音乐', 3, 1),
(4, 0, '游戏', 4, 1),
(5, 0, '科技', 5, 1),
(6, 0, '生活', 6, 1),
(7, 0, '影视', 7, 1);

-- 视频标签表
CREATE TABLE `content_tag`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '标签名',
    `use_count`   BIGINT      NOT NULL DEFAULT 0 COMMENT '使用次数',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY           `idx_status_count` (`status`, `use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频标签表';

-- 推荐流配置表
CREATE TABLE `content_feed_config`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `feed_type`    VARCHAR(20) NOT NULL COMMENT '流类型：recommend/hot/follow',
    `config_key`   VARCHAR(50) NOT NULL COMMENT '配置Key',
    `config_value` TEXT COMMENT '配置值（JSON）',
    `description`  VARCHAR(500)         DEFAULT NULL COMMENT '说明',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feed_key` (`feed_type`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐流配置表';

-- 热搜榜单表
CREATE TABLE `content_hot_search`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '热搜ID',
    `keyword`      VARCHAR(100) NOT NULL COMMENT '搜索关键词',
    `heat_score`   INT          NOT NULL DEFAULT 0 COMMENT '热度分',
    `rank`         INT          NOT NULL DEFAULT 0 COMMENT '榜单排名',
    `search_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '搜索次数',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-下线 1-上线',
    `rank_date`    DATE         NOT NULL COMMENT '榜单日期',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword_date` (`keyword`, `rank_date`),
    KEY            `idx_rank_date` (`rank_date`, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热搜榜单表';

-- 内容安全审核表
CREATE TABLE `content_security_audit`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审核ID',
    `target_type`    VARCHAR(20) NOT NULL COMMENT '对象类型：video/comment/danmaku',
    `target_id`      BIGINT UNSIGNED NOT NULL COMMENT '对象ID',
    `risk_level`     TINYINT     NOT NULL DEFAULT 0 COMMENT '风险等级：0-无 1-低 2-中 3-高',
    `risk_label`     VARCHAR(100)         DEFAULT NULL COMMENT '风险标签',
    `machine_result` JSON                 DEFAULT NULL COMMENT '机审结果',
    -- 未复核用 NULL 表示，不是 0：0 在 TINYINT 里既不是放行也不是拦截，会被误读成第三种状态
    `manual_result`  TINYINT              DEFAULT NULL COMMENT '人工复核：NULL-未复核 1-放行 2-拦截',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_target` (`target_type`, `target_id`),
    KEY              `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容安全审核表';


-- ============================================================
-- 五、互动（interact-service）
--     点赞/收藏/分享、评论与回复、弹幕、播放与点赞实时计数
--     关注关系表 user_follow 在第一节（按数据归属放在用户域）
-- ============================================================

-- 点赞/收藏/分享 统一记录表（按类型区分）
CREATE TABLE `interact_action`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '互动ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `target_type` VARCHAR(20) NOT NULL COMMENT '对象类型：video/comment',
    `target_id`   BIGINT UNSIGNED NOT NULL COMMENT '对象ID',
    `action_type` TINYINT     NOT NULL COMMENT '动作类型：1-点赞 2-收藏 3-分享',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-取消 1-有效',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target_action` (`user_id`, `target_type`, `target_id`, `action_type`),
    KEY           `idx_target_action` (`target_type`, `target_id`, `action_type`, `status`),
    KEY           `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞收藏分享记录表';

-- 评论表
CREATE TABLE `interact_comment`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `video_id`    BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
    `parent_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论ID（0为顶层评论）',
    `root_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '根评论ID（用于楼中楼聚合）',
    `content`     VARCHAR(2000) NOT NULL COMMENT '评论内容',
    `like_count`  BIGINT        NOT NULL DEFAULT 0 COMMENT '点赞数',
    `reply_count` INT           NOT NULL DEFAULT 0 COMMENT '回复数',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常 2-审核中',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY           `idx_video_id` (`video_id`, `status`, `create_time`),
    KEY           `idx_root_id` (`root_id`),
    KEY           `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 弹幕表（水平分表候选：按 video_id 或日期拆分）
CREATE TABLE `interact_danmaku`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '弹幕ID',
    `video_id`    BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '发送用户ID',
    `content`     VARCHAR(500)   NOT NULL COMMENT '弹幕内容',
    `appear_time` DECIMAL(10, 3) NOT NULL COMMENT '弹幕出现时间（秒，保留3位小数）',
    `color`       VARCHAR(10)             DEFAULT '#FFFFFF' COMMENT '弹幕颜色',
    `font_size`   TINYINT                 DEFAULT 25 COMMENT '字体大小',
    `position`    TINYINT                 DEFAULT 0 COMMENT '位置：0-滚动 1-顶部 2-底部',
    `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：0-屏蔽 1-正常',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY           `idx_video_time` (`video_id`, `appear_time`),
    KEY           `idx_user_id` (`user_id`),
    KEY           `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='弹幕表';

-- 播放计数表（准实时，用于持久化 Redis 计数）
CREATE TABLE `interact_play_count`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `video_id`      BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `play_count`    BIGINT   NOT NULL DEFAULT 0 COMMENT '播放数',
    `like_count`    BIGINT   NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` BIGINT   NOT NULL DEFAULT 0 COMMENT '评论数',
    `share_count`   BIGINT   NOT NULL DEFAULT 0 COMMENT '分享数',
    `stat_date`     DATE     NOT NULL COMMENT '统计日期',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_video_date` (`video_id`, `stat_date`),
    KEY             `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放计数日表';


-- ============================================================
-- 六、站内消息（message-service）
--     系统通知、互动消息、私信、会话与推送设备（WebSocket + 厂商 Push）
-- ============================================================

-- 消息主表
CREATE TABLE `message_record`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `msg_type`    TINYINT  NOT NULL COMMENT '消息类型：1-系统通知 2-互动消息 3-私信',
    `sender_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发送者ID（0为系统）',
    `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者ID',
    `content`     TEXT     NOT NULL COMMENT '消息内容',
    `extra`       JSON              DEFAULT NULL COMMENT '扩展字段',
    `is_read`     TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `read_time`   DATETIME          DEFAULT NULL COMMENT '已读时间',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY           `idx_receiver_type` (`receiver_id`, `msg_type`, `is_read`),
    -- 私信会话是双向查询：(我发给他) OR (他发给我)。只有 idx_receiver_type 的话
    -- 「我发出去的」那一半无索引可走，会话越长越慢；补上发件方前缀让两个分支都能命中
    KEY           `idx_sender_receiver` (`sender_id`, `receiver_id`, `msg_type`),
    KEY           `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息主表';

-- 私信会话表
-- 约定：user_id_a 恒为两人中 id 较小的那个，user_id_b 为较大的。
-- uk_conversation 是有序唯一键，不归一化的话「1 找 2」和「2 找 1」会各建一行，
-- 同一段对话被劈成两半，两边各看各的未读数。归一化由 ConversationServiceImpl 负责。
CREATE TABLE `message_conversation`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id_a`      BIGINT UNSIGNED NOT NULL COMMENT '用户A（id 较小者）',
    `user_id_b`      BIGINT UNSIGNED NOT NULL COMMENT '用户B（id 较大者）',
    `last_msg_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '最后一条消息ID',
    `last_msg_time`  DATETIME          DEFAULT NULL COMMENT '最后消息时间',
    `unread_count_a` INT      NOT NULL DEFAULT 0 COMMENT 'A 的未读数',
    `unread_count_b` INT      NOT NULL DEFAULT 0 COMMENT 'B 的未读数',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation` (`user_id_a`, `user_id_b`),
    KEY              `idx_last_msg_time` (`last_msg_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- 推送设备绑定表
CREATE TABLE `message_push_device`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `device_type` VARCHAR(20)  NOT NULL COMMENT '设备类型：ios/android/harmony',
    `push_token`  VARCHAR(255) NOT NULL COMMENT '推送 Token',
    `vendor`      VARCHAR(20)           DEFAULT NULL COMMENT '推送厂商：apns/fcm/huawei/xiaomi',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-失效 1-有效',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_device` (`user_id`, `device_type`, `vendor`),
    KEY           `idx_push_token` (`push_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送设备绑定表';


-- ============================================================
-- 七、搜索（search-service）
--     搜索历史、词频统计、联想词字典（全文检索以 ES 为主，MySQL 存统计与词表）
-- ============================================================

-- 用户搜索历史表
CREATE TABLE `search_history`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `user_id`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户ID（0为游客）',
    `keyword`          VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    `search_count`     INT          NOT NULL DEFAULT 1 COMMENT '该用户搜索次数',
    `last_search_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后搜索时间',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_keyword` (`user_id`, `keyword`),
    KEY                `idx_last_search_time` (`last_search_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户搜索历史表';

-- 搜索词统计表
CREATE TABLE `search_keyword_stat`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '统计ID',
    `keyword`      VARCHAR(200) NOT NULL COMMENT '关键词',
    `search_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '总搜索次数',
    `result_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '平均结果数',
    `stat_date`    DATE         NOT NULL COMMENT '统计日期',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword_date` (`keyword`, `stat_date`),
    KEY            `idx_stat_date_count` (`stat_date`, `search_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索词统计表';

-- 搜索建议词表
CREATE TABLE `search_suggest`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    `keyword`     VARCHAR(200) NOT NULL COMMENT '建议关键词',
    `weight`      INT          NOT NULL DEFAULT 0 COMMENT '权重',
    `source`      TINYINT      NOT NULL DEFAULT 1 COMMENT '来源：1-人工 2-自动挖掘',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword` (`keyword`),
    KEY           `idx_status_weight` (`status`, `weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索建议词表';


-- ============================================================
-- 八、推荐（recommend-service）
--     推荐结果表、算法与流配置、用户特征（模型在外部训练，这里只存结果与配置）
-- ============================================================

-- 用户推荐结果表（每日/实时更新）
CREATE TABLE `recommend_result`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `video_id`    BIGINT UNSIGNED NOT NULL COMMENT '推荐视频ID',
    `scene`       VARCHAR(20)    NOT NULL COMMENT '推荐场景：home/follow/topic',
    `score`       DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '推荐得分',
    `algo_type`   VARCHAR(20)    NOT NULL COMMENT '算法类型：cf/deep/heatmap',
    `is_exposed`  TINYINT        NOT NULL DEFAULT 0 COMMENT '是否已曝光：0-未曝光 1-已曝光',
    `is_clicked`  TINYINT        NOT NULL DEFAULT 0 COMMENT '是否点击：0-未点击 1-已点击',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_video_scene` (`user_id`, `video_id`, `scene`),
    KEY           `idx_user_scene_score` (`user_id`, `scene`, `score`),
    KEY           `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户推荐结果表';

-- 推荐算法配置表
CREATE TABLE `recommend_algo_config`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `scene`        VARCHAR(20) NOT NULL COMMENT '推荐场景',
    `algo_type`    VARCHAR(20) NOT NULL COMMENT '算法类型',
    `config_key`   VARCHAR(50) NOT NULL COMMENT '配置Key',
    `config_value` TEXT COMMENT '配置值（JSON）',
    `description`  VARCHAR(500)         DEFAULT NULL COMMENT '说明',
    `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_algo_key` (`scene`, `algo_type`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐算法配置表';

-- 用户行为特征表（供召回模型使用）
CREATE TABLE `recommend_user_feature`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '特征ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `feature_type`  VARCHAR(20)   NOT NULL COMMENT '特征类型：tag/category/author',
    `feature_value` VARCHAR(100)  NOT NULL COMMENT '特征值',
    `weight`        DECIMAL(6, 4) NOT NULL DEFAULT 0 COMMENT '特征权重',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_feature` (`user_id`, `feature_type`, `feature_value`),
    KEY             `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为特征表';

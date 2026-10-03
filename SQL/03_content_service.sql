-- 视频播放平台 - 内容服务 SQL（content_service）
-- 用途：分类/标签、推荐流聚合、热搜榜单、内容安全审核、创作者中心看板

CREATE DATABASE IF NOT EXISTS `content_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `content_service`;

-- 视频分类表
CREATE TABLE IF NOT EXISTS `content_category` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名',
    `icon_url` VARCHAR(500) DEFAULT NULL COMMENT '分类图标',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频分类表';

-- 视频标签表
CREATE TABLE IF NOT EXISTS `content_tag` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名',
    `use_count` BIGINT NOT NULL DEFAULT 0 COMMENT '使用次数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_status_count` (`status`, `use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频标签表';

-- 推荐流配置表
CREATE TABLE IF NOT EXISTS `content_feed_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `feed_type` VARCHAR(20) NOT NULL COMMENT '流类型：recommend/hot/follow',
    `config_key` VARCHAR(50) NOT NULL COMMENT '配置Key',
    `config_value` TEXT COMMENT '配置值（JSON）',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feed_key` (`feed_type`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐流配置表';

-- 热搜榜单表
CREATE TABLE IF NOT EXISTS `content_hot_search` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '热搜ID',
    `keyword` VARCHAR(100) NOT NULL COMMENT '搜索关键词',
    `heat_score` INT NOT NULL DEFAULT 0 COMMENT '热度分',
    `rank` INT NOT NULL DEFAULT 0 COMMENT '榜单排名',
    `search_count` BIGINT NOT NULL DEFAULT 0 COMMENT '搜索次数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下线 1-上线',
    `rank_date` DATE NOT NULL COMMENT '榜单日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword_date` (`keyword`, `rank_date`),
    KEY `idx_rank_date` (`rank_date`, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热搜榜单表';

-- 内容安全审核表
CREATE TABLE IF NOT EXISTS `content_security_audit` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审核ID',
    `target_type` VARCHAR(20) NOT NULL COMMENT '对象类型：video/comment/danmaku',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '对象ID',
    `risk_level` TINYINT NOT NULL DEFAULT 0 COMMENT '风险等级：0-无 1-低 2-中 3-高',
    `risk_label` VARCHAR(100) DEFAULT NULL COMMENT '风险标签',
    `machine_result` JSON DEFAULT NULL COMMENT '机审结果',
    `manual_result` TINYINT DEFAULT NULL COMMENT '人工复核：0-未复核 1-放行 2-拦截',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_target` (`target_type`, `target_id`),
    KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容安全审核表';

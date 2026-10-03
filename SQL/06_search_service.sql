-- 视频播放平台 - 搜索服务 SQL（search_service）
-- 用途：视频/用户/话题全文检索、搜索聚合与排序（ES 为主，MySQL 存搜索词统计）

CREATE DATABASE IF NOT EXISTS `search_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `search_service`;

-- 用户搜索历史表
CREATE TABLE IF NOT EXISTS `search_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `user_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户ID（0为游客）',
    `keyword` VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    `search_count` INT NOT NULL DEFAULT 1 COMMENT '该用户搜索次数',
    `last_search_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后搜索时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_keyword` (`user_id`, `keyword`),
    KEY `idx_last_search_time` (`last_search_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户搜索历史表';

-- 搜索词统计表
CREATE TABLE IF NOT EXISTS `search_keyword_stat` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '统计ID',
    `keyword` VARCHAR(200) NOT NULL COMMENT '关键词',
    `search_count` BIGINT NOT NULL DEFAULT 0 COMMENT '总搜索次数',
    `result_count` BIGINT NOT NULL DEFAULT 0 COMMENT '平均结果数',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword_date` (`keyword`, `stat_date`),
    KEY `idx_stat_date_count` (`stat_date`, `search_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索词统计表';

-- 搜索建议词表
CREATE TABLE IF NOT EXISTS `search_suggest` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    `keyword` VARCHAR(200) NOT NULL COMMENT '建议关键词',
    `weight` INT NOT NULL DEFAULT 0 COMMENT '权重',
    `source` TINYINT NOT NULL DEFAULT 1 COMMENT '来源：1-人工 2-自动挖掘',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_keyword` (`keyword`),
    KEY `idx_status_weight` (`status`, `weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索建议词表';

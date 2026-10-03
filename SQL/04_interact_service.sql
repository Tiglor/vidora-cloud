-- 视频播放平台 - 互动服务 SQL（interact_service）
-- 用途：点赞/收藏/分享、评论回复、弹幕收发存储、播放/点赞实时计数

CREATE DATABASE IF NOT EXISTS `interact_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `interact_service`;

-- 点赞/收藏/分享 统一记录表（按类型区分）
CREATE TABLE IF NOT EXISTS `interact_action` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '互动ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `target_type` VARCHAR(20) NOT NULL COMMENT '对象类型：video/comment',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '对象ID',
    `action_type` TINYINT NOT NULL COMMENT '动作类型：1-点赞 2-收藏 3-分享',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-取消 1-有效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target_action` (`user_id`, `target_type`, `target_id`, `action_type`),
    KEY `idx_target_action` (`target_type`, `target_id`, `action_type`, `status`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞收藏分享记录表';

-- 评论表
CREATE TABLE IF NOT EXISTS `interact_comment` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `video_id` BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父评论ID（0为顶层评论）',
    `root_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '根评论ID（用于楼中楼聚合）',
    `content` VARCHAR(2000) NOT NULL COMMENT '评论内容',
    `like_count` BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常 2-审核中',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_video_id` (`video_id`, `status`, `create_time`),
    KEY `idx_root_id` (`root_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 弹幕表（水平分表候选：按 video_id 或日期拆分）
CREATE TABLE IF NOT EXISTS `interact_danmaku` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '弹幕ID',
    `video_id` BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发送用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '弹幕内容',
    `appear_time` DECIMAL(10, 3) NOT NULL COMMENT '弹幕出现时间（秒，保留3位小数）',
    `color` VARCHAR(10) DEFAULT '#FFFFFF' COMMENT '弹幕颜色',
    `font_size` TINYINT DEFAULT 25 COMMENT '字体大小',
    `position` TINYINT DEFAULT 0 COMMENT '位置：0-滚动 1-顶部 2-底部',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-屏蔽 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_video_time` (`video_id`, `appear_time`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='弹幕表';

-- 播放计数表（准实时，用于持久化 Redis 计数）
CREATE TABLE IF NOT EXISTS `interact_play_count` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `video_id` BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `play_count` BIGINT NOT NULL DEFAULT 0 COMMENT '播放数',
    `like_count` BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` BIGINT NOT NULL DEFAULT 0 COMMENT '评论数',
    `share_count` BIGINT NOT NULL DEFAULT 0 COMMENT '分享数',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_video_date` (`video_id`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放计数日表';

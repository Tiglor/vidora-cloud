-- 视频播放平台 - 推荐服务 SQL（recommend_service）
-- 用途：个性化推荐、协同过滤与召回排序（算法模型外部训练，MySQL 存储推荐结果与配置）

CREATE DATABASE IF NOT EXISTS `recommend_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `recommend_service`;

-- 用户推荐结果表（每日/实时更新）
CREATE TABLE IF NOT EXISTS `recommend_result` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `video_id` BIGINT UNSIGNED NOT NULL COMMENT '推荐视频ID',
    `scene` VARCHAR(20) NOT NULL COMMENT '推荐场景：home/follow/topic',
    `score` DECIMAL(10, 6) NOT NULL DEFAULT 0 COMMENT '推荐得分',
    `algo_type` VARCHAR(20) NOT NULL COMMENT '算法类型：cf/deep/heatmap',
    `is_exposed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已曝光：0-未曝光 1-已曝光',
    `is_clicked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否点击：0-未点击 1-已点击',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_video_scene` (`user_id`, `video_id`, `scene`),
    KEY `idx_user_scene_score` (`user_id`, `scene`, `score`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户推荐结果表';

-- 推荐算法配置表
CREATE TABLE IF NOT EXISTS `recommend_algo_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `scene` VARCHAR(20) NOT NULL COMMENT '推荐场景',
    `algo_type` VARCHAR(20) NOT NULL COMMENT '算法类型',
    `config_key` VARCHAR(50) NOT NULL COMMENT '配置Key',
    `config_value` TEXT COMMENT '配置值（JSON）',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_algo_key` (`scene`, `algo_type`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐算法配置表';

-- 用户行为特征表（供召回模型使用）
CREATE TABLE IF NOT EXISTS `recommend_user_feature` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '特征ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `feature_type` VARCHAR(20) NOT NULL COMMENT '特征类型：tag/category/author',
    `feature_value` VARCHAR(100) NOT NULL COMMENT '特征值',
    `weight` DECIMAL(6, 4) NOT NULL DEFAULT 0 COMMENT '特征权重',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_feature` (`user_id`, `feature_type`, `feature_value`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为特征表';

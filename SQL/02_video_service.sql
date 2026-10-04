-- 视频播放平台 - 视频服务 SQL（video_service）
-- 用途：视频元数据、分片上传、MD5秒传、转码调度、HLS切片、审核状态

CREATE DATABASE IF NOT EXISTS `video_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `video_service`;

-- 视频主表
CREATE TABLE IF NOT EXISTS `video_info` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '视频ID',
    `video_key` VARCHAR(64) NOT NULL COMMENT '业务唯一Key（UUID）',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '上传用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '视频标题',
    `description` VARCHAR(2000) DEFAULT NULL COMMENT '视频描述',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `source_url` VARCHAR(500) DEFAULT NULL COMMENT '源片URL',
    `duration` INT NOT NULL DEFAULT 0 COMMENT '视频时长（秒）',
    `width` INT DEFAULT 0 COMMENT '视频宽度',
    `height` INT DEFAULT 0 COMMENT '视频高度',
    `file_size` BIGINT DEFAULT 0 COMMENT '源片文件大小（字节）',
    `file_hash` VARCHAR(64) DEFAULT NULL COMMENT '源片MD5（秒传校验）',
    `storage_path` VARCHAR(500) DEFAULT NULL COMMENT '存储对象名：MinIO objectName 或本地相对路径',
    `hls_url` VARCHAR(500) DEFAULT NULL COMMENT '转码后 HLS 播放索引（m3u8）地址',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-上传中 1-转码中 2-审核中 3-已发布 4-已下架',
    `visibility` TINYINT NOT NULL DEFAULT 1 COMMENT '可见性：0-私密 1-公开 2-仅粉丝',
    `category_id` BIGINT DEFAULT 0 COMMENT '分类ID',
    `tags` JSON DEFAULT NULL COMMENT '标签数组',
    `play_count` BIGINT NOT NULL DEFAULT 0 COMMENT '播放数',
    `like_count` BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` BIGINT NOT NULL DEFAULT 0 COMMENT '评论数',
    `share_count` BIGINT NOT NULL DEFAULT 0 COMMENT '分享数',
    `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_video_key` (`video_key`),
    -- 不能对 file_hash 建唯一索引：秒传复用的是存储层 blob，不是视频记录。
    -- 同一文件被不同用户（或同一用户多次）投稿是合法的，各自要有独立的 video_info 行。
    KEY `idx_file_hash` (`file_hash`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_publish` (`status`, `publish_time`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频主表';

-- 视频转码任务表（单次转码任务 = 一次多清晰度 HLS 转码，包含全部清晰度档位）
-- 说明：一次 ffmpeg 调用产出 master.m3u8 + 各档位切片，故以“一视频一任务”建模，档位名存于 renditions(JSON)
CREATE TABLE IF NOT EXISTS `video_transcode_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `video_id` BIGINT DEFAULT NULL COMMENT '关联 video_info.id',
    `video_key` VARCHAR(64) DEFAULT NULL COMMENT '视频唯一 key',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-成功 3-失败',
    `progress` TINYINT DEFAULT 0 COMMENT '进度 0-100',
    `source_path` VARCHAR(512) DEFAULT NULL COMMENT '源片存储对象名',
    `hls_path` VARCHAR(512) DEFAULT NULL COMMENT '主播放列表 objectName（master.m3u8）',
    `renditions` VARCHAR(255) DEFAULT NULL COMMENT '已生成档位(JSON 数组，如 ["1080p","720p"])',
    `error_msg` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    `retry_count` TINYINT DEFAULT 0 COMMENT '已重试次数',
    `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频转码任务表';

-- 视频分片上传记录表（断点续传 + MD5 秒传）
CREATE TABLE IF NOT EXISTS `video_multipart_upload` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上传ID',
    `upload_id` VARCHAR(64) NOT NULL COMMENT '上传任务ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发起上传的用户ID',
    `video_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联视频ID',
    `file_name` VARCHAR(200) NOT NULL COMMENT '原始文件名',
    `file_hash` VARCHAR(64) NOT NULL COMMENT '文件MD5',
    `file_size` BIGINT UNSIGNED NOT NULL COMMENT '文件总字节数',
    `chunk_size` INT NOT NULL COMMENT '分片大小',
    `total_chunks` INT NOT NULL COMMENT '总分片数',
    `completed_chunks` INT NOT NULL DEFAULT 0 COMMENT '已完成分片数',
    `bucket` VARCHAR(100) NOT NULL COMMENT 'MinIO bucket',
    `object_key` VARCHAR(500) NOT NULL COMMENT 'MinIO对象Key',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-上传中 1-已完成 2-已合并',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_id` (`upload_id`),
    -- 一次上传尝试一行，不是「一个用户一个文件一行」：同一文件再投一次要能开新会话、建新视频。
    -- 断点续传只是从这个索引里挑最近一条未合并的会话继续传，跨用户越权由代码校验 user_id 拦截
    KEY `idx_user_hash` (`user_id`, `file_hash`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频分片上传记录表';

-- 视频审核记录表
CREATE TABLE IF NOT EXISTS `video_audit_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审核ID',
    `video_id` BIGINT UNSIGNED NOT NULL COMMENT '视频ID',
    `audit_type` TINYINT NOT NULL DEFAULT 1 COMMENT '审核类型：1-机器审核 2-人工审核',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核结果：0-待审核 1-通过 2-拒绝',
    `reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因',
    `auditor_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_video_id` (`video_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频审核记录表';

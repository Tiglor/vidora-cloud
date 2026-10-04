-- 视频播放平台 - 消息服务 SQL（message_service）
-- 用途：系统通知、互动消息、私信、WebSocket + 厂商 Push

CREATE DATABASE IF NOT EXISTS `message_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `message_service`;

-- 消息主表
CREATE TABLE IF NOT EXISTS `message_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `msg_type` TINYINT NOT NULL COMMENT '消息类型：1-系统通知 2-互动消息 3-私信',
    `sender_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发送者ID（0为系统）',
    `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者ID',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `extra` JSON DEFAULT NULL COMMENT '扩展字段',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `read_time` DATETIME DEFAULT NULL COMMENT '已读时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-删除 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_receiver_type` (`receiver_id`, `msg_type`, `is_read`),
    -- 私信会话是双向查询：(我发给他) OR (他发给我)。只有 idx_receiver_type 的话
    -- 「我发出去的」那一半无索引可走，会话越长越慢；补上发件方前缀让两个分支都能命中
    KEY `idx_sender_receiver` (`sender_id`, `receiver_id`, `msg_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息主表';

-- 私信会话表
-- 约定：user_id_a 恒为两人中 id 较小的那个，user_id_b 为较大的。
-- uk_conversation 是有序唯一键，不归一化的话「1 找 2」和「2 找 1」会各建一行，
-- 同一段对话被劈成两半，两边各看各的未读数。归一化由 ConversationServiceImpl 负责。
CREATE TABLE IF NOT EXISTS `message_conversation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id_a` BIGINT UNSIGNED NOT NULL COMMENT '用户A（id 较小者）',
    `user_id_b` BIGINT UNSIGNED NOT NULL COMMENT '用户B（id 较大者）',
    `last_msg_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '最后一条消息ID',
    `last_msg_time` DATETIME DEFAULT NULL COMMENT '最后消息时间',
    `unread_count_a` INT NOT NULL DEFAULT 0 COMMENT 'A 的未读数',
    `unread_count_b` INT NOT NULL DEFAULT 0 COMMENT 'B 的未读数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation` (`user_id_a`, `user_id_b`),
    KEY `idx_last_msg_time` (`last_msg_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- 推送设备绑定表
CREATE TABLE IF NOT EXISTS `message_push_device` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `device_type` VARCHAR(20) NOT NULL COMMENT '设备类型：ios/android/harmony',
    `push_token` VARCHAR(255) NOT NULL COMMENT '推送 Token',
    `vendor` VARCHAR(20) DEFAULT NULL COMMENT '推送厂商：apns/fcm/huawei/xiaomi',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失效 1-有效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_device` (`user_id`, `device_type`, `vendor`),
    KEY `idx_push_token` (`push_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送设备绑定表';

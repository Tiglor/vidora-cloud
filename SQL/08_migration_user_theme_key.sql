-- 迁移：sys_user 增加 theme_key（用户可选主题色）
--
-- 01_user_service.sql 的 CREATE TABLE 里已经含这一列，本脚本只服务于「库早就建好了」的环境。
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，所以重复执行会报 1060 Duplicate column name，属预期，忽略即可。
--
-- 带 DEFAULT 的 ALTER 会让 MySQL 直接给存量行填上 'light-blue'，不需要额外的 UPDATE 回填。
ALTER TABLE `sys_user`
    ADD COLUMN `theme_key` VARCHAR(32) NOT NULL DEFAULT 'light-blue' COMMENT '主题标识，对应前端主题包 key' AFTER `region`;

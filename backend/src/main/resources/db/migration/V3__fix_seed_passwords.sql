-- =============================================
-- V3__fix_seed_passwords.sql
-- 修正 V2 中种子用户的 BCrypt 哈希
-- 旧哈希由外部工具生成（$2b$ 前缀），与 Spring Security
-- BCryptPasswordEncoder（$2a$ 前缀）不匹配，导致无法登录。
-- =============================================

UPDATE sys_user
SET password = '$2a$10$Xz5OlUFTUW742nwO1Pg/QuN/QCTO2nMIBZ9IZUITLLMO3y20RP28C'
WHERE username = 'admin';

UPDATE sys_user
SET password = '$2a$10$vYygpgjJXomAiuE/lHeLX.hSdGV9b7.vCvi5dm4g0OaHa1F2StBWG'
WHERE username = 'viewer';

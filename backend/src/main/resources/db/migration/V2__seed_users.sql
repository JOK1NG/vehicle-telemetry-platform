-- =============================================
-- V2__seed_users.sql
-- 种子用户：ADMIN (可读写) / VIEWER (只读)
-- 密码使用 BCrypt 加密，明文分别为 admin123 / viewer123
-- =============================================

-- 仅在用户不存在时插入，避免重复
INSERT INTO sys_user (username, password, role, enabled)
VALUES ('admin', '$2a$10$Xz5OlUFTUW742nwO1Pg/QuN/QCTO2nMIBZ9IZUITLLMO3y20RP28C', 'ADMIN', true)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user (username, password, role, enabled)
VALUES ('viewer', '$2a$10$vYygpgjJXomAiuE/lHeLX.hSdGV9b7.vCvi5dm4g0OaHa1F2StBWG', 'VIEWER', true)
ON CONFLICT (username) DO NOTHING;

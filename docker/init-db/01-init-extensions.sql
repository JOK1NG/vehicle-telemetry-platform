-- =============================================
-- 01-init-extensions.sql
-- Docker 容器首次启动时启用扩展（Flyway 管理表结构）
-- =============================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS timescaledb;

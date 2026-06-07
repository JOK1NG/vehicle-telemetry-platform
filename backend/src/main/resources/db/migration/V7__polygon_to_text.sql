-- =============================================
-- V7__polygon_to_text.sql
-- 把 polygon 字段从 JSONB 改成 TEXT，简化 Java 端绑定
-- =============================================
ALTER TABLE geofence ALTER COLUMN polygon TYPE TEXT USING polygon::TEXT;

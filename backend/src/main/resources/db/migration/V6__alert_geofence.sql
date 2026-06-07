-- =============================================
-- V6__alert_geofence.sql
-- 告警规则、地理围栏、围栏-车辆关联（M2+）
-- =============================================

-- -----------------------------------------
-- 告警规则（Q4: 阈值可配置）
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS alert_rule (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32)  NOT NULL UNIQUE,   -- 'OVERSPEED' | 'LOW_BATTERY' | 'OFFLINE'
    name         VARCHAR(64)  NOT NULL,           -- 中文名：超速告警
    level        SMALLINT     NOT NULL DEFAULT 2, -- 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL
    metric       VARCHAR(32)  NOT NULL,           -- 'speed' | 'battery' | 'offline_minutes'
    comparator   VARCHAR(8)   NOT NULL,           -- 'GT' | 'LT'
    threshold    DOUBLE PRECISION NOT NULL,       -- 触发阈值
    enabled      BOOLEAN      NOT NULL DEFAULT true,
    description  VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 默认规则种子
INSERT INTO alert_rule (code, name, level, metric, comparator, threshold, enabled, description) VALUES
  ('OVERSPEED',    '超速告警',     3, 'speed',           'GT',  80.0,  true, '速度超过 80 km/h 触发'),
  ('LOW_BATTERY',  '低电量告警',   2, 'battery',         'LT',  20.0,  true, '电量低于 20% 触发'),
  ('OFFLINE',      '车辆离线告警', 2, 'offline_minutes', 'GT',  5.0,   true, '离线超过 5 分钟触发'),
  ('GEOFENCE_ENTER', '进入围栏',   1, 'geofence',        'EQ',  0.0,   true, '车辆进入地理围栏触发'),
  ('GEOFENCE_EXIT',  '离开围栏',   1, 'geofence',        'EQ',  0.0,   true, '车辆离开地理围栏触发')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------
-- 地理围栏
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS geofence (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    type        VARCHAR(16)  NOT NULL,        -- 'CIRCLE' | 'POLYGON'
    center_lng  DOUBLE PRECISION,             -- CIRCLE 中心经度
    center_lat  DOUBLE PRECISION,             -- CIRCLE 中心纬度
    radius_m    DOUBLE PRECISION,             -- CIRCLE 半径（米）
    polygon     JSONB,                        -- POLYGON 顶点数组 [{lng, lat}, ...]
    geom        GEOMETRY(Geometry, 4326),     -- PostGIS 几何（CIRCLE 缓冲为 POLYGON）
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_geofence_geom ON geofence USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_geofence_enabled ON geofence (enabled) WHERE enabled = true;

-- -----------------------------------------
-- 围栏-车辆关联（多对多）
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS geofence_vehicle (
    geofence_id  BIGINT NOT NULL REFERENCES geofence (id) ON DELETE CASCADE,
    vehicle_id   BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (geofence_id, vehicle_id)
);
CREATE INDEX IF NOT EXISTS idx_gfv_vehicle ON geofence_vehicle (vehicle_id);
CREATE INDEX IF NOT EXISTS idx_gfv_geofence ON geofence_vehicle (geofence_id);

-- -----------------------------------------
-- 告警表追加字段：rule_id、geofence_id 用于回溯
-- -----------------------------------------
ALTER TABLE alert ADD COLUMN IF NOT EXISTS rule_id     BIGINT;
ALTER TABLE alert ADD COLUMN IF NOT EXISTS geofence_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_alert_rule ON alert (rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_geofence ON alert (geofence_id);
CREATE INDEX IF NOT EXISTS idx_alert_handled_time ON alert (handled, occurred_at DESC);

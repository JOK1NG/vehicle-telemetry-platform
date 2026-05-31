-- =============================================
-- V1__init_schema.sql
-- MVP 核心表结构 (Flyway 管理)
-- =============================================

-- -----------------------------------------
-- 车辆台账
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS vehicle (
    id           BIGSERIAL PRIMARY KEY,
    plate_no     VARCHAR(32)  NOT NULL UNIQUE,
    vin          VARCHAR(32),
    model        VARCHAR(64),
    status       SMALLINT DEFAULT 0,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now()
);

-- -----------------------------------------
-- 用户表（RBAC）
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id        BIGSERIAL PRIMARY KEY,
    username  VARCHAR(64)  NOT NULL UNIQUE,
    password  VARCHAR(128) NOT NULL,
    role      VARCHAR(32)  NOT NULL DEFAULT 'VIEWER',
    enabled   BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- -----------------------------------------
-- 遥测时序表（Hypertable）
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS telemetry (
    time         TIMESTAMPTZ      NOT NULL,
    vehicle_id   BIGINT           NOT NULL,
    lng          DOUBLE PRECISION NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    speed        REAL,
    heading      REAL,
    battery      REAL,
    fault_code   VARCHAR(32),
    geom         GEOMETRY(Point, 4326)
);

SELECT create_hypertable('telemetry', 'time', if_not_exists => true);

CREATE INDEX IF NOT EXISTS idx_telemetry_vehicle_time ON telemetry (vehicle_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_geom ON telemetry USING GIST (geom);

-- -----------------------------------------
-- 告警表
-- -----------------------------------------
CREATE TABLE IF NOT EXISTS alert (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT NOT NULL,
    type        VARCHAR(32) NOT NULL,
    level       SMALLINT DEFAULT 1,
    message     VARCHAR(255),
    lng         DOUBLE PRECISION,
    lat         DOUBLE PRECISION,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    handled     BOOLEAN DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_alert_vehicle_time ON alert (vehicle_id, occurred_at DESC);

-- =============================================
-- V5__ai_infra.sql
-- Phase 0：AI 基础设施（调用日志 + 异步任务）
-- =============================================

-- AI 调用日志
CREATE TABLE IF NOT EXISTS ai_call_log (
    id              BIGSERIAL PRIMARY KEY,
    scene           VARCHAR(64)  NOT NULL,
    model           VARCHAR(64)  NOT NULL,
    provider        VARCHAR(64)  NOT NULL,
    request_summary TEXT,
    response_summary TEXT,
    success         BOOLEAN      NOT NULL DEFAULT false,
    latency_ms      INTEGER,
    token_usage     INTEGER,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- AI 异步任务
CREATE TABLE IF NOT EXISTS ai_task (
    id              BIGSERIAL PRIMARY KEY,
    scene           VARCHAR(64)  NOT NULL,
    business_id     VARCHAR(64),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    request_payload JSONB,
    result_payload  JSONB,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_call_log_scene ON ai_call_log (scene, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_task_status ON ai_task (status, created_at);

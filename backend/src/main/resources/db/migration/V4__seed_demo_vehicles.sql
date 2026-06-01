-- =============================================
-- V4__seed_demo_vehicles.sql
-- 模拟器联调用种子车辆。
-- ID 显式写入 1..5，便于 SIMULATOR_VEHICLE_IDS 默认值直接对齐。
-- 同步把 vehicle.id 自增序列顶到 5 之后，避免后续 API 新增的车辆与之冲突。
-- =============================================

INSERT INTO vehicle (id, plate_no, vin, model, status, created_at, updated_at)
VALUES
    (1, '沪A00001', 'LSVNV2180E2000001', 'Demo-Sedan',     1, now(), now()),
    (2, '沪A00002', 'LSVNV2180E2000002', 'Demo-Sedan',     1, now(), now()),
    (3, '沪A00003', 'LSVNV2180E2000003', 'Demo-SUV',       1, now(), now()),
    (4, '沪A00004', 'LSVNV2180E2000004', 'Demo-SUV',       1, now(), now()),
    (5, '沪A00005', 'LSVNV2180E2000005', 'Demo-ElectricTruck', 1, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 把 BIGSERIAL 序列顶到当前最大 id 之后
SELECT setval(
    pg_get_serial_sequence('vehicle', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM vehicle), 1)
);

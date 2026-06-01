package com.iov.platform.simulator.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 单车运行态：当前位置、航向、速度、电量。
 *
 * 运动模型（MVP 简化版）：
 *   - 固定 dt 内沿当前 heading 方向推进 distance = speed * dt
 *   - 经纬度增量按本地近似投影（短距离内 1° ≈ 111km）换算
 *   - 越界后随机调转航向，避免飞出 base 框
 *   - 速度在 [minSpeed, maxSpeed] 间随机波动
 *   - 电量匀速下降，跌破 5% 时认为耗尽
 */
public class VehicleSimState {

    /** 1° lat ≈ 111km；1° lng 在 31° lat 附近 ≈ 111 * cos(31°) ≈ 95km。 */
    private static final double KM_PER_DEG_LAT = 111.0;
    private static final double KM_PER_DEG_LNG_AT_31 = 95.0;

    private final long vehicleId;
    private double lng;
    private double lat;
    private double speed;     // km/h
    private double heading;   // 0-360
    private double battery;   // 0-100
    private double baseCenterLng;
    private double baseCenterLat;
    private double spreadLng;
    private double spreadLat;
    private double minSpeed;
    private double maxSpeed;

    public VehicleSimState(long vehicleId, double initLng, double initLat,
                           double baseCenterLng, double baseCenterLat,
                           double spreadLng, double spreadLat,
                           double minSpeed, double maxSpeed) {
        this.vehicleId = vehicleId;
        this.lng = initLng;
        this.lat = initLat;
        this.baseCenterLng = baseCenterLng;
        this.baseCenterLat = baseCenterLat;
        this.spreadLng = spreadLng;
        this.spreadLat = spreadLat;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.speed = randomBetween(minSpeed, maxSpeed);
        this.heading = ThreadLocalRandom.current().nextDouble(0.0, 360.0);
        this.battery = ThreadLocalRandom.current().nextDouble(60.0, 100.0);
    }

    /**
     * 沿当前 heading 推进一步。
     *
     * @param dtMs 距上次推进的毫秒数
     */
    public void step(long dtMs) {
        if (battery <= 5.0) {
            // 电量耗尽：原地不动
            return;
        }

        double dtHours = dtMs / 3_600_000.0;
        double distanceKm = speed * dtHours;

        // 按 heading 拆解经纬度增量
        double rad = Math.toRadians(heading);
        double deltaLng = Math.sin(rad) * distanceKm / KM_PER_DEG_LNG_AT_31;
        double deltaLat = Math.cos(rad) * distanceKm / KM_PER_DEG_LAT;

        double newLng = lng + deltaLng;
        double newLat = lat + deltaLat;

        // 越界：随机调转航向，保留原位
        if (outOfBounds(newLng, newLat)) {
            heading = (heading + 180.0 + ThreadLocalRandom.current().nextDouble(-45.0, 45.0)) % 360.0;
            if (heading < 0) heading += 360.0;
            return;
        }

        lng = newLng;
        lat = newLat;

        // 速度轻微波动（±10%）
        double jitter = ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
        double newSpeed = speed * (1.0 + jitter);
        if (newSpeed < minSpeed) newSpeed = minSpeed;
        if (newSpeed > maxSpeed) newSpeed = maxSpeed;
        speed = newSpeed;

        // 航向缓慢漂移
        double drift = ThreadLocalRandom.current().nextDouble(-3.0, 3.0);
        heading = (heading + drift + 360.0) % 360.0;

        // 电量缓慢下降（每推进一步约掉 0.001%）
        battery = Math.max(0.0, battery - 0.001);
    }

    public double getLng() { return lng; }
    public double getLat() { return lat; }
    public double getSpeed() { return speed; }
    public double getHeading() { return heading; }
    public double getBattery() { return battery; }
    public long getVehicleId() { return vehicleId; }

    private boolean outOfBounds(double lng, double lat) {
        return lng < baseCenterLng - spreadLng
                || lng > baseCenterLng + spreadLng
                || lat < baseCenterLat - spreadLat
                || lat > baseCenterLat + spreadLat;
    }

    private static double randomBetween(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}

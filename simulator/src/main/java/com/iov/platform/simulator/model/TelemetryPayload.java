package com.iov.platform.simulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MQTT 遥测消息 JSON payload.
 * 字段顺序与命名严格遵循 docs/mvp-00-implementation-contract.md §2.2.
 */
public class TelemetryPayload {

    /** ISO 8601 UTC 时间戳，毫秒精度。 */
    private String ts;

    /** GCJ-02 经度。 */
    private double lng;

    /** GCJ-02 纬度。 */
    private double lat;

    /** 速度 km/h。 */
    private double speed;

    /** 航向角 0-360。 */
    private double heading;

    /** 电量 0-100。 */
    private double battery;

    /** 故障码，无故障时 null。 */
    @JsonProperty("faultCode")
    private String faultCode;

    public TelemetryPayload() {}

    public TelemetryPayload(String ts, double lng, double lat, double speed,
                            double heading, double battery, String faultCode) {
        this.ts = ts;
        this.lng = lng;
        this.lat = lat;
        this.speed = speed;
        this.heading = heading;
        this.battery = battery;
        this.faultCode = faultCode;
    }

    public String getTs() { return ts; }
    public void setTs(String ts) { this.ts = ts; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }

    public double getBattery() { return battery; }
    public void setBattery(double battery) { this.battery = battery; }

    public String getFaultCode() { return faultCode; }
    public void setFaultCode(String faultCode) { this.faultCode = faultCode; }
}

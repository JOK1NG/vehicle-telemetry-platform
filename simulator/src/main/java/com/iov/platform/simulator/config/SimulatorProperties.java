package com.iov.platform.simulator.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

/**
 * 模拟器配置项。环境变量优先 (SPRING_APPLICATION_JSON 或 SIMULATOR_*)。
 *
 * 示例：
 *   SIMULATOR_MQTT_URL=tcp://localhost:1883
 *   SIMULATOR_PUBLISH_INTERVAL_MS=1000
 *   SIMULATOR_VEHICLE_IDS=1,2,3,4,5
 *   SIMULATOR_BASE_CENTER_LNG=121.473701
 */
@ConfigurationProperties(prefix = "simulator")
@Validated
public class SimulatorProperties {

    @NotNull
    private Mqtt mqtt = new Mqtt();

    /** 发布周期（毫秒）。1000ms = 1Hz。 */
    @Positive
    @Min(100)
    private long publishIntervalMs = 1000L;

    /** 显式车辆 ID 列表。设置后覆盖 vehicleCount。 */
    private List<Long> vehicleIds = Collections.emptyList();

    /** 车辆数量（与 vehicleIds 二选一）。当 vehicleIds 为空时生效，生成 1..count。 */
    @Positive
    private int vehicleCount = 5;

    /** GCJ-02 基础经度中心。 */
    @Positive
    private double baseCenterLng = 121.473701;

    /** GCJ-02 基础纬度中心。 */
    @Positive
    private double baseCenterLat = 31.230416;

    /** 经度方向扩散范围（度，约 0.05 ≈ 5km）。 */
    @Positive
    private double spreadLng = 0.05;

    /** 纬度方向扩散范围（度，约 0.045 ≈ 5km）。 */
    @Positive
    private double spreadLat = 0.045;

    /** 速度下限 km/h。 */
    private double minSpeed = 20.0;

    /** 速度上限 km/h。 */
    private double maxSpeed = 60.0;

    /** 故障码注入概率（0..1）。0 表示不注入。 */
    private double faultProbability = 0.0;

    /** 启动后是否自动开始发布。false 表示只连接 MQTT，等外部触发。 */
    private boolean autoStart = true;

    public Mqtt getMqtt() { return mqtt; }
    public void setMqtt(Mqtt mqtt) { this.mqtt = mqtt; }

    public long getPublishIntervalMs() { return publishIntervalMs; }
    public void setPublishIntervalMs(long publishIntervalMs) { this.publishIntervalMs = publishIntervalMs; }

    public List<Long> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(List<Long> vehicleIds) { this.vehicleIds = vehicleIds; }

    public int getVehicleCount() { return vehicleCount; }
    public void setVehicleCount(int vehicleCount) { this.vehicleCount = vehicleCount; }

    public double getBaseCenterLng() { return baseCenterLng; }
    public void setBaseCenterLng(double baseCenterLng) { this.baseCenterLng = baseCenterLng; }

    public double getBaseCenterLat() { return baseCenterLat; }
    public void setBaseCenterLat(double baseCenterLat) { this.baseCenterLat = baseCenterLat; }

    public double getSpreadLng() { return spreadLng; }
    public void setSpreadLng(double spreadLng) { this.spreadLng = spreadLng; }

    public double getSpreadLat() { return spreadLat; }
    public void setSpreadLat(double spreadLat) { this.spreadLat = spreadLat; }

    public double getMinSpeed() { return minSpeed; }
    public void setMinSpeed(double minSpeed) { this.minSpeed = minSpeed; }

    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }

    public double getFaultProbability() { return faultProbability; }
    public void setFaultProbability(double faultProbability) { this.faultProbability = faultProbability; }

    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }

    public static class Mqtt {
        @NotBlank
        private String url = "tcp://localhost:1883";

        /** 留空表示匿名连接。 */
        private String username = "";

        private String password = "";

        /** Paho clientId ≤ 23 字符。 */
        @NotBlank
        private String clientId = "iov-simulator";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
    }
}

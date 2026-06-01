package com.iov.platform.simulator;

import com.iov.platform.simulator.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 车辆遥测模拟器入口 (M2).
 *
 * 独立 Spring Boot 进程，按配置生成多车 telemetry payload 并发布到
 * EMQX MQTT topic vehicle/{vehicleId}/telemetry，载荷格式严格遵循
 * docs/mvp-00-implementation-contract.md §2.2。
 *
 * 启动方式：
 *   mvn spring-boot:run
 *   或打成可执行 jar：mvn package && java -jar target/vehicle-telemetry-simulator-*.jar
 *
 * 启停：Ctrl+C 优雅关闭；publish-interval-ms / vehicle-ids / base 范围
 * 通过 application.yml 或环境变量覆盖。
 */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}

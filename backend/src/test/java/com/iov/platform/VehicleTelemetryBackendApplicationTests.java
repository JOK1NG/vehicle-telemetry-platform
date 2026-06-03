package com.iov.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=0",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
@ActiveProfiles("test")
@SuppressWarnings({"null", "unchecked"})
class VehicleTelemetryBackendApplicationTests {

    @MockitoBean
    private VehicleMapper vehicleMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void contextLoads() {
    }
}

package com.iov.platform.modules.realtime.controller;

import com.iov.platform.common.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"null", "unchecked"})
class TrajectoryControllerTest {

    @Test
    void trajectory_whenRowsExceedLimit_keepsOneRowPerBucket() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("vehicle:meta:1")).thenReturn("沪A00001,1");
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT COUNT(*) FROM telemetry"),
                eq(Integer.class),
                eq(1L),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(5001);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("time", OffsetDateTime.parse("2026-06-08T00:00:00+00:00"));
        row.put("lng", 121.0);
        row.put("lat", 31.0);
        row.put("speed", 42.0);
        row.put("heading", 90.0);
        row.put("battery", 80.0);
        when(jdbcTemplate.queryForList(
                anyString(),
                eq(2000),
                eq(1L),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(row));

        TrajectoryController controller = new TrajectoryController(jdbcTemplate, redis);

        Result<List<Map<String, Object>>> result = controller.trajectory(
                1L,
                "2026-06-01T00:00:00Z",
                "2026-06-01T01:00:00Z",
                2000
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(2000),
                eq(1L),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("ntile(?) OVER"));
        assertTrue(sql.contains("row_number() OVER (PARTITION BY bucket"));
        assertTrue(sql.contains("WHERE rn = 1"));
        assertEquals(0, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getData().get(0).get("vehicleId"));
    }
}

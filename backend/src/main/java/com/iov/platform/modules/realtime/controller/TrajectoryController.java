package com.iov.platform.modules.realtime.controller;

import com.iov.platform.common.Result;
import com.iov.platform.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 历史轨迹查询（M3+）
 * GET /api/vehicles/{id}/trajectory?start=&end=&maxPoints=
 * - 默认返回 7 天窗口，最多 maxPoints（默认 2000）个采样点
 * - 数据量大时按时间均匀抽稀（Time-based downsampling）
 */
@RestController
@RequiredArgsConstructor
public class TrajectoryController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;

    @GetMapping("/api/vehicles/{id}/trajectory")
    public Result<List<Map<String, Object>>> trajectory(
            @PathVariable Long id,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "2000") int maxPoints) {

        // 校验车辆存在
        String metaKey = "vehicle:meta:" + id;
        String meta = null;
        try { meta = redis.opsForValue().get(metaKey); } catch (Exception ignore) {}
        if (meta == null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle WHERE id = ?", Integer.class, id);
            if (count == null || count == 0) {
                throw new ResourceNotFoundException("车辆不存在");
            }
        }

        OffsetDateTime startTs = parseTs(start);
        OffsetDateTime endTs = parseTs(end);
        if (startTs == null || endTs == null) {
            return Result.fail(400, "时间格式应为 ISO 8601");
        }
        if (!endTs.isAfter(startTs)) {
            return Result.fail(400, "结束时间必须晚于开始时间");
        }
        // 上限：7 天
        if (endTs.toEpochSecond() - startTs.toEpochSecond() > 7L * 24 * 3600) {
            return Result.fail(400, "单次查询时间窗口不能超过 7 天");
        }
        int limit = Math.min(Math.max(maxPoints, 100), 5000);

        // 先查总点数
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telemetry WHERE vehicle_id = ? AND time BETWEEN ?::timestamptz AND ?::timestamptz",
                Integer.class, id, startTs, endTs);
        if (total == null || total == 0) {
            return Result.ok(List.of());
        }

        List<Map<String, Object>> rows;
        if (total <= limit) {
            rows = jdbcTemplate.queryForList(
                    "SELECT time, lng, lat, speed, heading, battery FROM telemetry " +
                            "WHERE vehicle_id = ? AND time BETWEEN ?::timestamptz AND ?::timestamptz " +
                            "ORDER BY time ASC",
                    id, startTs, endTs);
        } else {
            // Time-based 抽稀：用 row_number() 按时间均匀取 limit 个点
            rows = jdbcTemplate.queryForList(
                    "SELECT * FROM ( " +
                            "  SELECT time, lng, lat, speed, heading, battery, " +
                            "         ntile(?) OVER (ORDER BY time ASC) AS bucket " +
                            "  FROM telemetry " +
                            "  WHERE vehicle_id = ? AND time BETWEEN ?::timestamptz AND ?::timestamptz " +
                            ") t WHERE bucket <= ? ORDER BY time ASC",
                    limit, id, startTs, endTs, limit);
            // ntile 会给每行打 1..limit 的 bucket 编号；这里保留 bucket=1..limit 共 limit 行
        }

        // 格式化：time 序列化为 ISO 字符串
        DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Object t = r.get("time");
            if (t instanceof OffsetDateTime odt) {
                r.put("time", odt.format(fmt));
            }
            r.put("vehicleId", id);
            out.add(r);
        }
        return Result.ok(out);
    }

    private OffsetDateTime parseTs(String s) {
        if (s == null) return null;
        try {
            // 兼容 "2026-06-01T00:00:00Z" 和 "2026-06-01T00:00:00+08:00"
            if (s.endsWith("Z")) s = s.substring(0, s.length() - 1) + "+00:00";
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}

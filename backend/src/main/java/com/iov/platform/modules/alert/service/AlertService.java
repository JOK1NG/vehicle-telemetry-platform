package com.iov.platform.modules.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iov.platform.modules.alert.dto.AlertItem;
import com.iov.platform.modules.alert.entity.Alert;
import com.iov.platform.modules.alert.entity.AlertRule;
import com.iov.platform.modules.alert.mapper.AlertMapper;
import com.iov.platform.modules.alert.mapper.AlertRuleMapper;
import com.iov.platform.modules.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService extends ServiceImpl<AlertMapper, Alert> {

    private final AlertMapper alertMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final VehicleService vehicleService;

    /** 规则缓存：code -> rule（启动时 + 修改时刷新） */
    private volatile Map<String, AlertRule> ruleCache = new ConcurrentHashMap<>();

    /**
     * 启动时初始化规则缓存
     */
    public void reloadRuleCache() {
        Map<String, AlertRule> next = new HashMap<>();
        for (AlertRule r : alertRuleMapper.findAllEnabled()) {
            next.put(r.getCode(), r);
        }
        this.ruleCache = new ConcurrentHashMap<>(next);
        log.info("告警规则缓存已刷新，共 {} 条", next.size());
    }

    public AlertRule getRule(String code) {
        return ruleCache.get(code);
    }

    public List<AlertRule> listRules() {
        return alertRuleMapper.selectList(null);
    }

    public AlertRule updateRule(Long id, AlertRule patch) {
        AlertRule r = alertRuleMapper.selectById(id);
        if (r == null) return null;
        if (patch.getName() != null) r.setName(patch.getName());
        if (patch.getLevel() != null) r.setLevel(patch.getLevel());
        if (patch.getThreshold() != null) r.setThreshold(patch.getThreshold());
        if (patch.getEnabled() != null) r.setEnabled(patch.getEnabled());
        if (patch.getDescription() != null) r.setDescription(patch.getDescription());
        r.setUpdatedAt(OffsetDateTime.now());
        alertRuleMapper.updateById(r);
        reloadRuleCache(); // 全量刷新避免单条更新与并发全量刷新的竞态
        return r;
    }

    /**
     * 创建一条告警 + 写库 + 推 /topic/alerts
     * 同 vehicle+type 在 60s 内的重复告警会被去重
     */
    @Transactional
    public Alert fireAlert(Long vehicleId, String type, String message,
                           Double lng, Double lat, Long ruleId, Long geofenceId) {
        // 去重：60s 内同 vehicle+type 不重复推送
        OffsetDateTime since = OffsetDateTime.now().minusSeconds(60);
        long dup = alertMapper.countRecentUnhandle(vehicleId, type, since);
        if (dup > 0) {
            log.debug("告警去重: vehicleId={} type={} 60s 内已存在未处理告警", vehicleId, type);
            return null;
        }

        AlertRule rule = ruleCache.get(type);
        Integer level = rule != null && rule.getLevel() != null ? rule.getLevel() : 2;
        Long resolvedRuleId = ruleId != null ? ruleId : (rule != null ? rule.getId() : null);

        Alert alert = new Alert();
        alert.setVehicleId(vehicleId);
        alert.setType(type);
        alert.setLevel(level);
        alert.setMessage(message);
        alert.setLng(lng);
        alert.setLat(lat);
        alert.setOccurredAt(OffsetDateTime.now());
        alert.setHandled(false);
        alert.setRuleId(resolvedRuleId);
        alert.setGeofenceId(geofenceId);
        alertMapper.insert(alert);

        // 取 plateNo
        String plateNo = "";
        try {
            String meta = vehicleService.getVehicleMetaCache(vehicleId);
            if (meta != null) {
                String[] parts = meta.split(",", 2); // 格式: plateNo,status — limit=2 保证只分两段
                if (parts.length >= 1) plateNo = parts[0];
            }
        } catch (Exception ignore) {}
        if (plateNo.isEmpty()) {
            // fallback: 从 DB 查
            try {
                com.iov.platform.modules.vehicle.entity.Vehicle v = vehicleService.getVehicle(vehicleId);
                if (v != null) plateNo = v.getPlateNo();
            } catch (Exception ignore) {}
        }

        AlertItem item = AlertItem.from(alert, plateNo);
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", "ALERT");
            envelope.put("timestamp", OffsetDateTime.now().toString());
            envelope.put("alert", item);
            messagingTemplate.convertAndSend("/topic/alerts", envelope);
        } catch (Exception e) {
            log.error("WebSocket 告警广播失败: {}", e.getMessage());
        }

        log.info("新告警: id={} type={} vehicleId={} level={}", alert.getId(), type, vehicleId, level);
        return alert;
    }

    // ---- 历史告警查询 ----

    public Page<AlertItem> listAlerts(long current, long size, Integer level, String type, Boolean handled) {
        long pageSize = Math.min(Math.max(size, 1), 100);
        long pageNum = Math.max(current, 1);
        Page<Alert> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Alert> q = new LambdaQueryWrapper<>();
        if (level != null) q.eq(Alert::getLevel, level);
        if (type != null && !type.isBlank()) q.eq(Alert::getType, type);
        if (handled != null) q.eq(Alert::getHandled, handled);
        q.orderByDesc(Alert::getOccurredAt);

        Page<Alert> alertPage = alertMapper.selectPage(page, q);

        // 批量查 plateNo
        List<Alert> records = alertPage.getRecords();
        final Map<Long, String> plateMap = new HashMap<>();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Alert a : records) if (a.getVehicleId() != null) ids.add(a.getVehicleId());
        if (!ids.isEmpty()) {
            try {
                plateMap.putAll(vehicleService.getVehicleMetaCacheBatch(new java.util.ArrayList<>(ids)));
            } catch (Exception ignore) {}
        }

        List<AlertItem> items = records.stream()
                .map(a -> AlertItem.from(a, plateMap.getOrDefault(a.getVehicleId(), "")))
                .toList();

        Page<AlertItem> result = new Page<>(pageNum, pageSize, alertPage.getTotal());
        result.setRecords(items);
        return result;
    }

    public List<AlertItem> latest(int limit) {
        long safe = Math.min(Math.max(limit, 1), 100);
        List<Alert> records = alertMapper.findLatest((int) safe);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Alert a : records) if (a.getVehicleId() != null) ids.add(a.getVehicleId());
        final Map<Long, String> plateMap = new HashMap<>();
        if (!ids.isEmpty()) {
            try {
                plateMap.putAll(vehicleService.getVehicleMetaCacheBatch(new java.util.ArrayList<>(ids)));
            } catch (Exception ignore) {}
        }
        return records.stream()
                .map(a -> AlertItem.from(a, plateMap.getOrDefault(a.getVehicleId(), "")))
                .toList();
    }

    @Transactional
    public boolean markHandled(Long id) {
        Alert a = alertMapper.selectById(id);
        if (a == null) return false;
        int n = alertMapper.markHandled(id);
        return n > 0;
    }
}

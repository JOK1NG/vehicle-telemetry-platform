package com.iov.platform.modules.alert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iov.platform.common.Result;
import com.iov.platform.modules.alert.dto.AlertItem;
import com.iov.platform.modules.alert.entity.AlertRule;
import com.iov.platform.modules.alert.service.AlertRuleService;
import com.iov.platform.modules.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertRuleService ruleService;

    @GetMapping
    public Result<Page<AlertItem>> list(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean handled) {
        return Result.ok(alertService.listAlerts(current, size, level, type, handled));
    }

    @GetMapping("/latest")
    public Result<List<AlertItem>> latest(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(alertService.latest(limit));
    }

    @PatchMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        boolean ok = alertService.markHandled(id);
        return ok ? Result.ok() : Result.fail(404, "告警不存在");
    }

    // ---- 规则管理 ----

    @GetMapping("/rules")
    public Result<List<AlertRule>> listRules() {
        return Result.ok(ruleService.list());
    }

    @PutMapping("/rules/{id}")
    public Result<AlertRule> updateRule(@PathVariable Long id, @RequestBody AlertRule patch) {
        AlertRule r = ruleService.getById(id);
        if (r == null) return Result.fail(404, "规则不存在");
        if (patch.getName() != null) r.setName(patch.getName());
        if (patch.getLevel() != null) r.setLevel(patch.getLevel());
        if (patch.getThreshold() != null) r.setThreshold(patch.getThreshold());
        if (patch.getEnabled() != null) r.setEnabled(patch.getEnabled());
        if (patch.getDescription() != null) r.setDescription(patch.getDescription());
        ruleService.updateById(r);
        // 刷新告警服务缓存
        alertService.reloadRuleCache();
        return Result.ok(r);
    }
}

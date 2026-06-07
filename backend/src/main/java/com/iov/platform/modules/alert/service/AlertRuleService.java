package com.iov.platform.modules.alert.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iov.platform.modules.alert.entity.AlertRule;
import com.iov.platform.modules.alert.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertRuleService extends ServiceImpl<AlertRuleMapper, AlertRule> {

    public AlertRule getByCode(String code) {
        return lambdaQuery().eq(AlertRule::getCode, code).one();
    }
}

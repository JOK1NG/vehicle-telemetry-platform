package com.iov.platform.modules.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iov.platform.modules.alert.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * 加载所有 enabled 规则（启动时缓存）
     */
    default List<AlertRule> findAllEnabled() {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AlertRule>()
                .eq("enabled", true));
    }
}

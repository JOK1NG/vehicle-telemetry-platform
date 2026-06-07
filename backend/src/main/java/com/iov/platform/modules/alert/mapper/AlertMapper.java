package com.iov.platform.modules.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iov.platform.modules.alert.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    /**
     * 最近 N 条告警（按时间倒序）
     */
    @Select("SELECT * FROM alert ORDER BY occurred_at DESC LIMIT #{limit}")
    List<Alert> findLatest(@Param("limit") int limit);

    /**
     * 同车辆+同类型在指定时间窗内是否已存在未处理告警（去重防刷屏）
     */
    @Select("SELECT COUNT(*) FROM alert WHERE vehicle_id = #{vehicleId} AND type = #{type} " +
            "AND occurred_at > #{since} AND handled = false")
    long countRecentUnhandle(@Param("vehicleId") Long vehicleId,
                             @Param("type") String type,
                             @Param("since") OffsetDateTime since);

    /**
     * 标记已处理
     */
    @Update("UPDATE alert SET handled = true WHERE id = #{id}")
    int markHandled(@Param("id") Long id);
}

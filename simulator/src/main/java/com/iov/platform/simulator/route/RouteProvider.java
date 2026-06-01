package com.iov.platform.simulator.route;

import com.iov.platform.simulator.config.SimulatorProperties;
import com.iov.platform.simulator.model.VehicleSimState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 为一组车辆 ID 生成初始 VehicleSimState。
 *
 * vehicleIds 显式配置时优先使用；否则按 vehicleCount 生成 1..count。
 * 每辆车的初始位置在 base 范围内随机散布，互不重叠避免起点完全重合。
 */
@Component
public class RouteProvider {

    public List<VehicleSimState> create(SimulatorProperties props) {
        List<Long> ids = resolveVehicleIds(props);
        double cx = props.getBaseCenterLng();
        double cy = props.getBaseCenterLat();
        double sx = props.getSpreadLng();
        double sy = props.getSpreadLat();

        List<VehicleSimState> states = new ArrayList<>(ids.size());
        for (Long id : ids) {
            // 初始点：在 base 框内随机散布
            double initLng = cx + ThreadLocalRandom.current().nextDouble(-sx, sx);
            double initLat = cy + ThreadLocalRandom.current().nextDouble(-sy, sy);
            states.add(new VehicleSimState(
                    id, initLng, initLat,
                    cx, cy, sx, sy,
                    props.getMinSpeed(), props.getMaxSpeed()));
        }
        return states;
    }

    private List<Long> resolveVehicleIds(SimulatorProperties props) {
        if (props.getVehicleIds() != null && !props.getVehicleIds().isEmpty()) {
            return props.getVehicleIds();
        }
        int count = Math.max(1, props.getVehicleCount());
        List<Long> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ids.add((long) i);
        }
        return ids;
    }
}

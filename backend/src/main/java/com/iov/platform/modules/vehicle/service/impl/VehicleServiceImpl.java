package com.iov.platform.modules.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iov.platform.common.ResourceNotFoundException;
import com.iov.platform.modules.vehicle.dto.VehicleCreateRequest;
import com.iov.platform.modules.vehicle.dto.VehicleUpdateRequest;
import com.iov.platform.modules.vehicle.entity.Vehicle;
import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import com.iov.platform.modules.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl extends ServiceImpl<VehicleMapper, Vehicle> implements VehicleService {

    private final StringRedisTemplate redis;

    @Override
    public Page<Vehicle> listVehicles(long current, long size) {
        long pageSize = Math.min(Math.max(size, 1), 100);
        long pageNum = Math.max(current, 1);
        Page<Vehicle> page = new Page<>(pageNum, pageSize);
        return page(page, new LambdaQueryWrapper<Vehicle>().orderByDesc(Vehicle::getCreatedAt));
    }

    @Override
    public Vehicle getVehicle(Long id) {
        if (id == null) {
            return null;
        }
        return getById(id);
    }

    @Override
    @Transactional
    public Vehicle createVehicle(VehicleCreateRequest request) {
        String plateNo = request.getPlateNo().trim();

        long count = count(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, plateNo));
        if (count > 0) {
            throw new IllegalArgumentException("车牌号已存在");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo(plateNo);
        vehicle.setVin(request.getVin());
        vehicle.setModel(request.getModel());
        vehicle.setStatus(0);
        vehicle.setCreatedAt(OffsetDateTime.now());
        vehicle.setUpdatedAt(OffsetDateTime.now());

        save(vehicle);

        // 新增车辆，写入基础数据 Redis 缓存
        try {
            String key = "vehicle:meta:" + vehicle.getId();
            String val = vehicle.getPlateNo() + ",0";
            redis.opsForValue().set(key, val, 24L, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("写入车辆基础缓存失败 vehicleId={}", vehicle.getId(), e);
        }

        return vehicle;
    }

    @Override
    @Transactional
    public Vehicle updateVehicle(Long id, VehicleUpdateRequest request) {
        Vehicle exist = getById(id);
        if (exist == null) {
            throw new ResourceNotFoundException("车辆不存在");
        }

        String plateNo = request.getPlateNo().trim();
        if (!plateNo.equals(exist.getPlateNo())) {
            long count = count(new LambdaQueryWrapper<Vehicle>()
                    .eq(Vehicle::getPlateNo, plateNo)
                    .ne(Vehicle::getId, id));
            if (count > 0) {
                throw new IllegalArgumentException("车牌号已存在");
            }
            exist.setPlateNo(plateNo);
        }

        if (request.getVin() != null) {
            exist.setVin(request.getVin());
        }
        if (request.getModel() != null) {
            exist.setModel(request.getModel());
        }
        exist.setUpdatedAt(OffsetDateTime.now());

        updateById(exist);

        // 更新车辆，清理 Redis 中的基础元数据缓存（Cache-Aside 经典失效策略）
        try {
            String key = "vehicle:meta:" + id;
            redis.delete(key);
        } catch (Exception e) {
            log.error("清理车辆基础缓存失败 vehicleId={}", id, e);
        }

        return exist;
    }

    @Override
    @Transactional
    public boolean deleteVehicle(Long id) {
        Vehicle exist = getById(id);
        if (exist == null) {
            throw new ResourceNotFoundException("车辆不存在");
        }

        // 清理 Redis 中的实时态缓存和在线标记（MUL-39 修复）
        String rtKey = "vehicle:rt:" + id;
        redis.delete(rtKey);
        redis.opsForSet().remove("vehicle:online", String.valueOf(id));

        // 清理车辆基础元数据缓存
        try {
            String key = "vehicle:meta:" + id;
            redis.delete(key);
        } catch (Exception e) {
            log.error("清理车辆基础缓存失败 vehicleId={}", id, e);
        }

        return removeById(id);
    }

    @Override
    public String getVehicleMetaCache(Long id) {
        if (id == null) {
            return null;
        }
        String key = "vehicle:meta:" + id;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.error("从 Redis 获取车辆基础缓存失败 vehicleId={}", id, e);
        }

        // 缓存未命中，查库并写回缓存
        Vehicle vehicle = getById(id);
        if (vehicle != null) {
            String val = (vehicle.getPlateNo() != null ? vehicle.getPlateNo() : "") + "," + (vehicle.getStatus() != null ? vehicle.getStatus() : 0);
            try {
                redis.opsForValue().set(key, val, 24L, TimeUnit.HOURS);
            } catch (Exception e) {
                log.error("写入车辆基础缓存失败 vehicleId={}", id, e);
            }
            return val;
        }
        return null;
    }

    @Override
    public Map<Long, String> getVehicleMetaCacheBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = ids.stream().map(id -> "vehicle:meta:" + id).toList();
        List<String> cachedValues = null;
        try {
            cachedValues = redis.opsForValue().multiGet(java.util.Objects.requireNonNull(keys));
        } catch (Exception e) {
            log.error("从 Redis 批量获取车辆基础缓存失败", e);
        }

        Map<Long, String> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            String val = cachedValues != null && cachedValues.size() > i ? cachedValues.get(i) : null;
            if (val != null) {
                result.put(id, val);
            } else {
                missIds.add(id);
            }
        }

        if (!missIds.isEmpty()) {
            try {
                // 批量从数据库加载
                List<Vehicle> vehicles = baseMapper.selectBatchIds(missIds);
                if (vehicles != null) {
                    for (Vehicle vehicle : vehicles) {
                        if (vehicle != null && vehicle.getId() != null) {
                            String val = (vehicle.getPlateNo() != null ? vehicle.getPlateNo() : "") + "," + (vehicle.getStatus() != null ? vehicle.getStatus() : 0);
                            result.put(vehicle.getId(), val);
                            String key = "vehicle:meta:" + vehicle.getId();
                            try {
                                redis.opsForValue().set(key, val, 24L, TimeUnit.HOURS);
                            } catch (Exception e) {
                                log.error("写入车辆基础缓存失败 vehicleId={}", vehicle.getId(), e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("批量从DB加载车辆基础数据并缓存失败", e);
            }
        }

        return result;
    }
}

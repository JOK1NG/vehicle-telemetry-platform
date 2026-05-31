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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl extends ServiceImpl<VehicleMapper, Vehicle> implements VehicleService {

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
        return exist;
    }

    @Override
    @Transactional
    public boolean deleteVehicle(Long id) {
        Vehicle exist = getById(id);
        if (exist == null) {
            throw new ResourceNotFoundException("车辆不存在");
        }
        return removeById(id);
    }
}

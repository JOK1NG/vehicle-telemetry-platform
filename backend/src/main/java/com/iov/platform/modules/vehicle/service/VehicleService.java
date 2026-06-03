package com.iov.platform.modules.vehicle.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.iov.platform.modules.vehicle.dto.VehicleCreateRequest;
import com.iov.platform.modules.vehicle.dto.VehicleUpdateRequest;
import com.iov.platform.modules.vehicle.entity.Vehicle;

import java.util.List;
import java.util.Map;

public interface VehicleService extends IService<Vehicle> {

    Page<Vehicle> listVehicles(long current, long size);

    Vehicle getVehicle(Long id);

    Vehicle createVehicle(VehicleCreateRequest request);

    Vehicle updateVehicle(Long id, VehicleUpdateRequest request);

    boolean deleteVehicle(Long id);

    String getVehicleMetaCache(Long id);

    Map<Long, String> getVehicleMetaCacheBatch(List<Long> ids);
}

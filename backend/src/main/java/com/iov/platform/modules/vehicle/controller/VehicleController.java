package com.iov.platform.modules.vehicle.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iov.platform.common.Result;
import com.iov.platform.modules.vehicle.dto.VehicleCreateRequest;
import com.iov.platform.modules.vehicle.dto.VehicleUpdateRequest;
import com.iov.platform.modules.vehicle.entity.Vehicle;
import com.iov.platform.modules.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public Result<Page<Vehicle>> list(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        Page<Vehicle> page = vehicleService.listVehicles(current, size);
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result<Vehicle> detail(@PathVariable Long id) {
        Vehicle vehicle = vehicleService.getVehicle(id);
        if (vehicle == null) {
            return Result.fail(404, "车辆不存在");
        }
        return Result.ok(vehicle);
    }

    @PostMapping
    public Result<Vehicle> create(@Valid @RequestBody VehicleCreateRequest request) {
        Vehicle vehicle = vehicleService.createVehicle(request);
        return Result.ok(vehicle);
    }

    @PutMapping("/{id}")
    public Result<Vehicle> update(@PathVariable Long id, @Valid @RequestBody VehicleUpdateRequest request) {
        Vehicle vehicle = vehicleService.updateVehicle(id, request);
        return Result.ok(vehicle);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
        return Result.ok();
    }
}

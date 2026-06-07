package com.iov.platform.modules.geofence.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.geofence.dto.GeofenceDto;
import com.iov.platform.modules.geofence.service.GeofenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
@RequiredArgsConstructor
public class GeofenceController {

    private final GeofenceService service;

    @GetMapping
    public Result<List<GeofenceDto>> list() {
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public Result<GeofenceDto> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<GeofenceDto> create(@RequestBody GeofenceDto dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<GeofenceDto> update(@PathVariable Long id, @RequestBody GeofenceDto dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}

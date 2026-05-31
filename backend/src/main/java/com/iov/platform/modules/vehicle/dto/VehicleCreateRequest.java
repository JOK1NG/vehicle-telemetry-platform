package com.iov.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VehicleCreateRequest {

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 32, message = "车牌号长度不能超过32个字符")
    private String plateNo;

    @Size(max = 32, message = "VIN长度不能超过32个字符")
    private String vin;

    @Size(max = 64, message = "车型长度不能超过64个字符")
    private String model;
}

package com.iov.platform.modules.ai.dto;

import lombok.Data;

@Data
public class PingRequest {
    private String message;
    private String model;
}

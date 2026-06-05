package com.iov.platform.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PingResponse {
    private String reply;
    private String model;
    private String provider;
    private long latencyMs;
}

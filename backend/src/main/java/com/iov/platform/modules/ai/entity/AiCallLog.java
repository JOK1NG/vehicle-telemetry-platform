package com.iov.platform.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String model;
    private String provider;
    private String requestSummary;
    private String responseSummary;
    private Boolean success;
    private Integer latencyMs;
    private Integer tokenUsage;
    private Long createdBy;
    private OffsetDateTime createdAt;
}

package com.iov.platform.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String businessId;
    private String status;
    private String requestPayload;
    private String resultPayload;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

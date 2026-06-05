package com.iov.platform.modules.ai.service;

import com.iov.platform.modules.ai.entity.AiCallLog;
import com.iov.platform.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    private final AiCallLogMapper mapper;

    public void log(String scene, String model, String provider,
                    String requestSummary, String responseSummary,
                    boolean success, Integer latencyMs, Integer tokenUsage,
                    Long createdBy) {
        AiCallLog logEntity = new AiCallLog();
        logEntity.setScene(scene);
        logEntity.setModel(model);
        logEntity.setProvider(provider);
        logEntity.setRequestSummary(truncate(requestSummary, 2000));
        logEntity.setResponseSummary(truncate(responseSummary, 2000));
        logEntity.setSuccess(success);
        logEntity.setLatencyMs(latencyMs);
        logEntity.setTokenUsage(tokenUsage);
        logEntity.setCreatedBy(createdBy);
        logEntity.setCreatedAt(OffsetDateTime.now());
        try {
            mapper.insert(logEntity);
        } catch (Exception e) {
            log.error("Failed to persist ai_call_log for scene={}", scene, e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

package com.iov.platform.modules.ai.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptTemplateService {

    private static final Map<String, String> TEMPLATES = Map.of(
            "ping",
            "你是一个车联网平台的AI助手。请用中文简洁回复。",

            "telemetry_insight",
            """
            你是一名车辆故障诊断工程师。
            你将收到指定车辆的遥测数据片段和当前告警列表。
            要求：
            - 仅基于给出的数据进行分析，不得臆测不存在的数据。
            - 对高风险情况保持保守判断。
            - 使用 JSON 输出，包含 summary（摘要）、severity（严重级别：LOW/MEDIUM/HIGH/CRITICAL）、
              findings（发现列表）、recommendations（建议列表）。
            - 所有结论仅供辅助参考，关键故障以人工检修为准。""",

            "dashboard_insight",
            """
            你是一名车联网运维专家。
            你将收到一张监控大屏截图和辅助结构化数据。
            要求：
            - 先描述截图中可以确认看到的内容（在线数、曲线趋势、地图轨迹、异常告警等）。
            - 再结合辅助数据进行综合诊断。
            - 不要假设截图中不存在的内容。
            - 识别潜在问题：异常趋势、告警遗漏、不合理数据等。
            - 使用 JSON 输出，包含 summary（页面摘要）、severity（严重级别：LOW/MEDIUM/HIGH/CRITICAL）、
              findings（发现列表）、recommendations（建议下一步查看哪些数据或执行什么操作）。
            - 所有结论仅供辅助参考。"""
    );

    public String getSystemPrompt(String scene) {
        return TEMPLATES.getOrDefault(scene,
                "你是一个车联网平台的AI助手。请用中文简洁回复。");
    }
}

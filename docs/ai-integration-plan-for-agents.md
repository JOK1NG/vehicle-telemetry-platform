# Vehicle Telemetry Platform AI 接入计划（Agent 执行版）

## 文档目标

本文档面向后续协作 Agent，目标是在现有 `vehicle-telemetry-platform` 基础上，规划一套可分阶段落地的 AI 接入方案。重点不局限于当前 MVP，而是基于现有系统骨架向"智能车联网分析平台"扩展。本文强调：模块边界、接入优先级、可执行步骤、接口约定、数据流、风险控制与多模态能力落点。

## 已知项目基线

- 后端基于 Spring Boot 3.4.x、Java 17，使用 Maven 管理依赖。
- 当前系统包含认证、车辆、实时数据、MQTT、WebSocket、模拟器等核心能力，整体属于"车辆遥测平台"架构。
- 数据流主干：车辆或模拟器上报数据，经 MQTT 进入后端，经实时处理后落库与缓存，再通过 WebSocket 推送到前端监控界面。
- 系统已具备 PostgreSQL、Redis、MQTT、前端可视化面板等 AI 接入所需的上下文基础。

> 注：落地时 Agent 需先再次核对本地仓库代码与本文是否一致，再执行实施。

## 总体设计原则

AI 接入不应作为一个孤立聊天框存在，而应作为系统内生能力，嵌入以下关键节点：

1. **实时流入口**：遥测、告警、图像、视频帧进入系统时。
2. **监控视图层**：用户查看 Dashboard、轨迹、统计图时。
3. **诊断决策层**：故障排查、风险解释、工单生成时。
4. **运维分析层**：平台自身的监控、日志、链路诊断时。

因此建议新增独立的 `modules.ai` 模块，作为统一 AI 编排层，而不是把 AI 逻辑散落到 `vehicle`、`realtime`、`mqtt` 等模块中。

## 推荐目标架构

建议在现有后端中引入一个新的领域模块：

\`\`\`text
com.iov.platform.modules.ai
├── controller
│   ├── AiHealthController      ← 配置探测（GET /api/ai/health）
│   ├── AiPingController          ← 最小测试（POST /api/ai/ping，需认证）
│   └── AiInsightController       ← 遥测诊断 + 大屏解读
├── service
│   ├── AiChatGateway             ← 模型调用抽象接口
│   ├── SpringAiChatGateway       ← Spring AI OpenAI-compatible 实现
│   ├── TelemetryInsightService   ← 遥测文本诊断（流式/非流式）
│   ├── DashboardInsightService   ← 大屏截图 + 多模态/文本分析
│   ├── DashboardScreenshotService← Playwright 截图服务
│   ├── PromptTemplateService     ← 场景化 prompt 模板
│   └── AiCallLogService          ← 调用日志持久化
├── dto
│   ├── TelemetryInsightRequest / Response
│   ├── TelemetryInsightStreamEvent
│   ├── DashboardInsightRequest / Response
│   ├── PingRequest / Response
│   └── DashboardInsightResponse.Timing
├── entity
│   ├── AiCallLog
│   └── AiTask
├── mapper
│   ├── AiCallLogMapper
│   └── AiTaskMapper
├── config
│   ├── AiChatProperties
│   └── SpringAiChatConfiguration
└── service
    └── AiJsonUtils               ← JSON 截断/围栏提取工具
\`\`\`

该模块只负责：接收 AI 相关请求、统一封装模型调用、与其他业务模块协作获取上下文、输出自然语言结果与结构化结果、记录 AI 调用日志、成本、审计信息。

## 模型策略

### 首选模型类型

如果目标是多模态落地，建议优先选用支持以下输入的模型：文本、单张图片、多张图片、未来可扩展到视频关键帧。

建议实现"模型适配层"设计，不把代码绑定到某个厂商：

\`\`\`java
public interface MultimodalModelClient {
    AiAnalysisResult analyze(AiPromptProfile profile, List<AiImageInput> images, String textContext);
}
\`\`\`

### 模型候选

- **通义千问多模态**：国内访问稳定，适合生产接入。
- **OpenAI 多模态**：能力成熟，适合对比验证。
- **Ollama / 本地多模态模型**：适合隐私敏感和开发环境，但精度与稳定性需评估。

实际实现：通过 `SpringAiChatGateway` 统一封装 `OpenAiChatModel`，利用 OpenAI-compatible 协议接入 StepFun / Qwen 等 Provider。配置通过 `AiChatProperties` 集中管理，切换 Provider 只需修改 `AI_CHAT_BASE_URL` 与 `AI_CHAT_MODEL`。

## 分阶段实施计划

### Phase 0：基础设施接入

**目标**：先让系统具备"可调用 AI"的最小能力，但不改动现有核心业务路径。

**任务**：

1. 引入 Spring AI 相关依赖（BOM + starter）。
2. 建立 `modules.ai` 模块。
3. 完成统一模型配置。
4. 增加 AI 调用日志表 `ai_call_log` 与异步任务表 `ai_task`。
5. 提供配置探测接口：`GET /api/ai/health`（无需认证，不消耗 API 配额）。
6. 提供最小测试接口：`POST /api/ai/ping`（**需认证**，仅用于带 token 的连通性验证）。

**建议新增表**：

\`\`\`sql
create table ai_call_log (
    id bigserial primary key,
    scene varchar(64) not null,
    model varchar(64) not null,
    provider varchar(64) not null,
    request_summary text,
    response_summary text,
    success boolean not null,
    latency_ms integer,
    token_usage integer,
    created_by bigint,
    created_at timestamp not null default now()
);
\`\`\`

**验收标准**：
- `GET /api/ai/health` 可匿名访问，返回配置状态（`UP`/`DOWN`）。
- `POST /api/ai/ping` 需携带有效 Bearer token，调用模型并返回文本。
- 所有模型调用均记录到 `ai_call_log`。
- 不影响现有 MQTT / WS / realtime 流程。

---

### Phase 1：文本型 AI 能力

**目标**：先接入最稳妥的文本能力，直接消费遥测 JSON 与结构化统计，建立 AI 分析基础。

**场景 1：异常遥测解释**

输入：车辆 ID、时间范围、遥测片段（速度、转速、温度、电压等）、当前告警列表。

输出：总结、异常点、严重程度、运维建议。

**接口建议**：

\`\`\`http
POST /api/ai/insights/telemetry
\`\`\`

请求体示例：

\`\`\`json
{
  "vehicleId": "VH-001",
  "timeRange": {
    "start": "2026-06-03T10:00:00",
    "end": "2026-06-03T10:15:00"
  },
  "metrics": {
    "speed": [12, 18, 82, 95],
    "coolantTemp": [88, 91, 107, 112],
    "rpm": [900, 1500, 3800, 4200]
  },
  "alerts": ["COOLANT_HIGH", "HARSH_BRAKE"]
}
\`\`\`

响应体示例：

\`\`\`json
{
  "summary": "车辆在时间窗口内出现明显高温并伴随高转速运行。",
  "severity": "HIGH",
  "findings": [
    "冷却液温度持续升高并超过安全阈值。",
    "高转速工况下温度未回落。"
  ],
  "recommendations": [
    "建议检查冷却系统与风扇工作状态。",
    "在复检前避免长时间高负载行驶。"
  ]
}
\`\`\`

**Agent 执行要求**：

- 复用现有 `vehicle`、`realtime` 查询服务，不重复造数据访问层。
- Prompt 中必须注入"只根据给定数据回答，不得臆测"的约束。
- 输出结果应拆分为：`summary`、`findings`、`recommendations`、`severity` 四段，方便前端渲染。

---

### Phase 2：Dashboard 视觉解读（第一批多模态）

**目标**：让模型"看懂"前端 Dashboard 截图，而不仅是看 JSON 数据。

**核心价值**：模型可以从整体视角观察：多张曲线图之间的关系、地图轨迹与异常时间点的对应、告警列表与统计卡片的组合呈现。这类信息在纯 JSON 中往往不直观，但在截图里容易被模型整体理解。

**推荐交互**：前端在监控页加入按钮：「AI 解读当前页面」或「帮我分析本页异常」。

用户点击后：

1. 前端截图当前 Dashboard 面板。
2. 同时把当前筛选条件、车辆 ID、时间范围、聚合统计一起发送。
3. 后端调用多模态模型生成解读。

**接口建议**：

\`\`\`http
POST /api/ai/insights/dashboard
\`\`\`

请求体：

\`\`\`json
{
  "vehicleId": "VH-001",
  "dashboardImageBase64": "...",
  "filters": {
    "timeRange": "last_30_minutes",
    "selectedMetrics": ["speed", "temp", "rpm"]
  },
  "summaryStats": {
    "maxSpeed": 118,
    "avgTemp": 96,
    "harshBrakeCount": 5
  }
}
\`\`\`

**Agent 执行要求**：

- 前端截图优先，不在后端重绘图表。
- Base64 图像可落到对象存储或临时文件，再喂给模型。
- 请求中保留结构化摘要，避免模型完全依赖图像猜测数值。
- 若图片过大，前端先压缩到适合推理的尺寸。

**输出建议**：页面摘要、主要异常区域、趋势解释、建议下一步查看的数据。

**适合写入前端的 UI 形态**：右侧 AI 分析抽屉；卡片式结论 + 可展开详细解释；一键生成日报摘要。

---

### Phase 3：仪表盘/故障灯照片诊断（高价值多模态）

**目标**：让司机或运维上传车辆仪表盘照片，由模型识别故障灯，再结合遥测与故障码生成诊断结果。

**这是最值得优先做的多模态场景，原因**：

- 非常贴近真实车联网/运维使用场景。
- 图像信息与结构化遥测信息具有天然互补性。
- 容易体现"多模态联合推理"，而不只是 OCR 或图片分类。

**输入**：仪表盘照片、车辆 ID、拍摄时间（或自动使用上传时间）、同时段遥测片段、如有则带上 OBD 故障码。

**输出**：识别到的告警灯列表、告警含义解释、结合遥测的故障推断、严重级别、继续行驶建议 / 停车建议 / 检修建议。

**接口建议**：

\`\`\`http
POST /api/ai/diagnosis/cluster-photo
\`\`\`

**内部处理链**：

1. 图像识别：先识别仪表盘上的告警图标。
2. 上下文拉取：查询该车在上传前后 5-15 分钟的遥测与故障码。
3. 联合推理：把图像识别结果 + 结构化数据一起交给模型二次判断。
4. 输出标准化诊断结果。

**Agent 执行要求**：

- 拆成两个逻辑阶段，即使最终都由同一个模型完成，也要在代码结构上区分：`extractVisualWarnings()` 和 `buildJointDiagnosis()`。
- 响应中必须包含 `riskLevel` 字段，值限定为 `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`。
- 对"是否可继续行驶"给出明确布尔建议，例如 `drivable: true/false`。
- 最终文案需提示：AI 结论仅供辅助，关键故障以人工检修为准。

---

### Phase 4：车辆外观检查与损伤识别

**目标**：从车队管理角度扩展系统边界，支持"静态检查"。

**使用场景**：交车前检查、还车后检查、日常巡检、维修前初筛。

**输入**：多张车辆外观照片（前后左右/细节）、车辆 ID、检查人、检查时间。

**输出**：发现的潜在损伤列表、位置描述、损伤类型（划痕、凹陷、裂纹等）、严重程度、是否需要工单。

**接口建议**：

\`\`\`http
POST /api/ai/inspection/exterior
\`\`\`

**可扩展数据模型**：

\`\`\`sql
create table vehicle_inspection_report (
    id bigserial primary key,
    vehicle_id varchar(64) not null,
    inspector_id bigint,
    report_text text,
    severity varchar(16),
    created_at timestamp not null default now()
);

create table vehicle_inspection_damage (
    id bigserial primary key,
    report_id bigint not null,
    area varchar(64),
    damage_type varchar(64),
    severity varchar(16),
    description text
);
\`\`\`

**Agent 执行要求**：

- 先做"文本描述型识别"，不要一上来做复杂框选标注。
- 图片存储与数据库解耦，数据库仅保存 URL / key。
- 报告结构既要有自然语言，也要有结构化损伤项，方便后续接工单系统。

---

### Phase 5：驾驶行为教练（视频关键帧 / 图像序列）

**目标**：利用多模态模型对驾驶行为进行更"人类化"的解释。

**推荐切入方式**：先不直接分析完整视频流，而是分析以下轻量素材：急刹/急加速/急转弯事件发生前后的关键帧；行车记录仪截图；轨迹图 + 指标图的组合图像。

**输出**：危险行为说明、驾驶环境描述、风险解释、驾驶建议、驾驶评分（可由规则层二次映射）。

**Agent 执行要求**：

- 不让模型直接输出最终分数，可先输出分类标签，再由本地规则映射成分数。
- 关键帧应与对应时间点的遥测对齐。
- 输出要区分"基于图像判断"与"基于遥测判断"的结论来源，减少黑盒感。

---

### Phase 6：平台运维 AI 助手

**目标**：不仅让 AI 看车，也让 AI 看平台本身。

**输入**：Grafana / Prometheus 截图、链路拓扑图、关键日志摘要、某段时间系统指标。

**输出**：故障可能位置、排查建议顺序、影响范围判断。

**价值**：该能力更适合作为内部运维工具，但能显著扩展项目的系统智能化边界。

---

## 数据与接口约定

### 统一请求 DTO 建议

建议所有 AI 接口收敛成统一的媒体 + 上下文结构：

\`\`\`json
{
  "scene": "dashboard_insight",
  "vehicleId": "VH-001",
  "media": [
    {
      "type": "image",
      "name": "dashboard.png",
      "content": "base64 or object-storage-url"
    }
  ],
  "context": {
    "timeRange": {
      "start": "2026-06-03T10:00:00",
      "end": "2026-06-03T10:30:00"
    },
    "telemetry": {},
    "alerts": []
  }
}
\`\`\`

好处：所有场景共用编排逻辑；更容易切换模型提供商；更容易引入审计与缓存。

### 统一响应 DTO 建议

\`\`\`json
{
  "scene": "dashboard_insight",
  "summary": "...",
  "severity": "MEDIUM",
  "findings": ["..."],
  "recommendations": ["..."],
  "structured": {
    "riskLevel": "MEDIUM",
    "drivable": true,
    "detectedWarnings": []
  },
  "trace": {
    "provider": "qwen",
    "model": "qwen-vl-max",
    "latencyMs": 1860
  }
}
\`\`\`

---

## Prompt 设计要求

后续 Agent 实现时，不要把 prompt 散落在 service 里。建议引入 `PromptTemplateService` 或 resources 下的模板文件。

### Prompt 原则

1. 明确角色，例如"你是车队运维工程师"或"你是车辆故障诊断助手"。
2. 明确数据边界，只允许使用给定输入，不得臆测不存在的传感器或字段。
3. 明确输出格式，尽量要求 JSON 或固定字段。
4. 明确安全提示，对高风险诊断必须保守判断。
5. 明确"视觉结论"和"数值结论"分层输出。

### 示例 Prompt 骨架

\`\`\`text
你是一个车辆诊断助手。

你将收到：
1. 一张或多张车辆相关图片。
2. 对应时间窗口的遥测数据。
3. 当前已知告警或故障码。

要求：
- 先描述图像中可以确认看到的内容。
- 再结合遥测数据进行诊断。
- 不要假设未提供的部件状态。
- 对高风险情况保持保守判断。
- 使用 JSON 输出，包含 summary、findings、recommendations、riskLevel、drivable。
\`\`\`

---

## 与现有模块的协作关系

### 与 `vehicle` 模块

- 获取车辆档案
- 获取车辆状态
- 获取历史保养信息（若未来扩展）

### 与 `realtime` 模块

- 获取时间窗口内遥测片段
- 获取异常事件统计
- 获取聚合趋势信息

### 与 `mqtt` 模块

- 在遥测数据进入系统后，按规则异步触发 AI 任务
- 例如：高温 + 高频急刹事件触发 AI 生成一条异常解释

### 与 `ws` 模块

- 将 AI 分析结果实时推送给前端
- 支持前端看到"AI 正在分析"与"AI 已完成"状态

### 与 `simulator` 模块

- 生成测试用遥测片段
- 联合前端固定截图做回归测试
- 评估 prompt 与模型稳定性

---

## 任务执行模式：同步 vs 异步

### 同步接口

适合：Dashboard 页面即时解读、单张仪表盘图快速诊断。

特点：前端发起请求后直接等待结果；要求响应在数秒内。

### 异步任务

适合：多图外观巡检、驾驶周报/日报生成、平台运维报告生成。

特点：提交任务后先返回 taskId；后台处理完成后前端轮询或 WebSocket 推送。

建议新增任务表：

\`\`\`sql
create table ai_task (
    id bigserial primary key,
    scene varchar(64) not null,
    business_id varchar(64),
    status varchar(16) not null,
    request_payload jsonb,
    result_payload jsonb,
    error_message text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
\`\`\`

---

## 缓存与成本控制

AI 接入后必须考虑成本与性能。

### 建议策略

- 对相同图像 + 相同上下文 hash 做缓存，避免重复调用。
- 对日报/周报类分析做结果持久化。
- 对大图进行尺寸压缩。
- 对非核心实时场景采用异步批处理。
- 记录 token 与耗时，用于后续模型评估与成本审计。

### Redis 可承担的角色

- AI 调用去重缓存
- 任务状态缓存
- 最近分析结果缓存
- 图像文件元数据缓存

---

## 风险与安全约束

### 风险 1：幻觉

模型可能会"看见不存在的东西"或对缺失数据做错误推断。

缓解方式：在 prompt 中强调仅基于输入；输出结构中区分"明确观察到"与"推断"；对高风险诊断要求保守。

### 风险 2：误诊导致业务误用

缓解方式：所有高风险结论需附免责声明；UI 中展示"AI 建议，不替代人工检修"；关键场景引入人工复核状态。

### 风险 3：图片与隐私数据泄露

缓解方式：敏感图片尽量走国内或自建模型；对外部 Provider 做脱敏处理；保留调用审计。

### 风险 4：接口被滥用

缓解方式：所有 AI 接口走鉴权；限流与配额控制；大图大小限制与文件类型校验。

---

## 测试计划

### 单元测试

- Prompt 组装测试
- DTO 映射测试
- Provider Mock 测试
- 风险等级映射测试

### 集成测试

- 模拟图像 + 遥测输入的完整接口测试
- 模拟超时 / Provider 失败 / 非法图片格式测试
- 模拟缓存命中 / 未命中测试

### 回归测试数据集

建议建立一个固定测试集目录：

\`\`\`text
/testdata/ai/
├── dashboard/
├── cluster-photo/
├── inspection/
└── driving/
\`\`\`

每个案例包含：输入图片、输入 JSON、期望输出要点。这样未来切换模型或优化 prompt 时，能做稳定性对比。

---

## 推荐优先级（务实版）

如果资源有限，建议按以下顺序落地：

1. **文本遥测解释**：最快见效，工程复杂度最低。
2. **Dashboard 视觉解读**：最容易让用户直观感受到"AI 真懂界面"。
3. **仪表盘故障灯联合诊断**：多模态价值最高，最像真正产品能力。
4. **外观巡检**：适合扩展系统边界到车队检查。
5. **驾驶行为教练**：适合做进阶功能。
6. **运维 AI 助手**：适合内部使用与平台演进。

---

## 对后续 Agent 的具体执行指令

1. 先核对当前本地仓库结构与本文是否一致，尤其是包名、模块名、配置文件路径。
2. `modules.ai` 基础设施已完成，不要重复造轮子；新增场景复用现有 `AiChatGateway` 和 `PromptTemplateService`。
3. 模型接入走统一 `SpringAiChatGateway`，通过 `application.yml` 的 `ai.chat.*` 配置切换 Provider，**不**需要为每个 Provider 写独立 Client。
4. 第一批上线场景已实现：`telemetry insight`（流式 SSE + 非流式）和 `dashboard multimodal insight`（截图 + 图片/文本回退）。
5. 所有 AI 接口统一走 DTO，不允许在 Controller 中直接拼 prompt。
6. 所有模型调用都必须落日志到 `ai_call_log`。
7. 所有高风险诊断类结果必须带免责声明（已在 prompt 中注入）。
8. Dashboard 截图由后端 Playwright 完成，**不**依赖前端截图上传；前端可提供 base64 图片作为可选输入。
9. 所有输出给前端的 AI 结果都要保留结构化字段（`summary`/`severity`/`findings`/`recommendations`/`latencyMs`）。
10. 新增 AI 接口时同步补充 `GET /api/ai/health` 探测能力（若需要运维感知），并确保敏感端点（如 `ping`）走认证而非 `permitAll`。

---

## 最终目标蓝图

当以上阶段逐步完成后，系统将从"车辆数据监控平台"升级为"智能车联网分析平台"，具备如下能力：

- 看数据：理解遥测与告警。
- 看界面：理解 Dashboard、图表与地图。
- 看照片：识别仪表盘告警与车辆外观问题。
- 看行为：结合关键帧解释驾驶风险。
- 看平台：辅助排查自身系统故障。

这条路线既兼顾论文/展示效果，也兼顾实际产品价值与后续扩展性。

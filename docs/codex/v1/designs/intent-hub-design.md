# 意图中枢总体技术方案

## 设计目标

意图中枢采用“协议无关输入、场景自适应识别、标准确定输出”的设计。核心目标是把多源请求变成统一 Envelope，经过可解释、可降级、可观测的意图识别漏斗，再按场景路由给业务系统。

## 总体架构

建议 v1 拆为 7 个逻辑模块：

1. Traffic Gateway：鉴权、限流、WAF、TraceID、灰度入口。
2. Input Adapter：协议转换、Envelope 标准化、上下文加载。
3. Intent Runtime：预处理、规则识别、模型分类、LLM 兜底、拒识。
4. Scene Router：场景匹配、策略选择、下游动作选择。
5. Output Adapter：防腐层，标准结果、业务动作映射、幂等键生成，只发指令，不碰业务数据。
6. Config & Admin：意图、槽位、同义词、路由、阈值、模型版本管理。
7. Feedback & Observability：bad case、审计、日志、指标、追踪。

## 三大核心变更点

这三点是意图中枢区别于普通 NLU 系统的关键设计原则，后续评审、开发和验收都需要优先检查。

### 1. 双阶段路由：先选怎么认，再选谁来干

普通 NLU 系统通常只负责“识别出意图”，而意图中枢需要同时处理多租户、多渠道、多业务场景。因此路由必须拆为两个阶段：

- 前置路由：在识别前，根据 `tenant_id`、`source`、`channel`、`user_tags` 等信息选择识别策略，包括规则集、模型版本、置信度阈值、是否允许 LLM。
- 后置路由：在识别后，根据 `intent`、`slots`、`confidence`、`scene_id` 等信息选择下游业务动作。

一句话原则：先选怎么认，再选谁来干。

### 2. LLM 受控：最后一道防线，不是主力

LLM 不作为主识别路径，不承担常规高频意图识别。它只在规则和模型无法稳定处理时作为受控兜底，例如长尾表达、复杂指代、低置信度候选解释。

LLM 调用必须受策略控制，并具备开关、超时、预算、熔断、降级、Prompt 注入防护和审计日志。

一句话原则：LLM 是最后一道防线，不是主力。

### 3. 防腐层：只发指令，不碰业务数据

Output Adapter 是意图中枢和业务系统之间的防腐层。它负责把标准意图结果转换为业务指令或事件，但不直接修改业务数据库。

v1 只允许调用业务 API、投递 MQ 事件、发送 Webhook 或发送 MQTT 指令。严禁把 Output Adapter 做成通用业务数据库写入器。

一句话原则：只发指令，不碰业务数据。

## 已确认设计决策

| 事项 | 决策 |
| --- | --- |
| 数据流向图 v2 | 采纳 `数据流向图v2.png` 作为当前数据流向基线；DB Schema 仍以 P0 契约与 Schema 设计为准。 |
| 场景路由顺序 | 采用双阶段路由：前置按 `tenant_id/source/channel` 选策略，后置按 `intent/slots/confidence` 选业务动作。 |
| LLM 兜底边界 | LLM 仅作为受控兜底，必须具备熔断、降级、注入防护、超时、预算和审计。 |
| 防腐层/输出适配 | Output Adapter 只发指令，不碰业务数据；v1 严禁通用直写业务数据库，仅允许通过 API、MQ、Webhook、MQTT 等契约交互。 |
| 租户与配置版本 | 全链路按 `tenant_id + scene_id + version` 管理，配置发布支持回滚。 |
| 槽位生命周期 | 槽位状态机纳入 v1，支持未填、已抽取、待确认、已确认、已过期和缺槽追问。 |
| 拒识策略 | 标准化输出 `decision` 字段，例如 `REJECTED`、`HANDOFF`、`CLARIFY`、`BLOCKED`。 |
| 时序图响应链路 | 区分同步识别与异步执行，异步通过 MQ/Callback/Webhook。 |
| 幂等与重试 | 补 `idempotency_record`，防止下游重复执行。 |
| 多模态 | v1 延期，仅预留字段，不做实际解析。 |

## 已确认技术选型

以下为 2026-06-01 已确认的技术基线，后续 P1 工程骨架、设计评审和验收默认按此执行。若企业内部技术基线、安全规范或运维能力与该基线冲突，应作为变更项重新评审。

| 领域 | 主选 | 备选 | 取舍说明 |
| --- | --- | --- | --- |
| 后端框架 | Java 17/21 + Spring Boot 4.x | Spring Boot 3.5.x LTS/企业支持线 | 采用 Java LTS 和最新 Spring Boot 大版本作为目标基线；若企业生产基线暂未允许 4.x，可短期落在 3.5.x，但保持迁移友好。 |
| 网关 | Apache APISIX 或 Spring Cloud Gateway | Nginx/OpenResty | 必须二选一：APISIX 适合已有 K8s/独立网关/插件治理能力；Spring Cloud Gateway 适合深度 Spring Cloud 体系和较低运维复杂度。 |
| 配置中心 | Nacos 3.x | Apollo | Nacos 作为服务发现、运行时配置分发、配置监听和回滚能力；意图配置事实源仍由 Admin Portal + PostgreSQL `config_version` 管理。 |
| 数据库 | PostgreSQL 16+ | MySQL 8.x | 规则、配置、审计、bad case、幂等记录的主库；JSONB 适合 `recognition_trace`、`context_snapshot`、`latency_breakdown` 等半结构化数据。 |
| 缓存/会话 | Redis 7.x | KeyDB/Valkey | 用于会话缓存、配置缓存、限流辅助、分布式锁和短期幂等状态；最终审计与幂等状态仍落 PostgreSQL。 |
| 消息队列 | Kafka | RocketMQ/RabbitMQ | 用于训练数据回流、bad case 异步标注、审计事件、异步动作和最终一致性回调。 |
| 规则引擎 | 轻量规则 + AC 自动机/正则 | Drools | 高频意图、前置过滤和槽位抽取优先采用可解释、可热更新的轻量规则；P1 不默认引入 Drools。 |
| 模型服务 | Python/FastAPI 或 Triton | Java 内嵌 ONNX Runtime | 分离部署并通过 HTTP/gRPC 通信；FastAPI 适合 P1/P2 快速迭代，Triton 适合 GPU、高并发、多模型生产推理。 |
| LLM 接入 | Spring AI 抽象 + Spring AI Alibaba 默认实现 + Provider Adapter | LangChain4j / 直接 SDK | P1 默认用 Spring AI Alibaba 对接通义/DashScope，但核心仍保持 Spring AI/端口抽象；LLM 是最后一道防线，不是主力。 |
| 向量库 | v1 可暂不引入；需要示例检索时选 Qdrant 或 Milvus | pgvector | 不是意图识别 v1 必需项；只有做样例检索、语义召回或 RAG 时再引入。 |
| 可观测 | OpenTelemetry + Prometheus + Grafana + Loki/ELK | SkyWalking | OTel 是跨 Java、Python、网关和 worker 的统一 telemetry 标准；Prometheus/Grafana 做指标，Loki 或 ELK 做日志检索。 |

执行约束：

- 网关在 P1 执行前必须根据团队运维能力明确落点：已有 APISIX 平台则接入 APISIX，否则优先 Spring Cloud Gateway 跑通闭环。
- 模型服务 P1 优先使用 FastAPI stub/mock 或本地模拟接口，Triton 作为高并发生产推理升级项。
- Nacos 不替代 Admin Portal 的配置事实源；所有意图、槽位、路由和策略变更仍必须经过 `config_version`、发布、回滚和审计。
- Spring AI Alibaba 只能作为 Provider Adapter 的默认实现进入受控 LLM 兜底链路，不允许绕过 Spring AI/端口抽象和 `llm_policy` 成为主识别路径。

参考依据：

- Spring Boot 官方文档显示当前稳定文档线为 4.x：https://docs.spring.io/spring-boot/documentation.html
- Spring Cloud Gateway 官方文档说明其基于 Spring Framework、Spring Boot 和 Reactor：https://docs.spring.io/spring-cloud-gateway/reference/index.html
- Apache APISIX 官方资料覆盖鉴权、限流、插件和微服务网关场景：https://apisix.apache.org/docs/apisix/3.10/getting-started/rate-limiting/
- Nacos 官方发布历史显示 GA 版本已进入 3.x：https://nacos.io/en/download/release-history/
- OpenTelemetry 官方文档定位为厂商中立的 telemetry 框架，覆盖 traces、metrics、logs：https://opentelemetry.io/docs/
- Spring AI 官方资料显示 1.x 稳定线与 2.x 里程碑线并行：https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI Alibaba 官方说明其基于 Spring AI 构建，是通义系列模型及服务在 Java AI 应用开发中的实践方案：https://sca.aliyun.com/en/docs/ai/overview/
- PostgreSQL 官方文档说明 JSON/JSONB 数据类型与索引能力：https://www.postgresql.org/docs/current/datatype-json.html
- Apache Kafka 官方文档覆盖事件流、生产者、消费者和运维配置：https://kafka.apache.org/documentation/
- Redis 官方文档覆盖 Redis 7.x 运行与数据结构能力：https://redis.io/docs/latest/
- FastAPI 与 Triton 官方文档分别覆盖 Python API 服务和生产推理服务：https://fastapi.tiangolo.com/ 与 https://docs.nvidia.com/deeplearning/triton-inference-server/user-guide/docs/
- Milvus 与 Qdrant 均有活跃官方文档，可作为后续语义检索组件：https://milvus.io/docs 与 https://qdrant.tech/documentation/

## 核心数据模型

### IntentDefinition

- `tenant_id`
- `scene_id`
- `intent_code`
- `intent_name`
- `description`
- `scene_scope`
- `examples`
- `required_slots`
- `optional_slots`
- `status`
- `version`

### SlotDefinition

- `tenant_id`
- `scene_id`
- `slot_code`
- `slot_name`
- `data_type`
- `extractor`
- `normalizer`
- `required`
- `validation_rule`

### SlotState

- `session_id`
- `tenant_id`
- `scene_id`
- `slot_code`
- `raw_value`
- `normalized_value`
- `state`：`EMPTY`、`EXTRACTED`、`PENDING_CONFIRM`、`CONFIRMED`、`EXPIRED`
- `source_turn_id`
- `expires_at`

### SceneRoutingRule

- `route_stage`：`PRE` 或 `POST`
- `scene_id`
- `tenant_id`
- `source`
- `channel`
- `intent_code`
- `min_confidence`
- `strategy_id`
- `downstream_action`
- `priority`
- `effective_version`

### ConfigVersion

- `tenant_id`
- `scene_id`
- `version`
- `status`
- `published_by`
- `published_at`
- `rollback_from`
- `change_summary`

### RecognitionTrace

- `trace_id`
- `request_id`
- `preprocess_result`
- `rule_candidates`
- `model_candidates`
- `llm_result`
- `reject_reason`
- `selected_intent`
- `config_version`
- `model_version`
- `latency_breakdown`

### IdempotencyRecord

- `idempotency_key`
- `tenant_id`
- `scene_id`
- `request_hash`
- `downstream_action`
- `status`：`PENDING`、`SUCCESS`、`FAILED`、`RETRYING`、`EXPIRED`
- `retry_count`
- `last_error`
- `expires_at`

### BadCase

- `trace_id`
- `tenant_id`
- `scene_id`
- `input_snapshot`
- `expected_intent`
- `actual_intent`
- `error_type`
- `review_status`
- `annotator`

## DB Schema 分层

配置域：

- `tenant`
- `scene`
- `config_version`
- `intent_definition`
- `slot_definition`
- `synonym_mapping`
- `nlu_strategy`
- `scene_routing_rule`

运行域：

- `conversation_session`
- `slot_state`
- `recognition_trace`
- `bad_case`
- `idempotency_record`
- `audit_log`

## 识别流程

当前数据流向以 `数据流向图v2.png` 为基线，整体拆为三段：

1. 接入与治理：Gateway 校验身份、生成 TraceID、执行限流和基础安全拦截；Input Adapter 将请求转为 Envelope，并按 session_id 加载上下文。
2. 核心意图漏斗：Scene Router 执行前置路由，先根据 tenant/source/channel 初筛场景策略，得到启用的规则集、模型版本、阈值和 LLM 开关；Intent Runtime 执行预处理、规则识别、模型分类、LLM 兜底和拒识判断。
3. 输出与执行：Scene Router 执行后置路由，根据 intent、slots、confidence、metadata 二次确认场景与下游动作；Output Adapter 生成标准 IntentResult 和 idempotency_key，并通过 API/MQ/Webhook/MQTT 发出业务指令。

支撑流：

- Nacos 加载运行时路由规则、模型参数和场景映射；配置事实源仍以 Admin Portal + PostgreSQL 配置版本为准。
- 同步链路逐层返回识别结果；异步链路投递 MQ、Callback 或 Webhook，不与同步识别响应混淆。
- Feedback & Observability 记录识别链路、bad case、指标与审计日志，低置信度和失败样本进入样本库用于后续训练优化。

## 关键接口

### 识别接口

`POST /api/v1/intent/recognize`

用于同步识别并返回标准 IntentResult。适合 Chat、IVR、App、低延迟 API 调用。

### Webhook 接入

`POST /api/v1/events/webhook/{source}`

用于第三方事件输入。Input Adapter 负责把事件映射为 Envelope。

### 配置管理接口

- `POST /api/v1/admin/intents`
- `POST /api/v1/admin/scenes`
- `POST /api/v1/admin/routes`
- `POST /api/v1/admin/strategies`
- `POST /api/v1/admin/publish`

配置变更必须有版本、发布人、审计记录和回滚版本。

## 安全设计

- 租户隔离：所有配置、日志、会话、识别结果必须带 `tenant_id`。
- Prompt 注入防护：LLM 输入只接收脱敏后的标准上下文；系统提示词和工具描述不暴露给用户。
- PII 脱敏：手机号、证件号、地址等进入日志前脱敏；训练回流前二次清洗。
- 鉴权分层：外部 API 使用 API Key/JWT；管理接口使用 RBAC。
- LLM 预算控制：按租户、场景、用户设置调用次数、token、超时和降级策略。
- LLM 受控兜底：仅在规则/模型低置信度、长尾表达或复杂指代场景触发；LLM 异常时熔断并回退到拒识、澄清或人工转接。

## 可观测设计

- Trace：贯穿 Gateway、Adapter、Runtime、Router、Output、Downstream。
- Metrics：QPS、P95/P99、规则命中率、模型命中率、LLM 调用率、拒识率、bad case 率、下游失败率。
- Logs：结构化记录 request_id、trace_id、tenant_id、scene_id、intent_code、strategy_id、model_version。
- Audit：记录配置发布、模型切换、阈值调整、人工修正。

## 部署建议

v1 可以先采用模块化单体 + 独立模型服务：

- `intent-hub-api`：接入、适配、路由、输出、配置读取。
- `intent-model-service`：模型分类推理。
- `intent-admin`：配置后台或管理 API。
- `intent-worker`：bad case 回流、异步日志、数据导出。

当 QPS、团队边界或部署隔离要求升高后，再拆为独立微服务。

## 风险与取舍

- 不建议 v1 直接微服务化拆太细，否则会先承担分布式复杂度而不是验证识别闭环。
- 不建议 v1 直接把 LLM 作为主识别路径，成本、延迟、稳定性和可解释性都不适合核心链路。
- 不建议 Output Adapter 直接写任意业务数据库，应优先调用业务 API 或投递业务事件。
- 多模态应作为协议字段预留，不应进入 v1 主交付。
- 确认约束：Output Adapter 是防腐层，只发指令，不碰业务数据；v1 严禁通用直写业务数据库，任何直写 DB 诉求必须作为特例审批并重新评审边界。

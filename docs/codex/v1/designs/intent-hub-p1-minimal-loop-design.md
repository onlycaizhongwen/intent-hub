# 意图中枢 P1 最小识别闭环设计

## 目标

P1 目标是在 P0 契约和 Schema 已评审通过的基础上，完成最小可运行闭环：

输入请求 -> 输入适配 -> 前置路由 -> 预处理 -> 规则识别 -> 拒识/澄清判断 -> 后置路由 -> 标准输出 -> trace/bad case/idempotency 记录。

P1 不追求完整平台化，不接入真实大模型训练链路，不做完整多模态解析，不做复杂工作流。P1 的核心价值是证明三大核心变更点可以落地：

- 双阶段路由：先选怎么认，再选谁来干。
- LLM 受控：P1 可先预留策略和模拟调用，不进入主链路。
- 防腐层：只发指令，不碰业务数据。

当前数据流向以 `docs/codex/v1/designs/数据流向图v2.png` 为基线。P1 只实现 v2 中的最小闭环：接入与治理、核心意图漏斗、输出与执行三段均要跑通；BERT 精排、真实训练回流和完整 Nacos 动态加载可以先通过端口、内存配置或模拟实现占位。

## 配置治理方案

### P1 决策

P1 默认采用轻量 Admin Portal 作为配置治理方案，GitOps 作为 P2/P3 增强。

原因：

- 意图、槽位、同义词、路由、策略都需要运营或业务人员维护。
- Admin Portal 更适合可视化配置、发布、回滚、审计。
- GitOps 更适合成熟后的环境同步和大规模配置评审，可后置。

### P1 Admin Portal 最小能力

P1 不做复杂后台，只做配置治理闭环：

- 配置草稿：创建或编辑 `intent_definition`、`slot_definition`、`synonym_mapping`、`nlu_strategy`、`scene_routing_rule`、`downstream_action`。
- 配置校验：校验必填字段、版本冲突、路由输出是否存在、动作类型是否合法。
- 配置发布：生成 `config_version`，状态从 `DRAFT` 到 `PUBLISHED`。
- 配置回滚：回滚到历史已发布版本。
- 配置审计：写入 `audit_log`。
- 配置导入导出：支持 JSON 文件导入导出，给后续 GitOps 留接口。

禁止：

- 手工改生产数据库作为正式发布方式。
- 绕过 `config_version` 修改在线配置。
- 配置发布无审计记录。

## P1 技术基线

P1 工程骨架按已确认技术选型落地：

| 模块 | P1 默认技术 |
| --- | --- |
| 后端服务 | Java 17/21 + Spring Boot 4.x；若企业运行基线暂未允许 4.x，可短期使用 Spring Boot 3.5.x，但保持包结构、配置和依赖迁移友好。 |
| 网关入口 | Apache APISIX 或 Spring Cloud Gateway 二选一；P1 执行前按团队网关运维能力定版。 |
| 配置中心 | Nacos 3.x 用于服务发现和运行时配置分发；意图配置事实源仍是 Admin Portal API + PostgreSQL `config_version`。 |
| 主库 | PostgreSQL 16+，承载配置域、运行域、审计、bad case、幂等记录和 JSONB trace 快照。 |
| 缓存 | Redis 7.x，承载会话缓存、配置缓存、限流辅助和短期幂等状态。 |
| 消息队列 | Kafka；P1 只接入必要异步 topic，避免过早扩大异步复杂度。 |
| 规则识别 | 轻量规则 + AC 自动机/正则，不引入 Drools 作为 P1 默认依赖。 |
| 模型服务 | P1 先使用 FastAPI stub/mock 或本地模拟接口；Triton 作为 GPU、高并发、多模型生产推理升级项。 |
| LLM 接入 | Spring AI 抽象 + Spring AI Alibaba 默认实现 + Provider Adapter；P1 先完成接口隔离、预算、超时、审计、fallback，不进入主识别链路。 |
| 可观测 | OpenTelemetry + Prometheus + Grafana + Loki/ELK，从第一版接口开始贯穿 `trace_id`。 |

### Spring AI Alibaba 落地边界

P1 阶段采用 Spring AI Alibaba 作为默认 LLM Provider 实现，优先用于通义/DashScope 适配；但应用层和领域层必须保持 Spring AI/自定义端口抽象，不允许直接依赖 Spring AI Alibaba 的具体类。

落地原则：

- `intent-hub-domain` 只定义 `LlmRecognizePolicy`、`LlmClientPort` 或同等端口，不引用供应商 SDK。
- `intent-hub-application` 只编排 LLM 兜底用例，不决定具体模型供应商。
- `intent-hub-infrastructure/llm` 实现 `TongyiLlmAdapter`，内部可使用 Spring AI Alibaba starter 和 Spring AI `ChatClient`。
- Provider Adapter 必须统一执行 `llm_policy`：开关、触发条件、超时、预算、`max_retries`、熔断、审计、fallback decision。
- P1 默认不让真实 LLM 进入主识别链路；可先启用 stub 或受控沙箱调用验证适配器。

## P1 Maven Module 骨架

为了防止代码变成“大泥球”，P1 采用按领域上下文拆分的 Maven 多模块结构：

```text
intent-hub-parent
├── intent-hub-application          (应用层：编排用例，调用领域服务)
│   └── RecognizeAppService
├── intent-hub-domain               (领域层：核心业务逻辑)
│   ├── recognition                  (识别领域)
│   │   ├── RecognitionTask.java     (聚合根)
│   │   ├── IntentRecognizer.java    (领域服务)
│   │   └── policy                   (策略模式：Rule/Model/Llm)
│   ├── conversation                 (会话领域)
│   │   └── Conversation.java
│   └── config                       (配置领域)
│       └── SceneConfig.java
├── intent-hub-infrastructure        (基础设施层)
│   ├── persistence                  (DB 实现，MyBatis/JPA)
│   ├── cache                        (Redis)
│   ├── mq                           (Kafka/RocketMQ)
│   └── llm                          (Spring AI Alibaba 适配器)
│       └── TongyiLlmAdapter.java
└── intent-hub-interfaces            (用户接口层)
    ├── web                          (REST API)
    └── admin                        (管理后台 API)
```

模块依赖规则：

- `intent-hub-domain` 不依赖 Spring、Spring AI Alibaba、DB、Redis、Kafka、Web。
- `intent-hub-application` 只依赖 domain 和端口接口，负责编排 `RecognizeAppService`。
- `intent-hub-infrastructure` 实现 persistence/cache/mq/llm 等端口，其中 `llm` 默认提供 `TongyiLlmAdapter`。
- `intent-hub-interfaces` 只做 REST/Admin 入参出参转换，不写识别规则和状态机。
- `policy` 下的 Rule/Model/Llm 是策略抽象，不能与具体供应商实现绑定。

## P1 服务模块

### intent-hub-api

职责：

- 暴露 `POST /api/v1/intent/recognize`。
- 执行输入适配、前置路由、预处理、规则识别、后置路由、输出组装。
- 写入 `recognition_trace`、`bad_case`、`idempotency_record`。

### intent-admin

职责：

- 提供配置治理 API。
- 支持草稿、校验、发布、回滚、审计、导入导出。
- P1 可先做 API，不强制做完整 UI。

### intent-worker

职责：

- P1 可选。
- 用于异步 bad case 处理、异步下游动作投递或导出任务。
- 若不引入 worker，P1 可以先同步写 trace/bad case，异步动作仅记录 `ASYNC_ACCEPTED` 和 idempotency。

### intent-model-service

职责：

- P1 使用模拟模型接口或本地 stub。
- 不阻塞规则识别闭环。

## P1 数据流

1. Gateway 或 API 层接收 Envelope。
2. 校验 `tenant_id`、`source`、`channel`、`input_type`、`timestamp`。
3. 生成或透传 `trace_id`。
4. 前置路由读取当前 `tenant_id + scene_id + version` 的已发布配置。
5. 按前置路由选择 `strategy_id`、规则集、阈值和 LLM 策略。
6. 预处理：清洗、脱敏、同义词归一。
7. 规则识别：关键词/正则/AC 自动机命中候选意图。
8. 槽位抽取与状态更新：写入或更新 `slot_state`。
9. 决策：高置信度进入 `SUCCESS`，缺槽进入 `CLARIFY`，低置信度进入 `REJECTED` 或 `HANDOFF`。
10. 后置路由根据意图、槽位、置信度选择 downstream action。
11. 如触发异步动作，生成 `idempotency_key` 并写入 `idempotency_record`。
12. 返回 IntentResult。
13. 写入 `recognition_trace`；低置信度、拒识、人工转接或失败样本写入 `bad_case`。

与数据流向图 v2 的对应关系：

- 接入与治理：步骤 1-3，对应网关、输入适配和 trace 生成。
- 核心意图漏斗：步骤 4-9，对应前置路由、规则识别、模型端口、LLM 受控兜底和拒识。
- 输出与执行：步骤 10-12，对应后置路由、输出适配和下游动作。
- 配置与回流：步骤 4、13 在 P1 可先使用内存配置和内存记录，后续替换为 Nacos 配置监听、PostgreSQL 持久化、Kafka 回流。

## P1 识别策略

### 规则识别最小版

支持：

- exact 关键词匹配
- contains 包含匹配
- regex 正则匹配
- priority 优先级
- confidence 固定值或规则配置值
- explanation 命中解释

不做：

- 复杂规则 DSL
- 多轮复杂推理
- 自动规则学习

### LLM 策略

P1 默认实现采用 Spring AI Alibaba，但不接真实 LLM 主链路。LLM 仍然是最后一道防线，只能在 `llm_policy` 允许时作为受控兜底触发。

支持：

- 读取 `llm_policy`
- 判断是否满足 `trigger_conditions`
- 若启用，可先接 stub provider 或 Spring AI Alibaba 沙箱调用返回模拟/受控结果
- 记录是否触发 LLM、是否降级、fallback_decision
- 记录 provider、model、timeout、budget、max_retries 和审计摘要

不做：

- 大规模真实 LLM 调用
- Prompt 自动优化
- 工具调用

## P1 配置 API

### 配置版本

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/config/versions` | POST | 创建配置草稿 |
| `/api/v1/admin/config/versions/{version}` | GET | 查询配置版本 |
| `/api/v1/admin/config/versions/{version}/validate` | POST | 校验配置 |
| `/api/v1/admin/config/versions/{version}/publish` | POST | 发布配置 |
| `/api/v1/admin/config/versions/{version}/rollback` | POST | 回滚配置 |
| `/api/v1/admin/config/versions/{version}/export` | GET | 导出配置 |
| `/api/v1/admin/config/versions/import` | POST | 导入配置 |

### 配置对象

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/intents` | POST/PUT | 管理意图 |
| `/api/v1/admin/slots` | POST/PUT | 管理槽位 |
| `/api/v1/admin/synonyms` | POST/PUT | 管理同义词 |
| `/api/v1/admin/strategies` | POST/PUT | 管理 NLU 策略 |
| `/api/v1/admin/routes` | POST/PUT | 管理前置/后置路由 |
| `/api/v1/admin/downstream-actions` | POST/PUT | 管理防腐层动作 |

## P1 最小试点场景

### 订单查询

- 输入：`帮我查一下上周的订单`
- 意图：`ORDER_QUERY`
- 必填槽位：`time_range`
- 决策：`SUCCESS`
- 下游：同步返回识别结果，不触发业务执行。

### 订单取消

- 输入：`取消订单 O20260601001`
- 意图：`ORDER_CANCEL`
- 必填槽位：`order_id`
- 决策：`ASYNC_ACCEPTED`
- 下游：MQ `order.command.cancel`
- 幂等：必须写 `idempotency_record`。

### 库存 Webhook

- 输入类型：`WEBHOOK`
- 意图：`INVENTORY_EVENT`
- 必填槽位：`sku_id`、`event_type`
- 决策：`ASYNC_ACCEPTED`
- 下游：Webhook 或 MQ。

## P1 验收用例

| 编号 | 场景 | 输入 | 期望 |
| --- | --- | --- | --- |
| P1-001 | 订单查询成功 | `帮我查一下上周的订单` | `intent_code=ORDER_QUERY`，`decision=SUCCESS` |
| P1-002 | 订单取消异步接收 | `取消订单 O20260601001` | `decision=ASYNC_ACCEPTED`，生成 `idempotency_key` |
| P1-003 | 缺槽澄清 | `帮我取消订单` | `decision=CLARIFY`，提示缺少 `order_id` |
| P1-004 | 未知意图拒识 | `给我讲个笑话` | `decision=REJECTED`，写入 bad case |
| P1-005 | 高风险金额后置路由 | `转账 2000` | 命中 slot condition 示例，进入高风险动作或阻断 |
| P1-006 | 重复异步请求 | 同一订单取消重复请求 | 命中 idempotency，不重复触发下游 |
| P1-007 | 配置回滚 | 发布新规则后回滚 | 回滚后识别结果恢复 |
| P1-008 | 配置审计 | 发布/回滚配置 | `audit_log` 有记录 |

## P1 技术实现顺序

1. 建立 Maven 多模块工程骨架和核心 DTO。
2. 建立 DB migration 初版。
3. 实现配置读取和已发布版本选择。
4. 实现 Admin Portal 最小 API：版本、校验、发布、回滚、审计。
5. 实现识别入口和输入校验。
6. 实现前置路由。
7. 实现预处理和规则识别。
8. 实现槽位状态最小版。
9. 实现 decision 决策。
10. 实现 Spring AI Alibaba 默认适配器 stub，保持 Spring AI/端口抽象。
11. 实现后置路由和防腐层动作描述。
12. 实现 trace、bad case、idempotency 记录。
13. 跑通 3 个试点场景和 P1 验收用例。

## P1 风险控制

### 配置管理一致性

控制措施：

- 所有配置变更必须走 intent-admin API。
- 发布配置时生成 `config_version`。
- 发布和回滚必须写 `audit_log`。
- 提供配置导入导出，为 GitOps 留接口。

### 规则误命中

控制措施：

- 规则必须有 priority 和 explanation。
- 低置信度样本写入 bad case。
- P1 验收覆盖未知意图和缺槽场景。

### 下游重复执行

控制措施：

- 异步动作必须启用 `idempotency_required`。
- 写入 `idempotency_record` 后才能返回 `ASYNC_ACCEPTED`。
- 同一 `idempotency_key` 不重复触发下游。

### 防腐层越界

控制措施：

- downstream action schema 禁止 DB 连接串、表名、SQL。
- 仅允许 API、MQ、Webhook、MQTT。
- 验收时检查 Output Adapter 不具备通用 DB 写入能力。

### LLM 实现侵入领域层

控制措施：

- Spring AI Alibaba 只能出现在 `intent-hub-infrastructure/llm`。
- `intent-hub-domain/recognition/policy` 只保留 LLM 策略抽象。
- 通过模块依赖和代码评审阻止 domain 反向依赖 infrastructure。

## P1 完成标准

- Admin Portal 最小配置治理 API 可用。
- 配置可发布、回滚、审计、导入导出。
- 三个试点场景可跑通。
- P1 验收用例全部通过。
- Maven Module 依赖方向符合 application/domain/infrastructure/interfaces 分层。
- trace 可按 `trace_id` 回溯完整链路。
- bad case 可记录低置信度、拒识、人工转接或失败样本。
- 异步动作具备幂等记录。
- Output Adapter 不触碰业务数据库。

## P1 实现进展

### 已完成的第一步

已创建 Maven 多模块工程骨架：

- `intent-hub-domain`
- `intent-hub-application`
- `intent-hub-infrastructure`
- `intent-hub-interfaces`

已完成内存版最小闭环代码：

- REST 入口：`POST /api/v1/intent/recognize`
- 输入契约：`Envelope`
- 输出契约：`IntentResult`
- 决策枚举：`SUCCESS`、`CLARIFY`、`REJECTED`、`HANDOFF`、`BLOCKED`、`ASYNC_ACCEPTED`
- 规则识别：`RuleRecognitionPolicy`
- 前置路由：加载已发布 `SceneConfig`
- 后置路由：根据意图选择 `DownstreamAction`
- 缺槽澄清：`SceneConfig.requiredSlots` 可声明意图必填槽位，`ORDER_CANCEL` 缺少 `order_id` 时返回 `CLARIFY`
- 幂等：异步动作生成 `idempotencyKey`
- trace：内存记录 `IntentResult`
- bad case：拒识或低置信度进入内存 bad case
- LLM：`TongyiLlmAdapter` stub，仅位于 `intent-hub-infrastructure/llm`

### P1 本地验证结果

当前机器环境：

- JDK 17.0.19 已安装并可通过临时 `JAVA_HOME` 使用。
- 本机已有 Maven 3.9.7，无需重新安装；当前 PowerShell 会话需把 Maven `bin` 临时加入 `Path`。
- 已新增 `scripts/check-p1-env.ps1`，用于检查 JDK 17+ 与 Maven。

已完成验证：

- `mvn clean package` 全 Reactor 构建成功。
- `java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar` 可启动服务，Tomcat 监听 8080。
- `GET /api/v1/admin/health` 返回 `status=UP`。
- `POST /api/v1/intent/recognize` 已手工验证 `SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED`。
- 重复异步取消订单请求返回相同 `idempotencyKey`。
- `intent-hub-domain` 未依赖 Spring Web、Spring AI Alibaba、DB、Redis、Kafka。
- Spring 与 Spring AI Alibaba 依赖只出现在 `intent-hub-infrastructure` 和 `intent-hub-interfaces`。
- REST 入口、规则识别、异步幂等、bad case、trace 和 `TongyiLlmAdapter` stub 文件均已落地。
- `ORDER_CANCEL` 缺槽澄清路径已补齐，缺少 `order_id` 时不再进入异步接收。

### P1 自动化验收结果

已将手工冒烟用例固化为可重复执行的测试：

- 应用层：`RecognizeAppServiceTest` 覆盖订单查询成功、订单取消异步接收并生成稳定幂等键、取消订单缺少 `order_id` 返回 `CLARIFY` 且不生成幂等键、未知意图拒识并记录 bad case。
- 接口层：`RecognizeControllerTest` 采用直接 Controller 契约测试，覆盖请求映射、trace_id 生成、缺槽澄清、未知意图拒识、重复异步取消请求幂等键稳定。
- 测试依赖：`intent-hub-application` 与 `intent-hub-interfaces` 均已加入 `spring-boot-starter-test`。
- 验证结果：`mvn test` 通过，共 9 个测试。
- 验证结果：`mvn clean package` 通过，共 9 个测试。
- 实施取舍：曾尝试 MockMvc 测试，但 Spring Boot 4 测试依赖拆分会让当前最小工程引入额外测试装配复杂度；P1 先采用直接 Controller 契约测试锁定接口层行为，后续进入完整 Web 集成测试时再补 MockMvc 或 TestRestTemplate。

当前 P1-2 已完成，P1-3 PostgreSQL/Flyway 持久化闭环已通过真实库联调，P1-4 配置治理 API 版本生命周期与 JDBC 联调已完成。下一步继续 P1-4：补齐配置对象 CRUD，并把识别配置读取切到已发布版本。

### P1-3 持久化结果

已完成 PostgreSQL/Flyway 最小持久化的工程落地，并通过真实 PostgreSQL 实例做数据查询验收。

已落地内容：

- Flyway migration：`intent-hub-infrastructure/src/main/resources/db/migration/V1__p1_minimal_persistence.sql`。
- 首批 P1 表：`config_version`、`intent_definition`、`slot_definition`、`synonym_mapping`、`nlu_strategy`、`scene_routing_rule`、`downstream_action`、`recognition_trace`、`bad_case`、`idempotency_record`、`audit_log`。
- Spring Boot 4 Flyway 自动配置：`spring-boot-flyway`。
- JDBC adapter：`JdbcRecognitionTraceRepository`、`JdbcBadCaseRepository`、`JdbcIdempotencyRepository`。
- 默认 fallback：`intent-hub.persistence.mode=memory` 时继续使用内存 repository；`local-jdbc` profile 才启用 PostgreSQL 和 Flyway。
- 数据安全：trace 和 bad case 的输入快照写入前会对手机号、邮箱等文本做基础脱敏。
- 幂等约束：`idempotency_record.idempotency_key` 建唯一约束；重复 reserve 捕获唯一键冲突并返回同一个幂等键。

验证结果：

- `mvn test` 通过，共 9 个测试。
- `mvn clean package` 通过，共 9 个测试。
- 默认 memory 模式 jar 可启动，`GET /api/v1/admin/health` 返回 `UP`。
- 已发现并修复 JDBC starter 引入后的默认启动问题：默认模式加入 H2 runtime fallback，Flyway 仍默认关闭，业务持久化仍由 `intent-hub.persistence.mode` 控制。
- `local-jdbc` profile 连接 Docker `postgres:16-alpine` 空库后，Flyway 成功应用 `V1 - p1 minimal persistence`，创建 12 张 public 表。
- `POST /api/v1/intent/recognize` 在 JDBC 模式下验证：
  - `ORDER_QUERY` 返回 `SUCCESS`。
  - `ORDER_CANCEL` 返回 `ASYNC_ACCEPTED` 并生成幂等键。
  - 未知意图返回 `REJECTED` 并写入 bad case。
  - 重复 `REQ-JDBC-102` 返回同一个 `idempotency_key`。
- 数据库查询结果：`recognition_trace=7`、`bad_case=4`、`idempotency_record=1`。

关键问题与修复：

- 首次 `local-jdbc` 联调时接口返回 500，数据库缺少 `recognition_trace`。
- 根因是 Spring Boot 4 的 Flyway 自动配置位于独立 `spring-boot-flyway` 模块；仅引入 `flyway-core` 不会自动执行 migration。
- 已在 `intent-hub-infrastructure` 中新增 `spring-boot-flyway`，并复验通过。

### P1-4 配置治理 API 第一阶段结果

已完成 Admin Portal 最小配置治理 API 的第一阶段，先用 API 闭环替代完整后台 UI。

已落地内容：

- 应用层模型与端口：`ConfigVersionInfo`、`ConfigBundle`、`ConfigValidationResult`、`ConfigVersionPort`、`AuditLogPort`。
- 应用服务：`ConfigVersionAppService`。
- 基础设施适配器：`InMemoryConfigGovernanceRepository`、`JdbcConfigVersionRepository`、`JdbcAuditLogRepository`。
- REST 入口：`AdminConfigController`。
- 请求模型：`ConfigDraftRequest`、`ConfigVersionActionRequest`、`ConfigImportRequest`。

已支持接口：

| 接口 | 方法 | 当前能力 |
| --- | --- | --- |
| `/api/v1/admin/config/versions` | POST | 创建草稿 |
| `/api/v1/admin/config/versions/{version}` | GET | 查询版本 |
| `/api/v1/admin/config/versions/{version}/validate` | POST | 校验版本存在与状态 |
| `/api/v1/admin/config/versions/{version}/publish` | POST | 发布版本，并归档旧 published 版本 |
| `/api/v1/admin/config/versions/{version}/rollback` | POST | 回滚到目标版本 |
| `/api/v1/admin/config/versions/{version}/export` | GET | 导出配置 bundle |
| `/api/v1/admin/config/versions/import` | POST | 导入配置 bundle 为草稿 |

验证结果：

- `mvn test` 通过，共 13 个测试。
- `mvn clean package` 通过，共 13 个测试。
- 默认 memory 模式 jar 可启动。
- 已冒烟验证草稿创建、校验、发布、导出接口。
- 分层检查通过：`intent-hub-domain` 与 `intent-hub-application` 未引入 Spring Web、JDBC、Spring AI Alibaba 等基础设施依赖。
- `local-jdbc` PostgreSQL 联调通过：创建 `v-jdbc-1`、`v-jdbc-2` 草稿，完成校验、发布、回滚、导出。
- 数据库终态：`v-jdbc-1=PUBLISHED`，`v-jdbc-2=ARCHIVED`，`config_version_count=2`，`audit_log_count=6`。
- 审计记录覆盖 `CONFIG_DRAFT_CREATED`、`CONFIG_PUBLISHED`、`CONFIG_ROLLED_BACK`、`CONFIG_EXPORTED`。
- 当前健康检查口径为 `GET /api/v1/admin/health`；`/actuator/health` 未暴露。

当前边界：

- 第一阶段只完成版本生命周期和导入导出边界。
- 细粒度配置对象 CRUD 尚未完成，包括意图、槽位、同义词、策略、路由、下游动作。
- 识别配置读取仍使用当前内存 `SceneConfig`，尚未切换到 PostgreSQL 已发布版本。

下一步：

- 补配置对象 CRUD。
- 将识别配置读取切换到已发布版本。

### 试跑命令

在 JDK 17+ 和 Maven 可用环境执行：

```bash
mvn clean package
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar
```

说明：当前多模块工程直接执行 `mvn -pl intent-hub-interfaces spring-boot:run` 会依赖本地仓库中的 sibling artifacts；加 `-am` 又会让 Spring Boot run 尝试作用到父 POM。因此 P1 阶段以打包 jar 启动作为可靠本地验证方式。

识别请求示例：

```bash
curl -X POST http://localhost:8080/api/v1/intent/recognize \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "demo",
    "source": "app",
    "channel": "chat",
    "inputType": "TEXT",
    "text": "取消订单 O20260601001",
    "requestId": "REQ-001"
  }'
```

预期核心字段：

```json
{
  "intentCode": "ORDER_CANCEL",
  "decision": "ASYNC_ACCEPTED",
  "idempotencyKey": "..."
}
```

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
| `/api/v1/admin/config/versions/{version}/audits` | GET | 查询配置版本审计历史 |
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

当前 P1-2 已完成，P1-3 PostgreSQL/Flyway 持久化闭环已通过真实库联调，P1-4 配置治理 API 版本生命周期、JDBC 联调、配置对象最小 CRUD、已发布配置读取与配置版本审计查询已完成，P1-5 可观测与数据回流最小查询闭环已完成，P1-6 退出评审已通过。P2-1 已补齐动态 scene 读取最小闭环，P2-2 已补齐 bad case 标注、关闭和训练样本导出最小闭环，P2-3 已补齐最小指标采集与观测接口，P2-4 已补齐模型服务 adapter 最小闭环，P2-5 已补齐 LLM 受控兜底最小闭环；后续已补齐模型服务/LLM HTTP timeout 绑定、模型服务异常失败关闭、fallback 指标口径、LLM 预算消费最小计数、持久化审计、日预算原子预占门禁、管理端 confirmed/reserved/pending 查询、同步失败释放、stale pending 后台补偿、模型 adapter 本地 HTTP 冒烟、FastAPI 模型服务示例工程、模型服务健康检查和本地真实联调。下一步建议进入配置对象删除/批量导入与字段校验、真实 DashScope 沙箱冒烟、真实多实例 PostgreSQL 压测和模型服务部署化联调。

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
- 配置版本审计查询已补齐：`GET /api/v1/admin/config/versions/{version}/audits` 可按 `tenantId + sceneId + version` 查询最近审计历史，memory/JDBC 模式均支持，默认限制最近 100 条，最大 500 条。
- 当前健康检查口径为 `GET /api/v1/admin/health`；`/actuator/health` 未暴露。
- 配置对象最小 CRUD 已完成：`POST/GET /api/v1/admin/config/versions/{version}/{objectType}` 支持意图、槽位、同义词、策略、路由、下游动作。
- 编辑约束已落地：只有 `DRAFT` 版本可写，已发布版本不可直接改。
- 默认 memory 模式 HTTP 冒烟通过：intent、slot、downstream-action 可写入并随 export bundle 导出。
- 已发布配置读取已完成：`local-jdbc` 模式优先读取 PostgreSQL 最新 `PUBLISHED` 版本，未命中时回退 P1 内置配置。
- JDBC 冒烟通过：发布 `v-published-read-1` 后，识别请求 `REQ-PUBLISHED-READ-1` 返回 `INVOICE_QUERY/SUCCESS`，路径包含 `PRE_ROUTE:order-scene:v-published-read-1` 与 `POST_ROUTE:INVOICE_QUERY_API`。
- `mvn test` 通过，共 15 个测试。

当前边界：

- 第一阶段只完成版本生命周期和导入导出边界。
- 配置对象删除、批量导入和更细字段校验尚未完成。
- 前置路由动态 scene 读取已在 P2-1 完成最小闭环：JDBC 已发布配置读取不再固定 `order-scene`，支持 metadata 显式 scene、租户最新发布 scene 和内置配置回退。

下一步：

- 补配置对象删除、批量导入和更细字段校验。
- 补 `scene_routing_rule.match_condition` 的复杂条件表达式示例和规则化解析。
- bad case 标注状态流转已在 P2-2 完成最小闭环；最小指标采集已在 P2-3 完成，后续补 Micrometer/OpenTelemetry 桥接、Grafana 看板和告警。

### P1-5 可观测查询结果

已完成 P1-5 最小查询闭环，先以只读 Admin API 验证“可回溯、可筛选”，暂不引入完整观测看板。

已新增：

- 应用层：`RecognitionTraceRecord`、`BadCaseRecord`、`BadCaseQuery`、`ObservabilityQueryPort`、`ObservabilityAppService`。
- 接口层：`AdminObservabilityController`。
- 内存查询：复用 `InMemoryRecognitionTraceRepository` 与 `InMemoryBadCaseRepository`。
- JDBC 查询：复用 `JdbcRecognitionTraceRepository` 查询 `recognition_trace` 与 `bad_case`。

接口：

- `GET /api/v1/admin/observability/traces/{traceId}`
- `GET /api/v1/admin/observability/bad-cases?tenantId=&sceneId=&intentCode=&status=&limit=`

验证：

- `mvn test` 通过，共 17 个测试。
- 默认 memory 模式：`TRACE-OBS-SMOKE-003` 查询到 `ORDER_QUERY/SUCCESS`，路径包含 `POST_ROUTE:ORDER_QUERY_SYNC`。
- `local-jdbc` 模式：`TRACE-JDBC-OBS-003` 从 PostgreSQL 查询到 `INVOICE_QUERY/SUCCESS`，路径包含 `PRE_ROUTE:order-scene:v-published-read-1` 与 `POST_ROUTE:INVOICE_QUERY_API`。
- bad case 查询已验证 `tenantId=demo&status=OPEN` 可返回拒识样本。

### P2-1 动态 scene 读取结果

已完成 P2-1 最小闭环，先解决 P1 固定 `order-scene` 的扩展限制，不改变应用层和领域层契约。

已新增/调整：

- `JdbcSceneConfigRepository`：根据 Envelope metadata 解析 scene。
- 支持 `metadata.scene_id` 与 `metadata.sceneId` 两种显式传参。
- 未传 scene 时，按租户读取最新 `PUBLISHED` 配置版本对应的 scene。
- 指定 scene 无已发布版本时，回退 `BuiltinSceneConfigFactory.orderScene(envelope)`，保持 P1 兼容。
- `intent-hub-infrastructure` 增加测试依赖，并新增 `JdbcSceneConfigRepositoryTest`。

验证：

- `metadata.scene_id=invoice-scene` 时读取 `invoice-scene/v-invoice`。
- metadata 未指定 scene 时读取租户最新发布 scene。
- 指定不存在 scene 时回退 `order-scene/v1-p1`。
- `mvn test` 通过，共 20 个测试。

### P2-2 Bad Case 标注流转与样本导出结果

已完成 P2-2 最小闭环，先解决 P1 只能查询 bad case、不能进入人工运营和训练样本回流的问题。

已新增/调整：

- `BadCaseWorkflowAppService`：应用层写操作门面，负责 traceId、修正意图、actor 和导出参数的基础校验。
- `BadCaseWorkflowPort`：隔离 bad case 标注、关闭、导出训练样本的写操作端口。
- `BadCaseActionResult`：标注或关闭动作返回模型。
- `BadCaseTrainingSample`：训练样本导出模型，包含 `traceId`、`requestId`、`tenantId`、`sceneId`、脱敏文本、意图、decision、confidence、reason、status、metadata 和创建时间。
- `AdminObservabilityController`：新增 bad case 标注、关闭和导出接口。
- `InMemoryBadCaseRepository`：支持 `OPEN/ANNOTATED/CLOSED/EXPORTED` 状态流转。
- `JdbcBadCaseRepository`：复用现有 `bad_case.status`、`intent_code`、`reason` 字段完成最小标注与导出。

接口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/observability/bad-cases/{traceId}/annotate` | POST | 标注 bad case，必填 `correctedIntentCode`，可传 `note` 和 `actor` |
| `/api/v1/admin/observability/bad-cases/{traceId}/close` | POST | 关闭 bad case，可传 `note` 和 `actor` |
| `/api/v1/admin/observability/bad-cases/export` | GET | 导出训练样本，默认 `status=ANNOTATED`、`limit=100`、`markExported=false`、`actor=system` |

状态：

- `OPEN`：识别链路写入的原始 bad case。
- `ANNOTATED`：人工修正意图后进入可导出状态。
- `EXPORTED`：导出时传入 `markExported=true` 后标记。
- `CLOSED`：人工确认不再继续处理。

验证：

- `BadCaseWorkflowRepositoryTest` 覆盖 memory repository 的标注、导出后标记和关闭。
- `AdminObservabilityControllerTest` 覆盖 annotate、close、export 的接口契约。
- `mvn test` 通过，共 24 个测试。

当前边界：

- P2-2 不新增 DB migration，避免对已落地 P1 Schema 做破坏性调整。
- JDBC 标注暂用 `intent_code` 存修正意图，用 `reason` 存标注备注；没有独立 `corrected_intent_code`、`annotation_note`、`annotated_by`、`annotated_at` 字段。
- 导出训练样本只返回 JSON 列表，不负责写对象存储、发 Kafka 或启动训练任务。
- actor 尚未写入审计表，后续应补 audit log 或独立标注历史表。

### P2-3 指标采集与观测接口结果

已完成 P2-3 最小闭环，先解决 P1/P2 已有 trace 查询但缺少聚合指标出口的问题。

已新增/调整：

- `IntentMetricsPort`：应用层指标记录端口。
- `MetricsSnapshot`：指标快照模型，包含请求总数、bad case 候选数、模型 fallback 次数、LLM fallback 次数、LLM 预算消费尝试、LLM 预算后台补偿数量、耗时统计、P95/P99 长尾耗时、decision/intent/scene 计数和时间戳。
- `MetricsAppService`：指标查询与 Prometheus 文本导出服务。
- `InMemoryIntentMetricsRepository`：线程安全内存指标实现。
- `AdminMetricsController`：新增指标 Admin API。
- `RecognizeAppService`：识别完成后记录指标，不改变识别输出契约。

接口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/metrics` | GET | 返回 JSON 指标快照 |
| `/api/v1/admin/metrics/prometheus` | GET | 返回 Prometheus text/plain 指标 |

当前指标：

- `totalRequests`
- `totalBadCases`
- `totalModelFallbacks`
- `totalLlmFallbacks`
- `totalLlmBudgetAttempts`
- `totalLlmBudgetConsumed`
- `totalLlmBudgetReconciliations`
- `totalLatencyMillis`
- `averageLatencyMillis`
- `maxLatencyMillis`
- `p95LatencyMillis`
- `p99LatencyMillis`
- `decisions`
- `intents`
- `scenes`

验证：

- `RecognizeAppServiceTest` 覆盖识别链路会调用 metrics port。
- `InMemoryIntentMetricsRepositoryTest` 覆盖 `MODEL_FALLBACK` 与 `LLM_FALLBACK` 分别计数。
- `AdminMetricsControllerTest` 覆盖 JSON 快照和 Prometheus 文本导出。
- `mvn test` 通过，共 26 个测试。

当前边界：

- P2-3 不引入 Actuator/Micrometer，不暴露 `/actuator/health` 或 `/actuator/prometheus`。
- 当前指标为进程内内存指标，服务重启后清零。
- Prometheus 文本由 `MetricsAppService` 从快照生成，后续可桥接到 Micrometer registry 或 OpenTelemetry metrics。
- 暂未补 Grafana dashboard、告警规则和分布式实例聚合。

### P2-4 模型服务适配结果

已完成 P2-4 最小闭环，先把模型服务作为规则之后、LLM 之前的候选来源接入主链路。

已新增/调整：

- `ModelClientPort`：领域层模型服务端口。
- `ModelRecognitionPolicy`：领域层模型候选策略，仅在模型返回候选时写入 `recognitionPath`。
- `ModelServiceProperties`：基础设施配置，读取 `intent-hub.model-service.enabled/base-url/timeout-ms`。
- `HttpModelClientAdapter`：FastAPI 风格 HTTP adapter，调用 `POST {baseUrl}/recognize`，并通过 `GET {baseUrl}/health` 查询模型服务健康状态。
- `NoopModelClientAdapter`：默认关闭或未配置 baseUrl 时使用，不影响现有规则链路。
- `RecognizeAppService`：识别策略顺序调整为 `RuleRecognitionPolicy -> ModelRecognitionPolicy -> LlmRecognizePolicy`。
- `ModelRecognitionPolicy`：模型服务异常时记录 `MODEL_FALLBACK:CLOSED` 并失败关闭，避免外部模型不可用导致识别请求 500。

模型服务请求：

```json
{
  "text": "用户输入文本",
  "sceneId": "order-scene"
}
```

模型服务响应：

```json
{
  "intentCode": "ORDER_QUERY",
  "confidence": 0.82,
  "slots": {},
  "explanation": "model hit"
}
```

配置：

```yaml
intent-hub:
  model-service:
    enabled: false
    base-url: ""
    timeout-ms: 2000
```

验证：

- `RecognizeAppServiceTest` 覆盖规则未命中时模型候选可进入识别结果，路径包含 `ModelRecognitionPolicy`。
- `RecognizeAppServiceTest` 覆盖模型服务异常时失败关闭，路径包含 `MODEL_FALLBACK:CLOSED`。
- `ModelClientAdapterTest` 覆盖 no-op adapter、inactive HTTP adapter、MockRestServiceServer 成功返回、模型服务健康检查和 JDK 本地 HTTP server 冒烟。
- `AdminHealthControllerTest` 覆盖 `GET /api/v1/admin/health` 的模型服务健康状态输出。
- `IntentHubBeanConfigurationTest` 覆盖真实 Spring 容器启动所需的 `RestClient.Builder` Bean。
- `mvn test` 通过，全量共 61 个测试；`mvn package -DskipTests` 通过。

当前边界：

- 已新增 `examples/model-service-fastapi`，提供 `/health` 和 `/recognize` 最小样例工程。
- 已补 JDK 本地 HTTP server 冒烟，覆盖真实 POST、请求体和 JSON 响应解析；FastAPI 示例工程已本地冒烟通过；健康检查已接入 Admin health；已启动 jar + FastAPI 示例完成本地真实联调，`cancel A100` 识别路径包含 `ModelRecognitionPolicy`。
- 模型服务开关是全局配置，尚未进入 `tenant + scene + version` 的动态策略配置。
- `timeoutMs` 已通过 `SimpleClientHttpRequestFactory` 绑定到模型服务 HTTP adapter 的 connect/read timeout。

### P2-5 LLM 受控兜底结果

已完成 P2-5 最小闭环，先把 LLM 从“端口 stub”推进到“受全局治理与 scene 策略共同约束的最后兜底”。

已新增/调整：

- `LlmPolicy`：补 `dailyBudget`，并对 provider、model、timeout、maxRetries、fallbackDecision 做基础规整。
- `LlmRecognizePolicy`：只有 `enabled=true`、`dailyBudget>0`、`timeoutMs>0` 时才进入 LLM；provider 异常时记录 `LLM_FALLBACK:{fallbackDecision}` 并失败关闭。
- `LlmClientPort`：调用时接收 `LlmPolicy`，让 adapter 可以执行策略约束。
- `LlmGovernanceProperties`：读取 `intent-hub.llm.enabled/base-url/timeout-ms/max-retries/daily-budget/min-confidence`。
- `TongyiLlmAdapter`：提供 Spring AI Alibaba 优先、HTTP 契约 fallback 的受控兜底 adapter，按治理、策略和日预算原子预占门禁外呼，并执行有限重试和最低置信度过滤。
- `JdbcSceneConfigRepository`：从已发布 `nlu_strategy.llm_policy` 读取 scene 级 LLM 策略。
- `LlmBudgetAuditPort` / `LlmBudgetUsage`：记录并查询 LLM 外呼尝试的预算审计用量。
- `InMemoryLlmBudgetAuditRepository` / `JdbcLlmBudgetAuditRepository`：提供 memory/JDBC 双实现。
- `V2__p2_llm_budget_usage.sql`：新增 `llm_budget_usage` 表，按租户、场景、日期、provider 和 model 聚合外呼尝试次数与消费单位。
- `LlmBudgetAppService` / `AdminLlmBudgetController`：提供 `GET /api/v1/admin/llm/budget-usage` 查询日预算审计用量。

配置：

```yaml
intent-hub:
  llm:
    enabled: false
    base-url: ""
    timeout-ms: 3000
    max-retries: 0
    daily-budget: 0.0
    min-confidence: 0.70
```

验证：

- `RecognizeAppServiceTest` 覆盖 LLM provider 异常时失败关闭并写入 `LLM_FALLBACK:REJECTED`。
- `RecognizeAppServiceTest` 覆盖 scene 策略预算为 0 时不进入 LLM。
- `TongyiLlmAdapterTest` 覆盖治理关闭、策略预算为 0、Spring AI Alibaba ChatClient 分支、HTTP 契约 fallback、成功返回候选、有限重试后失败、真实外呼尝试前记录预算消费、远端失败后释放本次预占、日预算耗尽不外呼和全局预算收紧阻断；memory/JDBC 预算仓储测试覆盖日预算预占成功/失败、查询不双算、pending 差额暴露和失败释放后可再次预占。
- `JdbcSceneConfigRepositoryTest` 覆盖从已发布 `nlu_strategy.llm_policy` 读取策略。
- `mvn test` 通过，共 61 个测试。

当前边界：

- 当前 `TongyiLlmAdapter` 已预接入 Spring AI Alibaba `ChatClient`，并保留 HTTP 契约 fallback。
- DashScope 沙箱 profile 与冒烟脚本已准备完成；尚未使用真实 DashScope 沙箱密钥完成外部冒烟。
- `timeoutMs` 已通过 `SimpleClientHttpRequestFactory` 绑定到 LLM HTTP adapter 的 connect/read timeout。
- 当前已完成 LLM 外呼预算消费最小计数、持久化审计、外呼前日预算原子预占门禁、同步失败释放、默认关闭的 stale pending 后台补偿、补偿指标和管理端 confirmed/reserved/pending 查询；还没有真实多实例 PostgreSQL 压测、分布式保护和超额告警。

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

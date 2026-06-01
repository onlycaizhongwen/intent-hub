# 意图中枢 P1 下一步执行计划

## 目标

下一步不进入 P2，而是把当前“内存版最小闭环”推进为“可编译、可验收、可回溯、可配置发布”的 P1 工程闭环。

当前基线：

- 数据流向：采用 `docs/codex/v1/designs/数据流向图v2.png`。
- 架构原则：双阶段路由、LLM 受控、防腐层。
- 工程骨架：`intent-hub-domain`、`intent-hub-application`、`intent-hub-infrastructure`、`intent-hub-interfaces`。
- LLM 默认实现：P1 采用 Spring AI Alibaba，但保持 Spring AI/自定义端口抽象。

## 当前状态

已完成：

- Maven 多模块骨架。
- 内存版 `POST /api/v1/intent/recognize`。
- Envelope、IntentResult、decision 枚举。
- 轻量规则识别、前置路由、后置路由。
- 内存 trace、bad case、idempotency。
- `TongyiLlmAdapter` stub，位于基础设施层。

当前验证状态：

- JDK 17.0.19 已安装并可通过临时 `JAVA_HOME` 使用。
- 本机已有 Maven 3.9.7，无需重新安装；当前 PowerShell 会话需把 Maven `bin` 临时加入 `Path`。
- `mvn clean package` 已完成全 Reactor 构建并通过。
- 服务已通过打包后的 `intent-hub-interfaces` jar 启动，健康检查和核心识别接口已完成手工冒烟。
- P1-2 自动化验收已固化：`mvn test` 与 `mvn clean package` 均通过，共 9 个测试。
- P1-3 已完成：Flyway migration、JDBC adapter、默认 memory fallback、`local-jdbc` PostgreSQL profile 和真实 PostgreSQL 联调均已通过。
- P1-4 已完成配置版本生命周期、JDBC 联调、配置对象最小 CRUD 和已发布配置读取：配置版本草稿、查询、校验、发布、回滚、导入导出、审计端口/API、配置对象 Upsert/List、识别链路读取最新 PUBLISHED 配置均已落地。
- P1-5 已完成可观测最小查询闭环：trace_id 查询和 bad case 筛选 API 已落地，并通过 memory 与 local-jdbc 冒烟。

## 总体顺序

P1 下一步按 6 个工作包推进：

1. P1-1：工程可编译与本地可启动。
2. P1-2：识别接口验收用例固化。已完成。
3. P1-3：PostgreSQL/Flyway 持久化最小落地。已完成。
4. P1-4：Admin Portal 最小配置治理 API。版本生命周期、JDBC 联调、配置对象最小 CRUD 与已发布配置读取已完成。
5. P1-5：可观测与数据回流闭环。已完成最小查询 API。
6. P1-6：P1 退出评审与 P2 准入。

## P1-1 工程可编译与本地可启动

目标：

- 在 JDK 17+ 与 Maven 环境完成 `mvn clean package`。
- 启动 `intent-hub-interfaces`，验证 REST 入口可访问。

当前准备状态：

- 已新增 `scripts/check-p1-env.ps1` 用于检查 JDK 17+ 与 Maven。
- 当前本机已具备 JDK 17.0.19 与 Maven 3.9.7；`scripts/check-p1-env.ps1` 在设置临时环境变量后可通过。
- `mvn clean package` 全 Reactor 构建成功。
- `mvn -pl intent-hub-interfaces spring-boot:run` 会因 sibling module artifact 未安装而失败；`mvn -pl intent-hub-interfaces -am spring-boot:run` 会尝试在父 POM 上执行 Spring Boot run，也不适合作为当前启动命令。
- 当前可靠启动方式为先执行 `mvn clean package`，再执行 `java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar`。
- 已完成静态分层检查：Spring、Web、Spring AI Alibaba 依赖仅出现在 `intent-hub-infrastructure` 与 `intent-hub-interfaces`，未侵入 `intent-hub-domain` 与 `intent-hub-application`。
- 已补齐缺槽澄清的最小领域逻辑：`ORDER_CANCEL` 需要 `order_id`，缺失时返回 `CLARIFY`，不会提前生成异步幂等键。

实施项：

- 确认 parent `pom.xml`、四个 module `pom.xml` 的依赖方向。
- 修复 Java 17/21、Spring Boot 4.x 或兼容 Spring Boot 3.5.x 下的编译问题。
- 增加最小 `application.yml`，区分 `local` profile 和后续 `dev/prod` profile。
- 补充启动类、健康检查、基础异常处理和统一响应。

验收证据：

- `mvn clean package` 通过。
- `java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar` 可启动。
- `GET /api/v1/admin/health` 返回 `UP`。
- 领域层不依赖 Spring Web、Spring AI Alibaba、DB、Redis、Kafka。

停点：

- 编译和启动通过后再进入持久化与配置治理，避免在不可编译代码上继续叠功能。

## P1-2 识别接口验收用例固化

目标：

- 把 P1 验收从“手工试一下”变成可重复执行的测试。

实施项：

- 为 `RecognizeAppService` 增加单元测试。
- 为 REST 接口增加集成测试或 MockMvc 测试。
- 固化 8 个 P1 验收用例：
  - 订单查询成功。
  - 订单取消异步接收。
  - 缺槽澄清。
  - 未知意图拒识。
  - 高风险金额后置路由。
  - 重复异步请求幂等。
  - 配置回滚。
  - 配置审计。

验收证据：

- 测试能覆盖 `SUCCESS`、`CLARIFY`、`REJECTED`、`ASYNC_ACCEPTED`。
- `recognition_path` 能看出 Rule/Model/LLM/Reject 的路径。
- 低置信度或拒识样本进入 bad case 记录。
- 重复异步请求不会重复生成下游动作。

当前手工冒烟结果：

- `查一下订单` 返回 `intentCode=ORDER_QUERY`、`decision=SUCCESS`。
- `取消订单 O20260601001` 返回 `intentCode=ORDER_CANCEL`、`decision=ASYNC_ACCEPTED`，并生成 `idempotencyKey`。
- `帮我取消订单` 返回 `intentCode=ORDER_CANCEL`、`decision=CLARIFY`，且 `idempotencyKey=null`。
- `给我讲个笑话` 返回 `intentCode=UNKNOWN`、`decision=REJECTED`。
- 重复提交同一 `requestId=REQ-P1-002` 的订单取消请求，返回相同 `idempotencyKey`。

当前自动化验收结果：

- 已新增 `RecognizeAppServiceTest`，覆盖 `SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED`、bad case 记录和幂等键稳定。
- 已新增 `RecognizeControllerTest`，覆盖请求映射、trace_id 生成、接口层缺槽澄清、拒识和重复异步请求幂等。
- `mvn test` 通过，共 9 个测试。
- `mvn clean package` 通过，共 9 个测试。
- MockMvc 方案曾尝试但不作为 P1 当前实现：Spring Boot 4 测试依赖拆分会给最小工程带来额外测试装配复杂度，先用直接 Controller 契约测试锁定行为。

停点：

- 识别验收测试通过后，再把内存记录替换为数据库实现。

## P1-3 PostgreSQL/Flyway 持久化最小落地

目标：

- 将 P0 Schema 中 P1 必需表落到 PostgreSQL。
- 保留内存实现作为 local fallback，但正式 P1 验收以数据库持久化为准。

实施项：

- 引入 Flyway migration。
- 首批落表：
  - `config_version`
  - `intent_definition`
  - `slot_definition`
  - `synonym_mapping`
  - `nlu_strategy`
  - `scene_routing_rule`
  - `downstream_action`
  - `recognition_trace`
  - `bad_case`
  - `idempotency_record`
  - `audit_log`
- 实现 repository/mapper 端口适配。
- 对 `input_snapshot`、`context_snapshot` 写入前做脱敏。
- 对 `idempotency_key` 增加唯一约束或等价判重机制。

验收证据：

- Flyway 可从空库初始化。
- 一次识别请求后可查到 trace。
- 拒识或低置信度请求可查到 bad case。
- 异步动作可查到 idempotency record。
- 重复请求不会重复触发下游动作。

停点：

- 只落 P1 必需表，不扩展完整训练样本库和模型版本治理。

当前第一阶段结果：

- 已引入 `spring-boot-starter-jdbc`、`flyway-core`、`flyway-database-postgresql`、PostgreSQL runtime driver，并加入 H2 runtime 作为默认 memory 启动 fallback。
- 已补齐 Spring Boot 4 `spring-boot-flyway` 依赖，确保 `spring.flyway.enabled=true` 时加载 Flyway 自动配置。
- 已新增 Flyway `V1__p1_minimal_persistence.sql`，覆盖 P1 必需配置表、运行表、幂等表和审计表。
- 已新增 JDBC 版 `RecognitionTracePort`、`BadCasePort`、`IdempotencyPort` 适配器。
- 默认 `intent-hub.persistence.mode=memory`，`spring.flyway.enabled=false`；`local-jdbc` profile 使用 PostgreSQL 并启用 Flyway。
- 已补输入快照脱敏和幂等键唯一约束处理。
- `mvn test` 与 `mvn clean package` 均通过，共 9 个测试。
- 默认 jar 启动成功，`GET /api/v1/admin/health` 返回 `UP`。

真实 PostgreSQL 联调结果：

- 使用 Docker `postgres:16-alpine` 准备空库 `intent_hub`。
- 使用 `--spring.profiles.active=local-jdbc` 启动服务。
- Flyway 从空库成功应用 `V1 - p1 minimal persistence`，创建 12 张 public 表。
- 发起订单查询、订单取消、未知意图和重复订单取消请求。
- 查询结果：`recognition_trace=7`、`bad_case=4`、`idempotency_record=1`。
- 重复 `REQ-JDBC-102` 返回同一个 `idempotency_key`，幂等表未新增重复记录。

关键修复：

- 首次 `local-jdbc` 联调出现 `relation "recognition_trace" does not exist`。
- 根因：Spring Boot 4 将 Flyway 自动配置拆到 `spring-boot-flyway` 模块；仅引入 `flyway-core` 不会自动执行 migration。
- 处理：在 `intent-hub-infrastructure` 增加 `spring-boot-flyway`，保留 `flyway-core`、`flyway-database-postgresql` 和 PostgreSQL driver。

## P1-4 Admin Portal 最小配置治理 API

目标：

- 解决 P0 评审指出的配置一致性高风险点。
- P1 可以没有完整 UI，但必须有可审计、可回滚、可导入导出的配置治理 API。

实施项：

- 实现配置版本 API：
  - 创建草稿。
  - 查询版本。
  - 校验配置。
  - 发布配置。
  - 回滚配置。
  - 导入导出。
- 实现配置对象 API：
  - 意图。
  - 槽位。
  - 同义词。
  - 策略。
  - 前置/后置路由。
  - 下游动作。
- 发布时写入 `audit_log`。
- 配置读取优先读已发布版本。
- Nacos 在 P1 可先做运行时配置分发预留；配置事实源仍是 Admin API + PostgreSQL。

验收证据：

- 修改规则后发布新版本，识别结果按新版本生效。
- 回滚后识别结果恢复。
- 租户 A 配置不影响租户 B。
- 所有发布和回滚都有审计记录。

停点：

- 不做复杂前端后台；先完成 API 与配置生命周期。

当前第一阶段结果：

- 已新增应用层模型与端口：`ConfigVersionInfo`、`ConfigBundle`、`ConfigValidationResult`、`ConfigVersionPort`、`AuditLogPort`。
- 已新增应用服务：`ConfigVersionAppService`，覆盖草稿、查询、校验、发布、回滚、导入、导出。
- 已新增基础设施适配器：`InMemoryConfigGovernanceRepository`、`JdbcConfigVersionRepository`、`JdbcAuditLogRepository`。
- 已新增 REST 入口：`AdminConfigController`。
- 已支持接口：
  - `POST /api/v1/admin/config/versions`
  - `GET /api/v1/admin/config/versions/{version}`
  - `POST /api/v1/admin/config/versions/{version}/validate`
  - `POST /api/v1/admin/config/versions/{version}/publish`
  - `POST /api/v1/admin/config/versions/{version}/rollback`
  - `GET /api/v1/admin/config/versions/{version}/export`
  - `POST /api/v1/admin/config/versions/import`
- 发布逻辑已修正为只归档旧 `PUBLISHED` 版本，不会把未发布草稿提前归档。
- `mvn test` 与 `mvn clean package` 均通过，共 13 个测试。
- 默认 memory 模式已完成 API 冒烟：草稿创建、校验、发布、导出均可用。

真实 PostgreSQL 联调结果：

- 使用 `--spring.profiles.active=local-jdbc` 启动服务，连接 Docker PostgreSQL 16。
- 创建 `v-jdbc-1`、`v-jdbc-2` 草稿，完成 `validate`、`publish`、再次 `publish`、`rollback` 和 `export`。
- 数据库终态：`v-jdbc-1=PUBLISHED`，`v-jdbc-2=ARCHIVED`。
- 审计记录：`CONFIG_DRAFT_CREATED` 2 条、`CONFIG_PUBLISHED` 2 条、`CONFIG_ROLLED_BACK` 1 条、`CONFIG_EXPORTED` 1 条。
- 查询结果：`config_version_count=2`，`audit_log_count=6`。
- 健康检查口径：当前项目暴露 `GET /api/v1/admin/health`；`/actuator/health` 未暴露，联调时返回 404。

配置对象 CRUD 结果：

- 已新增 `ConfigObjectAppService`、`ConfigObjectPort`、`ConfigObjectType`。
- 已新增 `JdbcConfigObjectRepository`，覆盖 `intent_definition`、`slot_definition`、`synonym_mapping`、`nlu_strategy`、`scene_routing_rule`、`downstream_action`。
- 已新增 `ConfigObjectRequest` 与 REST 入口：`POST/GET /api/v1/admin/config/versions/{version}/{objectType}`。
- `objectType` 支持 `intents`、`slots`、`synonyms`、`strategies`、`routes`、`downstream-actions`。
- 写入约束：只允许编辑 `DRAFT` 版本，发布版本不可直接改。
- 默认 memory 模式 HTTP 冒烟：创建草稿后写入 intent、slot、downstream-action，导出 bundle 可带出对象。
- `mvn test` 通过，共 15 个测试。

已发布配置读取结果：

- 已新增 `JdbcSceneConfigRepository` 和 `BuiltinSceneConfigFactory`。
- `local-jdbc` 模式优先读取 PostgreSQL 最新 `PUBLISHED` 配置，未找到发布版本时回退 P1 内置配置。
- 当前映射范围：`intent_definition.definition` 到规则，`slot_definition.required` 到必填槽，`downstream_action` 到后置动作。
- JDBC 冒烟：发布 `v-published-read-1`，识别请求 `REQ-PUBLISHED-READ-1` 命中 `INVOICE_QUERY/SUCCESS`。
- 路径证据：`PRE_ROUTE:order-scene:v-published-read-1`、`POST_ROUTE:INVOICE_QUERY_API`。

剩余工作：

- 补齐配置对象删除、批量导入和更细的字段校验。
- 前置路由动态 scene 选择已在 P2-1 完成最小闭环：JDBC 已发布配置读取支持 `metadata.scene_id` / `metadata.sceneId` 显式选择 scene，未指定时读取租户最新 `PUBLISHED` scene，未命中时回退内置配置。

## P1-5 可观测与数据回流闭环

目标：

- 让每次识别都能被追踪、统计、回放和沉淀为 bad case。

实施项：

- 全链路贯穿 `trace_id`。已完成。
- `recognition_trace` 记录：
  - pre-route 结果。
  - 规则/模型/LLM/拒识路径。
  - post-route 结果。
  - latency breakdown。
  - config version。
- `bad_case` 记录：
  - 低置信度。
  - 拒识。
  - 人工转接。
  - 下游失败。
- 增加基础指标：
  - 请求量。
  - 规则命中率。
  - 拒识率。
  - 澄清率。
  - LLM 触发率。
  - 平均耗时和 P95。

验收证据：

- 任一 `trace_id` 可定位识别路径。已通过 `GET /api/v1/admin/observability/traces/{traceId}` 验证。
- bad case 可按租户、场景、意图、状态筛选。已通过 `GET /api/v1/admin/observability/bad-cases` 验证。
- 指标可用于判断规则误命中、低置信度和 LLM 兜底比例。

停点：

- P1 只要求记录与导出，不要求自动训练或自动优化规则。

当前结果：

- 已新增应用层模型与端口：`RecognitionTraceRecord`、`BadCaseRecord`、`BadCaseQuery`、`ObservabilityQueryPort`、`ObservabilityAppService`。
- 已新增 REST 入口：`AdminObservabilityController`。
- 已支持接口：
  - `GET /api/v1/admin/observability/traces/{traceId}`
  - `GET /api/v1/admin/observability/bad-cases?tenantId=&sceneId=&intentCode=&status=&limit=`
- 内存模式通过 `InMemoryRecognitionTraceRepository` 聚合 trace 查询，并委托 `InMemoryBadCaseRepository` 查询 bad case。
- JDBC 模式通过 `JdbcRecognitionTraceRepository` 读取 `recognition_trace` 与 `bad_case`。
- `mvn test` 通过，共 17 个测试。
- 默认 memory 冒烟：`TRACE-OBS-SMOKE-003` 可查询到 `ORDER_QUERY/SUCCESS`，路径包含 `POST_ROUTE:ORDER_QUERY_SYNC`。
- `local-jdbc` 冒烟：`TRACE-JDBC-OBS-003` 可从 PostgreSQL 查询到 `INVOICE_QUERY/SUCCESS`，路径包含 `PRE_ROUTE:order-scene:v-published-read-1` 和 `POST_ROUTE:INVOICE_QUERY_API`。

剩余工作：

- 补 Prometheus/OpenTelemetry 指标采集。
- 补 bad case 标注、关闭、导出和训练数据回流状态流转。

## P1-6 P1 退出评审与 P2 准入

目标：

- 明确什么时候 P1 算完成，什么时候可以进入 P2。

P1 完成标准：

- 工程可编译、可启动。
- 8 个 P1 验收用例通过。
- 数据库 migration 可从空库初始化。
- 配置发布、回滚、审计可用。
- trace、bad case、idempotency 已持久化。
- Output Adapter 没有直写业务库能力。
- LLM 仍是受控兜底，默认不作为主识别路径。
- 模块依赖方向符合 DDD 骨架约束。

P2 准入建议：

- 至少完成 2-3 个试点场景验收。
- Bad Case 已积累到可以评估规则覆盖率。
- 配置治理闭环可支撑多人协作。
- 已有基础压测数据，能判断是否需要引入 FastAPI/Triton 模型服务。

## 不建议现在做的事

- 不建议直接接入真实大规模 LLM 流量。
- 不建议先做完整 Admin 前端 UI。
- 不建议引入 Drools 这类重规则引擎。
- 不建议把 BERT/Triton 作为 P1 阻塞项。
- 不建议让 Output Adapter 直连业务库或携带 SQL。

## 推荐执行顺序

| 顺序 | 工作包 | 预计产物 | 进入下一步条件 |
| --- | --- | --- | --- |
| 1 | P1-1 工程可编译与本地可启动 | 可编译工程、健康检查 | `mvn clean package` 通过 |
| 2 | P1-2 识别接口验收用例固化 | 单元/接口测试 | 已通过：`mvn test` 与 `mvn clean package` 均 9 个测试通过 |
| 3 | P1-3 持久化最小落地 | Flyway + repository | 已完成：真实 PostgreSQL 验证 trace/bad case/idempotency 可查 |
| 4 | P1-4 配置治理 API | 草稿/发布/回滚/审计 API、配置对象 Upsert/List、已发布配置读取 | 已完成到 P1 最小闭环；下一步配置治理细化或 P1-5 |
| 5 | P1-5 可观测与回流 | trace 查询、bad case 筛选、后续指标 | 已完成最小查询闭环；后续补指标与标注流转 |
| 6 | P1-6 退出评审 | P1 验收报告 | 满足 P2 准入条件 |

## 下一步最小动作

优先执行 P1-1：

```bash
mvn clean package
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar
```

Windows 环境可先执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-p1-env.ps1
```

环境通过后再执行：

```bash
mvn clean package
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar
```

当前 P1-1、P1-2、P1-3、P1-4、P1-5 与 P1-6 均已完成；P2-1 动态 scene 读取最小闭环也已完成。下一步建议进入 P2-2 bad case 标注流转，同时并行补指标采集和配置治理细化。

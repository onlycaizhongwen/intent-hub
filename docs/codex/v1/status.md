# 项目状态

- 当前版本：v1
- 当前阶段：P2-5 LLM 受控兜底最小闭环已完成，P2 试点扩展进行中；模型服务健康检查、本地真实联调、Spring AI Alibaba 预接入、DashScope 沙箱冒烟准备、LLM 预算持久化审计、日预算原子预占门禁、同步失败释放、stale pending 后台补偿、补偿指标、基础告警快照、运维样例总入口、生产化落地检查清单、观测告警试点接入计划、试点执行记录模板、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例、告警 Runbook 与管理端 confirmed/reserved/pending 查询已完成
- 当前主题：intent-hub
- 说明：本文档记录意图中枢需求、设计、计划、审查主线状态。

## 需求索引

| 主题 | 需求文档 | 依赖 | 状态 |
| --- | --- | --- | --- |
| intent-hub | `docs/codex/v1/requirements/intent-hub-requirements.md` | 现有 designs 资料、首批业务场景、模型服务、配置与运维基线 | 已完成 |

## 进度与状态

| 主题 | Requirements | Design | Plan | Trace/Review | 整体状态 |
| --- | --- | --- | --- | --- | --- |
| intent-hub | 已完成 | P1 已设计，技术选型与 DDD 骨架已确认 | 已完成 | 已完成 | P1 有条件通过；P2-1 动态 scene 读取、P2-2 Bad Case 标注流转、P2-3 最小指标采集、P2-4 模型服务适配和 P2-5 LLM 受控兜底均已完成 |

## 交付物

- 需求文档：`docs/codex/v1/requirements/intent-hub-requirements.md`
- 技术方案：`docs/codex/v1/designs/intent-hub-design.md`
- P0 契约与 DB Schema 设计：`docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md`
- P1 最小识别闭环设计：`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`
- 实施计划：`docs/codex/v1/plans/intent-hub-plan.md`
- P1 下一步执行计划：`docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`
- 资料审查：`docs/codex/v1/trace/intent-hub-material-review.md`
- 确认决策：`docs/codex/v1/trace/intent-hub-confirmed-decisions.md`
- P0 评审报告：`docs/codex/v1/trace/intent-hub-p0-review-report.md`
- 技术选型确认：`docs/codex/v1/trace/intent-hub-tech-selection-confirmation.md`
- 数据流向图 v2 影响评估：`docs/codex/v1/trace/intent-hub-data-flow-v2-review.md`
- P1 退出评审：`docs/codex/v1/trace/intent-hub-p1-exit-review.md`
- P2-1 动态 scene 读取审查：`docs/codex/v1/trace/intent-hub-p2-dynamic-scene-routing-trace.md`
- P2-2 Bad Case 标注流转审查：`docs/codex/v1/trace/intent-hub-p2-bad-case-workflow-trace.md`
- P2-3 指标观测审查：`docs/codex/v1/trace/intent-hub-p2-metrics-observability-trace.md`
- P2-4 模型服务适配审查：`docs/codex/v1/trace/intent-hub-p2-model-service-adapter-trace.md`
- P2-5 LLM 受控兜底审查：`docs/codex/v1/trace/intent-hub-p2-llm-governance-trace.md`
- 运维样例总入口：`ops/README.md`
- 生产化落地检查清单：`ops/production-readiness-checklist.md`
- 观测告警试点接入计划：`ops/pilot-rollout-plan.md`
- 试点执行记录模板：`ops/pilot-execution-record-template.md`
- Prometheus 运维样例：`ops/prometheus/README.md`
- Alertmanager 路由样例：`ops/alertmanager/README.md`
- Grafana 看板样例：`ops/grafana/intent-hub-dashboard.json`
- SLO 样例：`ops/slo/README.md`
- 本地观测栈样例：`ops/local-observability/README.md`
- 告警 Runbook：`ops/runbooks/intent-hub-alert-runbook.md`
- HTML 阅读版：`docs/codex/v1/intent-hub-lifecycle.html`

## 变更记录

- 2026-06-01：根据 `docs/codex/v1/designs/` 资料完成意图中枢 v1 需求、总体技术方案、技术选型、实施计划和资料不合理点审查。
- 2026-06-01：根据用户确认回执，将 DB Schema、双阶段路由、LLM 受控兜底、禁止直写 DB、租户版本、槽位生命周期、拒识 decision、同步/异步分离、幂等重试、多模态延期固化进正式文档和 HTML。
- 2026-06-01：将三大核心变更点提升为规划主线：双阶段路由（先选怎么认，再选谁来干）、LLM 受控（最后一道防线，不是主力）、防腐层（只发指令，不碰业务数据）。
- 2026-06-01：进入 P0，新增契约与 DB Schema 设计，覆盖 Envelope、IntentResult、decision、双阶段路由、LLM 受控、防腐层动作模型、DB Schema 和评审清单。
- 2026-06-01：P0 评审结论为通过（Approved），已将微调项和配置一致性高风险点回写到 P0 设计与实施计划，可进入 P1。
- 2026-06-01：进入 P1 设计，默认采用轻量 Admin Portal 作为配置治理方案，GitOps 作为后续增强，并产出最小识别闭环设计。
- 2026-06-01：固化技术选型确认：Java 17/21 + Spring Boot 4.x、APISIX/SCG、Nacos 3.x、PostgreSQL 16+、Redis 7.x、Kafka、轻量规则、FastAPI/Triton、Spring AI 抽象 + Provider Adapter、OpenTelemetry 观测体系；同步更新总体设计、P1 设计和 HTML 阅读版。
- 2026-06-01：补充 P1 工程约束：LLM 默认实现采用 Spring AI Alibaba，但保持 Spring AI/自定义端口抽象；新增 Maven Module 骨架 `intent-hub-application/domain/infrastructure/interfaces`，并明确 `TongyiLlmAdapter` 只能位于基础设施层。
- 2026-06-01：完成 P1 启动前确认：P0 契约与 Schema 已 Approved，技术选型已确认，DDD/Maven Module 骨架已确认；状态更新为 P1 最小识别闭环可启动。
- 2026-06-01：开始 P1 最小识别闭环实现，新增 Maven 多模块工程 `intent-hub-domain/application/infrastructure/interfaces`，完成内存版 REST 识别入口、Envelope/IntentResult、轻量规则识别、双阶段路由、trace、bad case、幂等记录和 `TongyiLlmAdapter` stub。当前本机仅 Java 8 且未安装 Maven，真实编译需 JDK 17+ 与 Maven。
- 2026-06-01：采纳 `docs/codex/v1/designs/数据流向图v2.png` 作为当前数据流向基线，确认其不推翻 P0/P1，而是强化三阶段流转、双阶段路由、Nacos 配置加载和 Bad Case 回流；已同步更新 trace、资料审查、确认决策、总体设计、P1 设计、实施计划和 HTML 阅读版。
- 2026-06-01：新增 P1 下一步执行计划，明确下一步不直接进入 P2，而是按工程可编译、本地可启动、验收用例、PostgreSQL/Flyway 持久化、Admin Portal 配置治理 API、可观测回流和 P1 退出评审推进。
- 2026-06-01：推进 P1-1 编译启动准备，确认当前本机 Java 8 且 Maven 不可用，新增 `scripts/check-p1-env.ps1` 环境检查脚本；完成静态分层验证，并补齐 `ORDER_CANCEL` 缺少 `order_id` 时返回 `CLARIFY`、不提前生成异步幂等键的领域逻辑。
- 2026-06-01：完成 P1-1/P1-2 手工验证：已安装并使用 JDK 17.0.19，复用本机 Maven 3.9.7，`mvn clean package` 全 Reactor 构建成功；通过 `java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar` 启动服务，`/api/v1/admin/health` 返回 `UP`，`POST /api/v1/intent/recognize` 已验证 `SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED` 和重复异步请求幂等键稳定。
- 2026-06-01：完成 P1-2 自动化验收固化：新增 `RecognizeAppServiceTest` 与 `RecognizeControllerTest`，覆盖订单查询、订单取消异步接收、缺槽澄清、未知意图拒识、trace_id 生成和重复异步请求幂等键稳定；`mvn test` 与 `mvn clean package` 均通过，共 9 个测试。下一步进入 P1-3 PostgreSQL/Flyway 持久化最小落地。
- 2026-06-01：完成 P1-3 持久化第一阶段：新增 Flyway `V1__p1_minimal_persistence.sql`，落地 `config_version`、意图/槽位/同义词/策略/路由/动作配置表、`recognition_trace`、`bad_case`、`idempotency_record`、`audit_log`；新增 JDBC 版 trace、bad case、idempotency 适配器和敏感文本脱敏；默认仍为 memory fallback，`local-jdbc` profile 启用 PostgreSQL + Flyway。`mvn test` 与 `mvn clean package` 均通过，共 9 个测试；默认 jar 启动后健康检查返回 `UP`。下一步需要连接真实 PostgreSQL 做 Flyway 初始化和查询验收。
- 2026-06-01：完成 P1-3 真实 PostgreSQL/Flyway 联调：补齐 Spring Boot 4 `spring-boot-flyway` 自动配置依赖，使用 Docker `postgres:16-alpine` 空库启动 `local-jdbc` profile，Flyway 成功应用 `V1 - p1 minimal persistence` 并创建 12 张 public 表；接口验证 `ORDER_QUERY/SUCCESS`、`ORDER_CANCEL/ASYNC_ACCEPTED`、`UNKNOWN/REJECTED`，数据库查询 `recognition_trace=7`、`bad_case=4`、`idempotency_record=1`；重复 `REQ-JDBC-102` 返回相同幂等键且幂等表不新增重复记录。下一步进入 P1-4 Admin Portal 最小配置治理 API。
- 2026-06-01：完成 P1-4 Admin Portal 最小配置治理 API 第一阶段：新增 `ConfigVersionAppService`、配置版本端口、审计端口、内存/JDBC 适配器和 `AdminConfigController`；支持配置草稿、查询、校验、发布、回滚、导入、导出并写入审计端口。`mvn test` 与 `mvn clean package` 均通过，共 13 个测试；默认 memory 模式 jar 启动后已冒烟草稿创建、校验、发布和导出接口。
- 2026-06-01：完成 P1-4 Admin API `local-jdbc` 联调：使用 PostgreSQL 验证创建 `v-jdbc-1`、`v-jdbc-2` 草稿、校验、发布、发布新版本、回滚和导出；数据库终态为 `v-jdbc-1=PUBLISHED`、`v-jdbc-2=ARCHIVED`，`config_version_count=2`、`audit_log_count=6`，审计覆盖草稿、发布、回滚、导出。当前健康检查口径为 `GET /api/v1/admin/health`，`/actuator/health` 未暴露。下一步补意图、槽位、策略、路由、下游动作的细粒度 CRUD，并把识别配置读取切到已发布版本。
- 2026-06-01：完成 P1-4 配置对象最小 CRUD：新增 `ConfigObjectAppService`、`ConfigObjectPort`、`ConfigObjectType`、`JdbcConfigObjectRepository` 和 `ConfigObjectRequest`；支持 `POST/GET /api/v1/admin/config/versions/{version}/{objectType}` 管理意图、槽位、同义词、策略、路由、下游动作，并限制仅 `DRAFT` 版本可编辑。`mvn test` 通过，共 15 个测试；默认 memory 模式 HTTP 冒烟验证 intent、slot、downstream-action 写入后可通过 export bundle 导出。下一步把识别配置读取切到已发布版本。
- 2026-06-01：完成 P1-4 已发布配置读取：新增 `JdbcSceneConfigRepository` 与 `BuiltinSceneConfigFactory`，`local-jdbc` 模式优先读取 PostgreSQL 最新 `PUBLISHED` 配置，未找到发布版本时回退 P1 内置配置；发布 `v-published-read-1` 后，`POST /api/v1/intent/recognize` 对 `REQ-PUBLISHED-READ-1` 命中 `INVOICE_QUERY/SUCCESS`，路径包含 `PRE_ROUTE:order-scene:v-published-read-1` 和 `POST_ROUTE:INVOICE_QUERY_API`，数据库 trace 已记录。`mvn test` 通过，共 15 个测试。
- 2026-06-01：完成 P1-5 可观测与数据回流最小查询闭环：新增 `ObservabilityAppService`、`ObservabilityQueryPort`、trace/bad case 查询模型和 `AdminObservabilityController`；支持 `GET /api/v1/admin/observability/traces/{traceId}` 与 `GET /api/v1/admin/observability/bad-cases`。`mvn test` 通过，共 17 个测试；默认 memory 模式已验证 `TRACE-OBS-SMOKE-003` 查询到 `ORDER_QUERY/SUCCESS`，`local-jdbc` 模式已验证 `TRACE-JDBC-OBS-003` 从 PostgreSQL 查询到 `INVOICE_QUERY/SUCCESS`。
- 2026-06-01：完成 P1-6 退出评审，结论为有条件通过（Conditionally Approved）；P1 已具备可运行、可配置、可回溯、可持久化的最小闭环，可进入 P2 规划与试点扩展。非阻塞遗留项包括指标采集、bad case 标注流转、动态 scene 路由、配置对象删除/批量导入、真实模型服务和真实 LLM 小流量验证。
- 2026-06-01：完成 P2-1 动态 scene 读取最小闭环：`JdbcSceneConfigRepository` 不再固定 `order-scene`，支持 Envelope `metadata.scene_id` / `metadata.sceneId` 显式选择已发布 scene；未指定时读取租户最新 `PUBLISHED` scene；指定 scene 无发布版本时回退内置 `order-scene/v1-p1`。新增 `JdbcSceneConfigRepositoryTest` 覆盖 metadata 指定、租户最新发布和回退兼容，`mvn test` 通过，共 20 个测试。
- 2026-06-02：完成 P2-2 Bad Case 标注流转与样本导出最小闭环：新增 `BadCaseWorkflowAppService`、`BadCaseWorkflowPort`、标注/关闭/导出训练样本 Admin API，并接入 memory/JDBC 双实现；最小状态流转复用 `bad_case.status` 表达 `OPEN/ANNOTATED/CLOSED/EXPORTED`，暂不新增破坏性 DB migration。`mvn test` 通过，共 24 个测试。
- 2026-06-02：完成 P2-3 最小指标采集与观测接口：新增 `IntentMetricsPort`、`MetricsAppService`、`MetricsSnapshot`、`InMemoryIntentMetricsRepository` 和 `AdminMetricsController`；支持 `GET /api/v1/admin/metrics` JSON 快照与 `GET /api/v1/admin/metrics/prometheus` 文本导出。当前不引入 Actuator/Micrometer，不改变 `/api/v1/admin/health` 口径。`mvn test` 通过，共 26 个测试。
- 2026-06-02：完成 P2-4 模型服务适配最小闭环：新增 `ModelClientPort`、`ModelRecognitionPolicy`、`ModelServiceProperties`、`HttpModelClientAdapter` 和 `NoopModelClientAdapter`；识别策略顺序为 Rule -> Model -> LLM，默认关闭/no-op，不影响规则主链路。`mvn test` 通过，共 29 个测试。
- 2026-06-02：完成 P2-5 LLM 受控兜底最小闭环：新增 `LlmGovernanceProperties`、LLM adapter 请求/响应契约、已发布 `nlu_strategy.llm_policy` 读取、预算/超时门禁、有限重试和 fallback 失败关闭；默认 `intent-hub.llm.enabled=false` 且预算为 0，不影响规则和模型主链路。后续已补齐模型服务与 LLM HTTP timeout 绑定、模型服务异常失败关闭、模型/LLM fallback 指标口径、LLM 预算消费最小计数、模型 adapter 本地 HTTP 冒烟、FastAPI 模型服务示例、模型服务健康检查接入、本地真实联调、Spring AI Alibaba `ChatClient` 预接入和 DashScope 沙箱冒烟 profile/script 准备。联调中发现并修复真实 jar 启动缺少 `RestClient.Builder` Bean 的问题。当前复验 `mvn test` 通过，共 57 个测试。
- 2026-06-02：修复 DashScope 冒烟脚本的 PowerShell 字符串语法问题，改用 ASCII 冒烟输入规避 Windows 终端编码破坏；已通过 PowerShell Parser 语法检查、`mvn test`、`mvn package -DskipTests`、`python -m py_compile examples/model-service-fastapi/app.py` 和 `git diff --check`（仅 CRLF 提示）。本轮按用户要求暂不提交、不推送。
- 2026-06-02：补齐 LLM 预算持久化审计最小闭环，新增 `LlmBudgetAuditPort`、`LlmBudgetUsage`、memory/JDBC 实现和 Flyway `V2__p2_llm_budget_usage.sql`；`TongyiLlmAdapter` 在真实外呼尝试前同时写入指标和预算审计。`mvn -pl intent-hub-infrastructure -am test` 通过，相关模块共 31 个测试。
- 2026-06-03：补齐 LLM 日预算同步失败释放闭环，新增 `LlmBudgetAuditPort.releaseDailyBudgetReservation`，memory/JDBC 在 provider 同步异常后释放本次预占；`TongyiLlmAdapter` 在远端失败时释放 reserved 预算但保留 confirmed 外呼尝试审计，管理端 pending 差额可用于发现未释放或异步异常。`mvn -pl intent-hub-infrastructure,intent-hub-interfaces -am test` 通过，相关模块共 57 个测试。
- 2026-06-03：补齐 LLM 日预算 stale pending 后台补偿最小能力，新增 `LlmBudgetAuditPort.reconcileStaleDailyBudgetReservations`、memory/JDBC 实现和默认关闭的 `intent-hub.llm.budget-reconciliation.*` 调度任务；补偿只校正 `__budget__/__daily__` reserved 预占行，不回滚 confirmed 外呼审计。`mvn test` 通过，全量共 61 个测试。
- 2026-06-03：补齐 LLM 日预算后台补偿指标，新增 `intent_hub_llm_budget_reconciliations_total`，用于观察 stale reserved 预占被后台补偿校正的数量，为后续告警接入打基础；补偿指标只记录校正行数，不改变预算补偿语义。
- 2026-06-04：补齐基础告警快照，新增 `MetricsAlertAppService` 与 `GET /api/v1/admin/metrics/alerts`，基于 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时返回 `OK/WARN/CRITICAL`；`mvn -pl intent-hub-interfaces -am test` 通过，共 62 个测试。
- 2026-06-04：补充 `ops/prometheus/intent-hub-alert-rules.yml` Prometheus/Alertmanager 告警规则样例，覆盖 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时；该样例不改变运行时代码，不引入 Actuator/Micrometer/Alertmanager 依赖，生产化 scrape、route、Grafana dashboard 和 SLO 仍待后续补齐。
- 2026-06-04：补充 `ops/grafana/intent-hub-dashboard.json` Grafana 看板样例，覆盖请求量、bad case 率、耗时、decision 分布、fallback、LLM 预算活动、intent 和 scene 分布；该样例依赖 Prometheus 已抓取 `/api/v1/admin/metrics/prometheus`，不提供生产化 datasource、folder/provisioning、权限和 SLO 配置。
- 2026-06-04：补充 `ops/prometheus/intent-hub-scrape-config.yml` 和 `ops/prometheus/README.md`，提供 Prometheus 抓取 `/api/v1/admin/metrics/prometheus` 的配置片段样例与接入说明；生产环境仍需补服务发现、TLS/鉴权、Alertmanager route、receiver、SLO 和多实例聚合策略。
- 2026-06-04：补充 `ops/slo/README.md` SLO 与错误预算样例，按可用性、延迟、质量、受控 LLM、预算补偿和 Bad Case 回流划分目标；该样例不是正式 SLA，生产落地前仍需结合租户等级、真实流量、成本预算和监管要求确认阈值。
- 2026-06-04：补充 `ops/alertmanager/alertmanager-route-sample.yml` 和 `ops/alertmanager/README.md`，提供 Alertmanager route、receiver 和 inhibit 样例；生产环境仍需替换真实接收地址，并补齐 TLS/鉴权、secret 管理、升级策略、静默策略和审计要求。
- 2026-06-04：补充 `ops/local-observability/` 本地观测栈样例，通过 Docker Compose 串联 Prometheus、Alertmanager 和 Grafana，用于本地验证 `/api/v1/admin/metrics/prometheus`、告警规则和 dashboard；该样例不是生产部署配置。
- 2026-06-04：补充 `ops/runbooks/intent-hub-alert-runbook.md`，覆盖 bad case 率高、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时高和最大耗时 critical 的影响判断、止血动作、定位步骤和复盘清单。
- 2026-06-04：补充 `ops/README.md` 运维样例总入口，将 Prometheus scrape、告警规则、Alertmanager 路由、Grafana 看板、SLO、本地观测栈和告警 Runbook 串成统一接入顺序，并明确生产化前必改项。
- 2026-06-04：补充 `ops/production-readiness-checklist.md` 生产化落地检查清单，将指标入口、Prometheus、Alertmanager、Grafana、SLO、安全合规、演练验收拆成可确认事项。
- 2026-06-04：补充 `ops/pilot-rollout-plan.md` 观测告警试点接入计划，按 dev/staging 试点环境拆解一周接入节奏、验收步骤、回滚策略和复盘模板。
- 2026-06-04：补充 `ops/pilot-execution-record-template.md` 试点执行记录模板，用于真实试点时统一记录 metrics、scrape、rule、route、dashboard、告警演练、复盘和附件证据。

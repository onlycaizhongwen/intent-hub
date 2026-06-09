# 项目状态

- 当前版本：v1
- 当前阶段：P2-17 配置评审权限模型最小闭环已完成；P2-6 密钥治理与外部联调准入、P2-7 多实例一致性与压测、P2-8 观测告警本地试点、P2-9 配置发布治理增强、P2-10 配置审批与 GitOps 导出、P2-11 Admin 配置评审工作台聚合契约、P2-12 配置评审驳回与撤回、P2-13 审批快照哈希、P2-14 审批快照哈希强字段化、P2-15 发布 expectedSnapshotHash 条件校验、P2-16 审批元数据强字段化均已形成阶段证据。模型服务健康检查、本地真实联调、模型服务容器化配置样例、Spring AI Alibaba 预接入、DashScope 沙箱冒烟准备、LLM 预算持久化审计、日预算原子预占门禁、同步失败释放、stale pending 后台补偿、补偿指标、基础告警快照、运维样例总入口、生产化落地检查清单、观测告警试点接入计划、试点执行记录模板、本地试点执行记录、告警演练场景、本地观测栈预检脚本、本地观测栈配置校验脚本、外部联调前预检脚本、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例、告警 Runbook、管理端 confirmed/reserved/pending 查询、模型策略 JDBC 冒烟、带鉴权模型服务本地 smoke、Secret 轮换 smoke、多实例一致性 smoke、LLM 预算多实例 smoke、LLM 补偿多实例 smoke、基础双实例压测 smoke、P2-8 本地观测告警 smoke、配置版本 diff API、发布前 dry-run 报告、GitOps 文件结构建议、提交评审、批准、驳回、撤回、审批快照哈希、审批快照哈希强字段、发布 expectedSnapshotHash 条件校验、审批人和审批时间强字段、配置评审权限角色门禁、GitOps 审查包导出、Admin 配置评审工作台聚合契约、scene 级模型 endpoint/timeout 动态路由、场景模型客户端缓存复用、模型服务 token 引用鉴权与缺失失败关闭、统一 Secret resolver 默认实现、文件挂载 Secret resolver 预留、managed-config Secret resolver、配置版本审计查询、配置对象删除与批量导入、配置字段基础校验、发布前跨对象引用校验、`scene_routing_rule.match_condition` 最小后置路由条件解析、显式 `actionSchema.intentCode` 动作归属读取已完成。真实 Prometheus/Alertmanager/Grafana dev/staging 接入、真实 GitOps PR 同步、前端 Admin Portal 页面、真实登录态/IAM、标准 JSON canonicalization 与结构化审批历史仍待后续环境与产品流程验证。
- 当前主题：intent-hub
- 说明：本文档记录意图中枢需求、设计、计划、审查主线状态。

## 需求索引

| 主题 | 需求文档 | 依赖 | 状态 |
| --- | --- | --- | --- |
| intent-hub | `docs/codex/v1/requirements/intent-hub-requirements.md` | 现有 designs 资料、首批业务场景、模型服务、配置与运维基线 | 已完成 |

## 进度与状态

| 主题 | Requirements | Design | Plan | Trace/Review | 整体状态 |
| --- | --- | --- | --- | --- | --- |
| intent-hub | 已完成 | P1 已设计，技术选型与 DDD 骨架已确认 | 已完成 | 已完成 | P1 有条件通过；P2-1 至 P2-5 已完成，P2-6/P2-7/P2-8 已形成本地阶段闭环，P2-9 至 P2-17 已完成最小配置发布治理、审批状态、GitOps 导出、评审工作台聚合契约、评审回退、审批快照哈希、hash 强字段化、发布 expected hash 条件校验、审批元数据强字段化与最小权限门禁；真实外部联调、真实观测栈试点、真实 GitOps PR 同步、前端页面、真实登录态/IAM 与完整审批流仍待后续验证 |

## 交付物

- 需求文档：`docs/codex/v1/requirements/intent-hub-requirements.md`
- 技术方案：`docs/codex/v1/designs/intent-hub-design.md`
- P0 契约与 DB Schema 设计：`docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md`
- P1 最小识别闭环设计：`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`
- 实施计划：`docs/codex/v1/plans/intent-hub-plan.md`
- P1 下一步执行计划：`docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`
- P2 下一步执行计划：`docs/codex/v1/plans/intent-hub-p2-next-step-plan.md`
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
- P2-7 多实例一致性与压测审查：`docs/codex/v1/trace/intent-hub-p2-multi-instance-consistency-trace.md`
- P2-8 观测告警本地试点审查：`docs/codex/v1/trace/intent-hub-p2-observability-pilot-trace.md`
- P2-9 配置发布治理增强审查：`docs/codex/v1/trace/intent-hub-p2-config-governance-trace.md`
- P2-10 配置审批与 GitOps 导出审查：`docs/codex/v1/trace/intent-hub-p2-config-approval-gitops-trace.md`
- P2-11 Admin 配置评审工作台聚合契约审查：`docs/codex/v1/trace/intent-hub-p2-admin-review-workspace-trace.md`
- P2-12 配置评审驳回与撤回审查：`docs/codex/v1/trace/intent-hub-p2-config-review-return-trace.md`
- P2-13 审批快照哈希审查：`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-trace.md`
- P2-14 审批快照哈希强字段化审查：`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-field-trace.md`
- P2-15 发布 expectedSnapshotHash 条件校验审查：`docs/codex/v1/trace/intent-hub-p2-publish-expected-snapshot-hash-trace.md`
- P2-16 审批元数据强字段化审查：`docs/codex/v1/trace/intent-hub-p2-approval-metadata-field-trace.md`
- P2-17 配置评审权限模型审查：`docs/codex/v1/trace/intent-hub-p2-config-review-permission-trace.md`
- P2-18 评审工作台按角色过滤动作审查：`docs/codex/v1/trace/intent-hub-p2-review-workspace-role-filter-trace.md`
- 模型服务 FastAPI 示例：`examples/model-service-fastapi/README.md`
- 模型服务容器化校验脚本：`scripts/validate-model-service-container.ps1`
- 模型策略 JDBC 冒烟脚本：`scripts/smoke-model-policy-jdbc.ps1`
- 运维样例总入口：`ops/README.md`
- 生产化落地检查清单：`ops/production-readiness-checklist.md`
- 观测告警试点接入计划：`ops/pilot-rollout-plan.md`
- 试点执行记录模板：`ops/pilot-execution-record-template.md`
- 本地试点执行记录：`ops/pilot-execution-record-local.md`
- 告警演练场景：`ops/alert-drill-scenarios.md`
- 本地观测栈预检脚本：`scripts/check-observability-local.ps1`
- 本地观测栈配置校验脚本：`scripts/validate-observability-compose.ps1`
- P2-8 本地观测告警 smoke：`scripts/smoke-observability-pilot-local.ps1`
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
- 2026-06-01：完成 P1-6 退出评审，结论为有条件通过（Conditionally Approved）；P1 已具备可运行、可配置、可回溯、可持久化的最小闭环，可进入 P2 规划与试点扩展。非阻塞遗留项包括指标采集、bad case 标注流转、动态 scene 路由、更细配置字段校验、真实模型服务和真实 LLM 小流量验证。
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
- 2026-06-04：补齐基础告警快照，新增 `MetricsAlertAppService` 与 `GET /api/v1/admin/metrics/alerts`，基于 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时返回 `OK/WARN/CRITICAL`；后续已补 P95/P99 长尾耗时告警。`mvn -pl intent-hub-interfaces -am test` 通过，共 62 个测试。
- 2026-06-04：补充 `ops/prometheus/intent-hub-alert-rules.yml` Prometheus/Alertmanager 告警规则样例，覆盖 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时、P95/P99 长尾耗时和最大耗时；该样例不改变运行时代码，不引入 Actuator/Micrometer/Alertmanager 依赖，生产化 scrape、route、Grafana dashboard 和 SLO 仍待后续补齐。
- 2026-06-04：补充 `ops/grafana/intent-hub-dashboard.json` Grafana 看板样例，覆盖请求量、bad case 率、耗时、P95/P99 长尾耗时、decision 分布、fallback、LLM 预算活动、intent 和 scene 分布；该样例依赖 Prometheus 已抓取 `/api/v1/admin/metrics/prometheus`，不提供生产化 datasource、folder/provisioning、权限和 SLO 配置。
- 2026-06-04：补充 `ops/prometheus/intent-hub-scrape-config.yml` 和 `ops/prometheus/README.md`，提供 Prometheus 抓取 `/api/v1/admin/metrics/prometheus` 的配置片段样例与接入说明；生产环境仍需补服务发现、TLS/鉴权、Alertmanager route、receiver、SLO 和多实例聚合策略。
- 2026-06-04：补充 `ops/slo/README.md` SLO 与错误预算样例，按可用性、延迟、质量、受控 LLM、预算补偿和 Bad Case 回流划分目标；该样例不是正式 SLA，生产落地前仍需结合租户等级、真实流量、成本预算和监管要求确认阈值。
- 2026-06-04：补充 `ops/alertmanager/alertmanager-route-sample.yml` 和 `ops/alertmanager/README.md`，提供 Alertmanager route、receiver 和 inhibit 样例；生产环境仍需替换真实接收地址，并补齐 TLS/鉴权、secret 管理、升级策略、静默策略和审计要求。
- 2026-06-04：补充 `ops/local-observability/` 本地观测栈样例，通过 Docker Compose 串联 Prometheus、Alertmanager 和 Grafana，用于本地验证 `/api/v1/admin/metrics/prometheus`、告警规则和 dashboard；该样例不是生产部署配置。
- 2026-06-04：补充 `ops/runbooks/intent-hub-alert-runbook.md`，覆盖 bad case 率高、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时高和最大耗时 critical 的影响判断、止血动作、定位步骤和复盘清单。
- 2026-06-04：补充 `ops/README.md` 运维样例总入口，将 Prometheus scrape、告警规则、Alertmanager 路由、Grafana 看板、SLO、本地观测栈和告警 Runbook 串成统一接入顺序，并明确生产化前必改项。
- 2026-06-04：补充 `ops/production-readiness-checklist.md` 生产化落地检查清单，将指标入口、Prometheus、Alertmanager、Grafana、SLO、安全合规、演练验收拆成可确认事项。
- 2026-06-04：补充 `ops/pilot-rollout-plan.md` 观测告警试点接入计划，按 dev/staging 试点环境拆解一周接入节奏、验收步骤、回滚策略和复盘模板。
- 2026-06-04：补充 `ops/pilot-execution-record-template.md` 试点执行记录模板，用于真实试点时统一记录 metrics、scrape、rule、route、dashboard、告警演练、复盘和附件证据。
- 2026-06-04：补充 `ops/alert-drill-scenarios.md` 告警演练场景，覆盖 6 条 P2.x 告警的推荐触发方式、验证点、恢复方式和禁止动作。
- 2026-06-04：补充 `scripts/check-observability-local.ps1` 本地观测栈预检脚本，用于检查本地 ops 配置文件、Intent Hub health/metrics endpoint 和 Docker 命令。
- 2026-06-04：补充 `scripts/validate-observability-compose.ps1` 本地观测栈配置校验脚本，用于不启动容器校验 Docker Compose、Prometheus rule 引用、target 和 Grafana provisioning 引用。
- 2026-06-04：补充 `examples/model-service-fastapi` 容器化配置样例和 `scripts/validate-model-service-container.ps1` 校验脚本，用于后续模型服务部署化联调前验证 Dockerfile、Compose、端口映射和健康检查。
- 2026-06-08：完成模型服务容器与 Intent Hub jar 本地端到端联调，并修复 Spring AI Alibaba optional 依赖未进入运行包时的启动失败；`GET /api/v1/admin/health` 已验证 `model_service.healthy=true`，识别路径已验证进入 `ModelRecognitionPolicy`。
- 2026-06-08：补充 `scripts/smoke-model-service-e2e.ps1`，将模型服务容器 + Intent Hub jar 端到端联调固化为一键 smoke，已验证自动打包、启动、识别断言和清理流程。
- 2026-06-08：扩展 FastAPI 模型服务示例，补充 `modelVersion`、`threshold` 与退款、物流、发票等多意图样本；Java adapter 核心消费字段保持不变，端到端 smoke 已增加模型版本断言。
- 2026-06-08：增强模型服务健康详情，`GET /api/v1/admin/health` 可透出 `model_service.modelVersion` 与 `threshold`；该信息仅用于观测和 smoke 断言，不进入识别候选或下游业务动作。
- 2026-06-08：补齐 scene 级 `model_policy` 最小治理闭环，已支持从已发布 `nlu_strategy.model_policy` 读取模型参与开关、endpoint、timeout 与最低置信度；运行时已按 `enabled/minConfidence` 控制模型候选，并支持按 scene 策略动态覆盖模型服务 endpoint/timeout。
- 2026-06-08：修复 Admin 策略对象规范化遗漏 `modelPolicy` 的问题，避免 HTTP Admin upsert 时写入 `{}`；新增 `scripts/smoke-model-policy-jdbc.ps1`，已通过真实 PostgreSQL 16 空库验证 Flyway V1/V2/V3、`nlu_strategy.model_policy` 字段、Admin `modelPolicy` 写入/查询、发布配置读取和 `MODEL_POLICY:DISABLED` 识别路径。
- 2026-06-08：补齐 P95/P99 长尾耗时指标与告警，`MetricsSnapshot`、Prometheus 文本、`/api/v1/admin/metrics/alerts`、Prometheus 规则、Grafana 看板和 Runbook 均已同步；相关模块测试通过，共 66 个测试。
- 2026-06-08：补齐配置版本审计查询闭环，新增 `AuditLogEntry`、`ConfigAuditAppService` 与 `GET /api/v1/admin/config/versions/{version}/audits`；memory/JDBC 审计仓储均支持按 `tenantId + sceneId + version` 倒序查询，JDBC 覆盖 `audit_log.detail` JSON 解析。相关模块测试通过，共 68 个测试。
- 2026-06-08：补齐配置对象删除与批量导入闭环，新增 `POST /api/v1/admin/config/versions/{version}/{objectType}/bulk` 与 `DELETE /api/v1/admin/config/versions/{version}/{objectType}/{objectId}`；保留仅 `DRAFT` 可写约束，并记录 `CONFIG_OBJECT_BULK_UPSERTED` 与 `CONFIG_OBJECT_DELETED` 审计动作。相关模块测试通过，共 71 个测试。
- 2026-06-08：补齐配置字段基础校验，`ConfigObjectAppService.normalize` 已拦截 `confidenceThreshold`、`modelPolicy.minConfidence`、`routeStage`、`actionType`、`timeoutMs`、`llmPolicy.timeoutMs/maxRetries/dailyBudget` 等高风险字段边界；单条 upsert 与批量 upsert 复用同一校验入口。应用层相关测试通过，共 17 个测试。
- 2026-06-08：补齐发布前跨对象引用校验，`ConfigVersionAppService.validate` 已检查 slot 所属 intent、POST route 下游动作、downstream action 反推 intent 的最小引用完整性，`publish` 复用 validate 结果阻断破损配置包。应用层相关测试通过，共 18 个测试。
- 2026-06-08：补齐 `scene_routing_rule.match_condition` 最小后置路由条件解析，新增 `PostRouteRule` 并让 `SceneConfig.actionFor(RecognitionCandidate)` 按候选意图、最低置信度和槽位等值条件选择 downstream action；JDBC 已按 `priority asc, id asc` 读取 POST 规则，支持 `intentCode`/`intent_code`、`minConfidence`/`min_confidence`、`slots`/`slotEquals`/`slotConditions`，并保留未命中时按 intent 默认动作回退。相关模块测试通过，共 75 个测试。
- 2026-06-08：补齐下游动作显式 intent 归属读取，Admin 下游动作对象支持顶层 `intentCode` 写入 `actionSchema.intentCode`，发布前校验优先使用显式 intent 引用，JDBC 已发布配置读取优先按 `downstream_action.action_schema.intentCode` 建立 intent -> action 映射；旧配置继续保留按 `ACTION_CODE` 后缀推断的兼容路径。应用层与基础设施层测试通过，共 60 个测试。

- 2026-06-08：补齐 scene 级模型 endpoint/timeout 动态路由，`ModelRecognitionPolicy` 已将 `ModelPolicy` 传给模型端口，`HttpModelClientAdapter` 可优先使用 `modelPolicy.endpoint/timeoutMs` 构建请求客户端，并按 endpoint + timeout 缓存复用场景客户端；全局 `enabled=false` 仍保持 no-op，未配置 scene endpoint 时继续使用全局 base-url。应用层、基础设施层与接口层关联测试通过，共 83 个测试。
- 2026-06-08：补齐模型服务 token 引用鉴权，`modelPolicy.authTokenRef` 只保存环境变量或系统属性引用名，运行时解析后向 scene 模型服务请求注入 Bearer 鉴权头；不在 DB、文档或仓库保存明文 token。应用层与基础设施层测试通过，共 65 个测试。
- 2026-06-08：补齐模型服务 token 引用缺失失败关闭，`authTokenRef` 已配置但系统属性/环境变量无法解析时不再发无鉴权请求，识别路径记录 `MODEL_FALLBACK:AUTH_MISSING_TOKEN` 并进入 bad case/模型 fallback 指标口径。应用层与基础设施层测试通过，共 67 个测试。
- 2026-06-09：新增 P2 下一步执行计划，建议按 P2-6 密钥治理与外部联调准入、P2-7 多实例一致性与压测、P2-8 观测告警真实试点、P2-9 配置发布治理增强推进；下一步最小动作优先落地 Secret 解析端口与默认 env/system property 实现。
- 2026-06-09：启动 P2-6 密钥治理与外部联调准入，新增 `SecretRefResolver` 和默认 `EnvironmentSecretRefResolver`，模型服务 adapter 已改为通过统一 resolver 解析 `authTokenRef`，LLM adapter 已接入同一 resolver 作为 DashScope/Provider 凭证治理预留；基础设施层相关测试通过，共 47 个测试。Vault/K8s Secret、真实带鉴权模型服务和真实 DashScope 沙箱外呼仍待后续联调。
- 2026-06-09：补齐 P2-6 外部联调前预检脚本 `scripts/preflight-external-integration.ps1`，可检查 Intent Hub health、模型服务 health、模型服务 `authTokenRef` 与 DashScope Secret 引用存在性，且不打印密钥值；该脚本不发识别请求、不调用 LLM，不代表真实带鉴权模型服务 smoke 或 DashScope 外呼已完成。
- 2026-06-09：补齐 P2-6 文件挂载 Secret resolver 预留，新增 `CompositeSecretRefResolver` 与 `FileSecretRefResolver`，支持通过 `intent-hub.secret.file-root` 读取外部挂载 Secret 文件，并拒绝路径穿越；该形态可承接 K8s Secret/Vault Agent 文件挂载，但 Vault SDK、Nacos 加密配置、轮换审计和真实外部 smoke 仍待后续落地。
- 2026-06-09：完成 P2-6 本地带鉴权模型服务 smoke，`scripts/smoke-model-service-e2e.ps1 -WithAuth` 会启动临时 PostgreSQL 16、FastAPI 模型服务容器和 Intent Hub `local-jdbc`，验证无 token 直连模型被 401 拒绝、preflight 不打印密钥值、已发布 `modelPolicy.authTokenRef` 配置可被识别链路读取，并通过 Bearer token 返回 `ORDER_CANCEL/ASYNC_ACCEPTED` 与 `ModelRecognitionPolicy` 路径。真实远端模型服务、DashScope 沙箱外呼和生产级 Secret 权限/轮换/审计仍待后续完成。
- 2026-06-09：固化 P2-6 外部联调冒烟记录模板 `ops/external-integration-smoke-record-template.md`，用于真实模型服务和 DashScope/LLM 联调时统一留存 Secret 引用、preflight、鉴权、trace、预算、指标和安全复核证据；同步更新运维入口、生产化清单和 P2 计划，明确 preflight 与本地 smoke 不等同于真实远端联调完成。
- 2026-06-09：补齐 P2-6 managed-config Secret resolver，支持通过 `intent-hub.secret.managed-config.enabled=true` 与 `intent-hub.secret.managed-config.refs.*` 读取外部托管配置注入的 Secret 映射；该能力可承接 Nacos/Apollo/Spring Config 等平台完成解密后的运行时配置，但不替代 Vault SDK、权限模型、轮换审计和真实外部 smoke。
- 2026-06-09：补齐 P2-6 模型服务 Secret 轮换感知，scene 级模型客户端缓存 key 不再保存明文 token，而是使用 `authTokenRef + token fingerprint`；同一路由 token 解析值变化时会清理旧客户端并重建，避免旧 Bearer 鉴权头长期驻留。基础设施层相关测试通过，共 59 个测试。
- 2026-06-09：新增并验证 `scripts/smoke-secret-rotation.ps1`，通过临时 PostgreSQL 16、文件挂载 Secret、FastAPI 模型服务容器和 Intent Hub `local-jdbc` 完成本地 Secret 轮换演练：初始 token 可识别，改写挂载文件后旧 token 直连被 401 拒绝、新 token 直连通过，Intent Hub 第二次识别仍进入 `ModelRecognitionPolicy`；脚本结束后已清理容器、进程和临时 Secret 文件。

## 2026-06-09 P2-7 多实例一致性更新

- 当前进展：P2-7 本地四段闭环已完成，本地双实例一致性 smoke、LLM 日预算多实例 smoke、LLM stale pending 后台补偿 smoke 与基础双实例并发压测已通过。
- 新增交付物：`scripts/smoke-multi-instance-consistency.ps1`、`scripts/smoke-llm-budget-multi-instance.ps1`、`scripts/smoke-llm-budget-reconciliation-multi-instance.ps1`、`scripts/stress-multi-instance-basic.ps1`。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-multi-instance-consistency-trace.md`。
- 验证证据：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-multi-instance-consistency.ps1 -SkipPackage` 通过；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-multi-instance.ps1 -SkipPackage` 通过；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-reconciliation-multi-instance.ps1 -SkipPackage` 通过；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stress-multi-instance-basic.ps1 -SkipPackage` 通过。
- 验证结论：同库双实例下，实例 A 发布配置后，实例 A/B 对同一个异步请求均返回 `ORDER_CANCEL/ASYNC_ACCEPTED` 和同一个非空 `idempotencyKey`；`idempotency_record` 仅 1 条共享记录，`recognition_trace` 有 2 条请求记录。LLM budget smoke 中，两个实例共享 PostgreSQL 与本地 mock LLM，`dailyBudget=2` 下 6 个并发请求只有 2 个成功命中 LLM，其余进入 `LLM_FALLBACK:REJECTED`；管理端预算 reserved 不超过日预算且派生 pending 为 0。LLM reconciliation smoke 中，两个实例同时开启补偿 scheduler 后只发生 1 次补偿，预算行从 stale pending 校正到 provider confirmed 用量，provider 外呼审计不被回滚。基础 stress 中，40 个并发请求输出 32 成功、8 拒识、0 fallback、0 error，平均 246.48ms、P95 319ms、P99 471ms，数据库 `recognition_trace=40`、`bad_case=8`。
- 后续重点：可进入 P2-8 观测告警真实试点，或扩展 P2-7 到模型服务异常/缺 token fallback 组合压测。

## 2026-06-09 P2-8 观测告警本地试点更新

- 当前进展：P2-8 本地可重复试点已完成，真实 Prometheus/Alertmanager/Grafana dev/staging 接入仍待后续环境验证。
- 新增交付物：`scripts/smoke-observability-pilot-local.ps1`、`ops/pilot-execution-record-local.md`。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-observability-pilot-trace.md`。
- 验证证据：PowerShell Parser 检查通过；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-observability-pilot-local.ps1 -SkipPackage` 通过。
- 验证结论：本地单实例 memory 模式健康检查返回 `UP`；10 条本地流量形成 6 条 `SUCCESS`、4 条 `REJECTED`/bad case；`/api/v1/admin/metrics` 返回 `totalRequests=10` 与 `totalBadCases=4`；Prometheus 文本包含 `intent_hub_requests_total` 与 `intent_hub_bad_cases_total`；`/api/v1/admin/metrics/alerts` 返回 `WARN` 且包含 `BAD_CASE_RATE_HIGH`；执行记录已生成。
- 关键修复：PowerShell 请求体改为 UTF-8 bytes 发送，并用 Unicode 码点构造“查一下订单”样本文本，避免 Windows 默认编码导致中文规则关键词无法命中。
- 后续重点：在 dev/staging 环境接入真实 Prometheus target、Alertmanager receiver 与 Grafana dashboard，并按 `ops/pilot-execution-record-template.md` 留存真实试点证据；如果暂时没有真实观测栈环境，可进入 P2-9 配置发布治理增强。

## 2026-06-09 P2-9 配置发布治理增强更新

- 当前进展：P2-9 最小配置发布治理闭环已完成，审批状态流转与真实 GitOps PR 同步仍为后续预留。
- 新增交付物：`ConfigDiffEntry`、`ConfigDiffResult`、`ConfigDryRunReport`、配置版本 diff 服务、发布前 dry-run 服务、Admin `/diff` 与 `/dry-run` API。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-config-governance-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-interfaces -am test` 通过。
- 验证结论：配置版本 diff 可按业务标识识别 `ADDED/MODIFIED/REMOVED`，dry-run 可复用发布前跨对象引用校验、返回可发布状态、差异报告与 GitOps 文件结构建议；现有发布和回滚接口保持兼容。
- 后续重点：补 Admin Portal diff 可视化、审批状态机、dry-run 报告审计快照、GitOps 导出/PR 流程，以及发布前乐观锁或版本状态保护。

## 2026-06-09 P2-10 配置审批与 GitOps 导出更新

- 当前进展：P2-10 最小审批状态机与 GitOps 审查包导出闭环已完成，完整 Git PR 同步、驳回/撤回、多人审批和审批快照哈希仍为后续预留。
- 新增交付物：`ConfigVersionPort.updateStatus(...)`、`ConfigGitOpsExport`、`submitReview`、`approve`、`exportGitOps` 服务能力，以及 Admin `submit-review/approve/gitops` API。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-config-approval-gitops-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：配置版本可从 `DRAFT` 提交到 `REVIEWING`，`REVIEWING` 不能直接发布，必须先 `APPROVED`；GitOps 导出包含配置对象文件内容和 `dry-run.json`；历史 DRAFT 直接发布仍保持兼容。
- 后续重点：补 Admin Portal diff + dry-run + approve 页面、`rejectReview/cancelReview`、审批意见与快照哈希、真实 GitOps 目录/PR 流程和 publish 乐观锁。

## 2026-06-09 P2-11 Admin 配置评审工作台聚合契约更新

- 当前进展：P2-11 页面数据聚合契约已完成；当前仓库没有独立前端工程或模板页面，因此本阶段不交付完整 UI。
- 新增交付物：`ConfigReviewWorkspace`、`ConfigReviewWorkspaceAppService`、Admin `GET /api/v1/admin/config/versions/{version}/review-workspace`。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-admin-review-workspace-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：工作台接口可一次返回版本状态、校验、dry-run/diff、审计、可用动作和阻断原因；DRAFT 可见 `SUBMIT_REVIEW/PUBLISH_COMPAT`，REVIEWING 可见 `APPROVE` 且不直接暴露 `PUBLISH`。
- 后续重点：建立 Admin Portal 前端页面、补用户权限与审批权限模型、增加字段级 diff/分页、补驳回/撤回与审批快照哈希。

## 2026-06-09 P2-12 配置评审驳回与撤回更新

- 当前进展：P2-12 评审回退最小闭环已完成，审批快照哈希、结构化 review history、权限模型和多人审批仍为后续预留。
- 新增交付物：`ConfigVersionAppService.rejectReview(...)`、`ConfigVersionAppService.cancelReview(...)`、`ConfigVersionActionRequest.reason`、Admin `reject-review/cancel-review` API，以及工作台 `REJECT_REVIEW/CANCEL_REVIEW/CANCEL_APPROVAL` 动作。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-config-review-return-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：`REVIEWING` 可驳回回 `DRAFT`，`REVIEWING/APPROVED` 可撤回回 `DRAFT`；回退后复用既有“仅 DRAFT 可编辑”约束；驳回和撤回均写入审计 detail。
- 后续重点：补审批快照哈希、review comment/history 查询、审批权限模型，以及前端 Admin Portal 操作页。

## 2026-06-09 P2-13 审批快照哈希更新

- 当前进展：P2-13 审批快照哈希最小闭环已完成，标准 JSON canonicalization、`config_version` 强字段和工作台显式 hash 字段仍为后续预留。
- 新增交付物：`CONFIG_APPROVED.detail.snapshotHash`，以及 `APPROVED` 发布前的配置快照漂移校验。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：批准时会记录配置包 SHA-256 快照哈希；批准后如配置内容漂移，发布会被 `approved config snapshot has changed` 阻断；接口层可从审计 detail 看到 `snapshotHash`。
- 后续重点：将 hash 固化到 `config_version` 字段、在 `review-workspace` 显式返回当前/批准 hash、补 publish expected hash 条件发布和权限模型。

## 2026-06-09 P2-14 审批快照哈希强字段化更新

- 当前进展：P2-14 审批快照哈希强字段化已完成，`config_version.approved_snapshot_hash`、版本详情 hash 字段和工作台 hash 字段均已落地。
- 新增交付物：Flyway `V4__p2_config_approval_snapshot_hash.sql`、`ConfigVersionInfo.approvedSnapshotHash/currentSnapshotHash`、`ConfigVersionPort.updateApprovedSnapshotHash(...)`，以及 `APPROVED` 发布优先读取强字段的漂移校验。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-field-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：批准时会写入 `approved_snapshot_hash` 强字段；版本详情与 review-workspace 可直接返回 `approvedSnapshotHash/currentSnapshotHash`；旧数据仍可回退读取 `CONFIG_APPROVED.detail.snapshotHash`，批准后配置漂移仍会阻断发布。
- 后续重点：补 publish `expectedSnapshotHash` 条件发布、`approved_by/approved_at` 强字段、审批权限模型和结构化 review history。

## 2026-06-09 P2-15 发布 expectedSnapshotHash 条件校验更新

- 当前进展：P2-15 发布 expectedSnapshotHash 条件校验已完成，Admin/API 调用方可携带工作台读取到的 `currentSnapshotHash` 做发布前二次确认。
- 新增交付物：`ConfigVersionAppService.publish(..., expectedSnapshotHash)`、`ConfigVersionActionRequest.expectedSnapshotHash`、Admin publish 请求体透传。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-publish-expected-snapshot-hash-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：错误 expected hash 会阻断发布；正确 current hash 可发布成功；旧 publish 调用保持兼容，APPROVED 发布仍保留批准快照漂移校验。
- 后续重点：Admin Portal 发布按钮强制携带 `currentSnapshotHash`，补接口示例、`approved_by/approved_at` 强字段、审批权限模型和结构化 review history。

## 2026-06-09 P2-16 审批元数据强字段化更新

- 当前进展：P2-16 审批元数据强字段化已完成，`config_version.approved_by/approved_at`、版本详情和工作台审批元数据均已落地。
- 新增交付物：Flyway `V5__p2_config_approval_metadata.sql`、`ConfigVersionInfo.approvedBy/approvedAt`、memory/JDBC 审批元数据读写。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-approval-metadata-field-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：approve 后版本详情与 review-workspace 可直接返回审批人、审批时间和批准 hash；旧构造器保持兼容。
- 后续重点：审批权限模型、结构化 review history、撤回审批元数据清理语义和 Admin Portal 审批信息展示。

## 2026-06-09 P2-17 配置评审权限模型更新

- 当前进展：P2-17 配置评审权限模型最小闭环已完成，Admin 配置评审动作已具备角色门禁。
- 新增交付物：`ConfigVersionActionRequest.roles`、应用层 `CONFIG_APPROVER/CONFIG_PUBLISHER` 门禁、Admin 评审动作角色透传。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-config-review-permission-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：approve/reject/cancel 要求 `CONFIG_APPROVER`，publish 要求 `CONFIG_PUBLISHER`；错误角色被阻断，正确角色可通过；内部兼容调用保持可用。
- 后续重点：真实登录态/IAM 接入、review-workspace 按角色过滤动作、统一 403 响应和 tenant/scene 级权限。

## 2026-06-09 P2-18 评审工作台按角色过滤动作更新

- 当前进展：P2-18 评审工作台按角色过滤动作最小闭环已完成，Admin 工作台数据面已能按调用方角色隐藏无权限受控动作。
- 新增交付物：`ConfigReviewWorkspaceAppService.getWorkspace(..., roles)`、Admin `review-workspace` roles 查询参数、受控动作角色过滤、权限阻断原因返回。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-review-workspace-role-filter-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：应用层 32 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 114 个测试；Admin 缺省 roles 不展示发布/审批受控动作，`CONFIG_APPROVER` 与 `CONFIG_PUBLISHER` 分别控制审批/撤回和发布动作可见性。
- 后续重点：真实登录态/IAM 接入、统一 403 响应、tenant/scene 级权限、`SUBMIT_REVIEW/ROLLBACK_TARGET` 更细授权和结构化 review history。

## 2026-06-09 P2-19 统一 403 响应更新

- 当前进展：P2-19 统一 403 响应最小闭环已完成，Admin 配置评审权限失败已从应用层 `SecurityException` 映射为结构化 HTTP 403 JSON。
- 新增交付物：`ApiErrorResponse`、`GlobalExceptionHandler`、接口层权限失败响应测试。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-forbidden-error-response-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：应用层 32 个测试、基础设施层 59 个测试、接口层 24 个测试，合计 115 个测试；错误角色调用 approve 返回 HTTP 403，响应包含 `code=FORBIDDEN`、`status=403` 和权限失败 message。
- 后续重点：真实登录态/IAM 角色来源、tenant/scene 级权限、更多领域异常统一错误响应、错误响应 `traceId/requestId` 和结构化 review history。

## 2026-06-09 P2-20 Admin 请求上下文角色来源更新

- 当前进展：P2-20 Admin 请求上下文角色来源最小闭环已完成，配置评审动作和工作台数据面已能优先使用网关/IAM 注入式 header。
- 新增交付物：`AdminRequestContext`、`X-IntentHub-Actor` / `X-IntentHub-Roles` 解析、动作接口与 `review-workspace` header 优先逻辑。
- 新增审查文档：`docs/codex/v1/trace/intent-hub-p2-admin-request-context-trace.md`。
- 验证证据：`mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test` 通过。
- 验证结论：应用层 32 个测试、基础设施层 59 个测试、接口层 26 个测试，合计 117 个测试；header actor/roles 可覆盖请求体/query，旧兼容路径仍保留。
- 后续重点：tenant/scene 级权限、Spring Security/JWT Filter、header 可信边界、更多领域异常统一错误响应和结构化 review history。

# P2-5 LLM 受控兜底

## 恢复胶囊

- 任务需求：继续 P2，完成小流量 LLM 受控兜底的最小闭环。
- 关键决策：LLM 仍是最后一道防线；默认关闭且预算为 0；只有全局治理配置和 scene 级 `llm_policy` 同时允许时才触发；失败按 fallback decision 关闭。
- 当前阶段：P2.x 日预算原子预占、同步失败释放、后台补偿、基础告警快照、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例与告警 Runbook 已完成；本轮在既有 LLM 受控兜底基础上补齐外呼前日预算原子预占、管理端 confirmed/reserved/pending 预算查询、远端失败释放本次预占、默认关闭的 stale pending 后台补偿、`GET /api/v1/admin/metrics/alerts` 基础告警快照、`ops/prometheus/intent-hub-scrape-config.yml` scrape 样例、`ops/prometheus/intent-hub-alert-rules.yml` 告警规则样例、`ops/alertmanager/alertmanager-route-sample.yml` 路由样例、`ops/grafana/intent-hub-dashboard.json` 看板样例、`ops/slo/README.md` SLO 样例、`ops/local-observability` 本地观测栈样例和 `ops/runbooks/intent-hub-alert-runbook.md` 告警 Runbook，相关模块测试通过，待用户明确指令后推送 GitHub。
- 已完成产物：LLM 领域策略门禁、基础设施治理配置、Spring AI Alibaba 优先/HTTP fallback adapter、LLM 预算审计端口与 memory/JDBC 实现、同步失败释放、后台补偿调度、基础告警快照、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例、告警 Runbook、DashScope smoke profile/script、JDBC 策略读取、测试、README/status/HTML/trace 同步。
- 剩余工作：GitHub 推送只在用户明确发指令时执行。
- 重要发现：当前 Spring AI Alibaba 依赖已作为 optional 存在；P2-5 已在基础设施层预接入 `ChatClient`，同时保留 HTTP 契约 fallback，真实 DashScope 沙箱冒烟仍需凭证。

## 步骤列表

- [v] 建立 P2-5 任务记录。
- [v] 实现 LLM 治理门禁和 adapter。
  - 当前产物：`LlmGovernanceProperties`、`TongyiLlmAdapter`、`LlmRecognitionRequest`、`LlmRecognitionResponse`。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-domain`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补已发布 `llm_policy` 读取。
  - 当前产物：`JdbcSceneConfigRepository` 从 `nlu_strategy.llm_policy` 读取策略。
  - 下一步：已完成。
- [v] 补测试。
  - 当前产物：`mvn test` 已通过，共 61 个测试；`mvn package -DskipTests` 已通过。
  - 下一步：已完成。
- [v] 同步 README/status/HTML/trace。
- [v] 提交。
  - 当前产物：本地变更已验证，按用户要求使用中文 commit 说明提交。
  - 下一步：推送 GitHub 需用户单独指令。
- [v] 绑定模型服务与 LLM HTTP timeout。
  - 当前产物：`ExternalRestClients` 使用 `SimpleClientHttpRequestFactory` 将 `timeout-ms` 绑定到 connect/read timeout，模型服务和 LLM adapter 均已接入。
  - 下一步：已完成。
- [v] 补模型服务异常失败关闭。
  - 当前产物：`ModelRecognitionPolicy` 捕获模型服务异常，记录 `MODEL_FALLBACK:CLOSED` 并返回空候选，避免外部模型不可用导致识别入口 500。
  - 下一步：已完成。
- [v] 补模型与 LLM fallback 最小指标。
  - 当前产物：`MetricsSnapshot` 新增 `totalModelFallbacks`，Prometheus 导出 `intent_hub_model_fallbacks_total` 和 `intent_hub_llm_fallbacks_total`；内存指标分别按 `MODEL_FALLBACK` 与 `LLM_FALLBACK` 计数。
  - 下一步：已完成。
- [v] 补 LLM 预算消费最小计数。
  - 当前产物：`IntentMetricsPort.recordLlmBudgetConsumption`、`MetricsSnapshot.totalLlmBudgetAttempts/totalLlmBudgetConsumed` 和 Prometheus `intent_hub_llm_budget_*` 指标；`TongyiLlmAdapter` 仅在真实外呼尝试前记账。
  - 下一步：已完成；持久化审计、日预算原子预占、同步失败释放和后台补偿已补，后续再做告警和真实多实例压测。
- [v] 补 LLM 预算持久化审计。
  - 当前产物：`LlmBudgetAuditPort`、`LlmBudgetUsage`、`InMemoryLlmBudgetAuditRepository`、`JdbcLlmBudgetAuditRepository` 和 Flyway `V2__p2_llm_budget_usage.sql`；按 `tenant_id + scene_id + usage_date + provider + model` 记录外呼尝试次数和消费单位。
  - 下一步：已完成；日预算原子预占、同步失败释放、后台补偿和管理端 confirmed/reserved/pending 查询已补，后续做告警和真实多实例压测。
- [v] 补模型 adapter 本地 HTTP 冒烟。
  - 当前产物：`ModelClientAdapterTest` 使用 JDK `HttpServer` 验证 `HttpModelClientAdapter` 真实 POST、请求体和 JSON 响应解析。
  - 下一步：已完成；FastAPI 示例工程和模型服务健康检查已补。
- [v] 补 FastAPI 模型服务示例。
  - 当前产物：`examples/model-service-fastapi` 提供 `/health` 与 `/recognize`，本地 uvicorn 冒烟通过。
  - 下一步：已完成；后续扩展样本、阈值和模型版本示例。
- [v] 补模型服务健康检查接入。
  - 当前产物：`ModelClientPort.healthy()`、`HttpModelClientAdapter` 的 `GET /health` 检查和 `AdminHealthController` 的 `model_service.healthy` 输出。
  - 下一步：已完成；本地真实联调已补，后续做部署化模型服务联调。
- [v] 补本地真实模型服务联调。
  - 当前产物：显式注册 `RestClient.Builder` Bean，修复 jar 启动缺口；启动 `examples/model-service-fastapi` 与 Intent Hub jar，验证 `model_service.healthy=true` 和 `cancel A100` 命中 `ModelRecognitionPolicy`。
  - 下一步：已完成；后续做容器/环境级联调。
- [v] 预接入 Spring AI Alibaba ChatClient。
  - 当前产物：`TongyiLlmAdapter` 在 provider 为 `spring-ai-alibaba` 且存在 `ChatClient.Builder` 时优先走 Spring AI `ChatClient`，否则保留 HTTP 契约 fallback；新增测试覆盖两个分支。
  - 下一步：已完成；后续接入真实 DashScope 沙箱密钥做小流量冒烟。
- [v] 准备 DashScope 沙箱冒烟脚本。
  - 当前产物：`dashscope-smoke` profile 读取 `DASHSCOPE_API_KEY`/`DASHSCOPE_CHAT_MODEL`，`scripts/dashscope-smoke.ps1` 自动发布带 LLM policy 的 smoke scene 并触发识别请求。
  - 下一步：已完成；真实外呼等待沙箱凭证和用户明确执行指令。
- [v] 补 LLM 预算后台补偿。
  - 当前产物：`LlmBudgetAuditPort.reconcileStaleDailyBudgetReservations`、memory/JDBC stale pending 补偿实现、默认关闭的 `LlmBudgetReconciliationTask` 调度和配置。
  - 下一步：已完成；基础告警快照已补，后续补 Micrometer/OpenTelemetry 桥接和真实多实例 PostgreSQL 压测。
- [v] 补基础告警快照。
  - 当前产物：`MetricsAlertAppService`、`MetricsAlertSnapshot` 和 `GET /api/v1/admin/metrics/alerts`，基于 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时返回 `OK/WARN/CRITICAL`。
  - 下一步：已完成；后续桥接 Prometheus Alertmanager/Grafana Alerting。
- [v] 补 Prometheus 告警规则样例。
  - 当前产物：`ops/prometheus/intent-hub-alert-rules.yml`，覆盖 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时。
  - 下一步：已完成；后续补真实 scrape 配置、Alertmanager 路由和 Grafana dashboard。
- [v] 补 Prometheus scrape 配置片段样例。
  - 当前产物：`ops/prometheus/intent-hub-scrape-config.yml` 和 `ops/prometheus/README.md`，说明如何抓取 `/api/v1/admin/metrics/prometheus`。
  - 下一步：已完成；后续补真实服务发现、TLS/鉴权、Alertmanager route、receiver 和 SLO。
- [v] 补 Grafana 看板样例。
  - 当前产物：`ops/grafana/intent-hub-dashboard.json`，覆盖请求量、bad case 率、耗时、decision 分布、fallback、LLM 预算活动、intent 和 scene 分布。
  - 下一步：已完成；后续补真实 datasource/provisioning、folder、权限、SLO 和窗口化速率面板。
- [v] 补 SLO 与错误预算样例。
  - 当前产物：`ops/slo/README.md`，按可用性、延迟、质量、受控 LLM、预算补偿和 Bad Case 回流划分目标。
  - 下一步：已完成；后续结合真实业务等级、流量和监管要求确认正式 SLA。
- [v] 补 Alertmanager 路由样例。
  - 当前产物：`ops/alertmanager/alertmanager-route-sample.yml` 和 `ops/alertmanager/README.md`，按 critical/warning 分流并提供 receiver/inhibit 样例。
  - 下一步：已完成；后续结合真实值班通道、secret、TLS/鉴权和升级策略落地。
- [v] 补本地观测栈样例。
  - 当前产物：`ops/local-observability`，通过 Docker Compose 串联 Prometheus、Alertmanager 和 Grafana。
  - 下一步：已完成；后续可在本机启动 Intent Hub 后进行真实 compose 冒烟。
- [v] 补告警 Runbook。
  - 当前产物：`ops/runbooks/intent-hub-alert-runbook.md`，覆盖当前 6 条 P2.x Prometheus 告警的影响判断、止血、定位和复盘步骤。
  - 下一步：已完成；后续可结合真实值班演练调整升级策略。

## 研究发现

- LLM 门禁应在领域策略层先拦截预算为 0 或 timeout 为 0 的场景，避免指标误算 LLM fallback。
- 基础设施 adapter 仍需二次检查全局治理开关、baseUrl 和全局预算，防止误配置导致外部调用。
- 当前 P2-5 还不是完整生产 LLM：已预接入 Spring AI Alibaba `ChatClient`，但没有真实 DashScope 沙箱冒烟、生产化 Prometheus/Grafana 告警和真实多实例压测；底层 HTTP timeout 绑定、最小预算消费计数、持久化审计、日预算原子预占、同步失败释放、后台补偿、基础告警快照、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例、告警 Runbook 和管理端 confirmed/reserved/pending 查询已完成。
- 2026-06-02：已补齐底层 HTTP timeout 绑定；`mvn test` 通过，共 37 个测试；`git diff --check` 通过。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补齐模型服务异常失败关闭；`mvn test` 通过，共 38 个测试；`git diff --check` 通过。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补齐模型与 LLM fallback 最小指标口径；`mvn test` 通过，共 39 个测试；`git diff --check` 通过。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补齐 LLM 预算消费最小计数；`mvn test` 通过，共 39 个测试。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补齐模型 adapter 本地 HTTP server 冒烟；`mvn test` 通过，共 40 个测试。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补齐模型服务健康检查接入；当前复验 `mvn test` 通过，共 45 个测试。本轮按用户要求暂不提交、不推送。
- 2026-06-02：本地真实模型服务联调时发现 jar 启动缺少 `RestClient.Builder` Bean，已在 `IntentHubBeanConfiguration` 显式注册并补 `IntentHubBeanConfigurationTest`；当前复验 `mvn test` 通过，共 45 个测试；`mvn package -DskipTests` 通过；FastAPI + jar 联调通过。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已预接入 Spring AI Alibaba `ChatClient` 分支，并保留 HTTP 契约 fallback；`mvn -pl intent-hub-infrastructure -am test` 通过，相关模块共 29 个测试。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补 DashScope 沙箱冒烟 profile 与 `scripts/dashscope-smoke.ps1`；凭证只读取环境变量，不写入仓库。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已修复 `scripts/dashscope-smoke.ps1` 的 PowerShell 语法问题，改用 ASCII 冒烟输入避免 Windows 终端编码破坏；`mvn -pl intent-hub-interfaces -am test` 通过，共 45 个测试。本轮按用户要求暂不提交、不推送。
- 2026-06-02：已补 LLM 预算持久化审计，新增 memory/JDBC 预算审计实现和 `llm_budget_usage` Flyway 表；`mvn -pl intent-hub-infrastructure -am test` 通过，相关模块共 31 个测试。本轮按用户要求暂不提交、不推送。

- 2026-06-03：补齐外呼前日预算最小门禁与管理端预算查询，新增 `LlmBudgetAppService` 和 `AdminLlmBudgetController`；`TongyiLlmAdapter` 以全局预算与 scene 预算较小值作为有效预算，达到当日用量后直接返回空候选，不记录额外外呼尝试。`mvn -pl intent-hub-infrastructure,intent-hub-interfaces -am test` 通过，相关模块共 50 个测试。

- 2026-06-03：将 LLM 日预算门禁从“查询后记账”升级为外呼前原子预占：`LlmBudgetAuditPort.tryReserveDailyBudget` 负责按有效日预算预占；memory 实现用同步计数保护，JDBC 实现用预算保留行、唯一键和条件 update 控制超额；日用量查询排除预算保留行，避免预占与明细审计双算。`mvn -pl intent-hub-infrastructure,intent-hub-interfaces -am test` 通过，相关模块共 52 个测试；后续全量 `mvn test` 通过，共 54 个测试。

- 2026-06-03：扩展 LLM 预算查询视图，`LlmBudgetUsage` 保留 confirmed 明细用量 `attempts/consumedUnits`，新增 `reservedAttempts/reservedUnits` 和 `pendingUnits()`；memory/JDBC 查询同时返回预占行与明细行，能暴露预占成功但明细审计缺失的 pending 差额。`mvn -pl intent-hub-infrastructure,intent-hub-interfaces -am test` 通过，相关模块共 54 个测试。
- 2026-06-03：补齐 LLM 日预算同步失败释放：`LlmBudgetAuditPort.releaseDailyBudgetReservation` 在 provider 远端异常时释放本次预占；memory/JDBC 实现均保证释放后 reserved/pending 归零且可再次预占；`TongyiLlmAdapterTest` 覆盖 HTTP 远端失败后释放本次预占。`mvn -pl intent-hub-infrastructure,intent-hub-interfaces -am test` 通过，相关模块共 57 个测试。
- 2026-06-03：补齐 LLM 日预算 stale pending 后台补偿：`LlmBudgetAuditPort.reconcileStaleDailyBudgetReservations` 只校正 `__budget__/__daily__` reserved 预占行，不回滚 confirmed 外呼审计；调度任务通过 `intent-hub.llm.budget-reconciliation.enabled` 默认关闭，开启后按配置周期补偿 stale pending。`mvn test` 通过，全量共 61 个测试。

## 错误记录

- 首次 `mvn test` 因新增断言漏掉既有 `POST_ROUTE:NONE` 路径失败，已按现有后置路由行为修正断言后全量通过。
- 首次 PowerShell parser 验证命令使用未初始化 `[ref]` 变量导致验证自身失败；修正验证命令后发现脚本内中文字符串被截断，已重建脚本并通过语法检查。

## 2026-06-03 补充记录：LLM 日预算后台补偿指标

- 本轮目标：在已完成的 stale pending 后台补偿基础上，补齐可观测指标出口。
- 已完成：新增 `IntentMetricsPort.recordLlmBudgetReconciliation`、`MetricsSnapshot.totalLlmBudgetReconciliations` 和 Prometheus 指标 `intent_hub_llm_budget_reconciliations_total`。
- 语义边界：指标只记录后台补偿校正的 stale reserved 预占数量，不改变预算预占、释放、补偿和 confirmed 外呼审计语义。
- 文档同步：已更新 README、status、HTML 生命周期页、P1 设计和 P2-5 trace 审查。
- 下一步：在后续告警工作中基于该指标补阈值规则、Micrometer/OpenTelemetry 桥接和真实多实例 PostgreSQL 压测。

## 2026-06-04 补充记录：基础告警快照

- 本轮目标：在不引入 Actuator/Micrometer/Alertmanager 的前提下，先给管理端提供可查询的基础告警快照。
- 已完成：新增 `MetricsAlertAppService`、`MetricsAlert`、`MetricsAlertSnapshot`、`AlertSeverity` 和 `GET /api/v1/admin/metrics/alerts`。
- 告警口径：bad case 率超过 30% 返回 `BAD_CASE_RATE_HIGH`；模型 fallback、LLM fallback、LLM 预算后台补偿出现正数时返回 WARN；平均耗时超过 1000ms 返回 WARN；最大耗时超过 3000ms 返回 CRITICAL。
- 验证：`mvn -pl intent-hub-interfaces -am test` 通过，Reactor 5 个模块成功，测试共 62 个，失败 0。
- 后续：将该快照桥接到 Prometheus Alertmanager/Grafana Alerting，并补 P95/P99、窗口化速率和多实例聚合。

## 2026-06-04 补充记录：Prometheus 告警规则样例

- 本轮目标：在基础告警快照之后，补一份可给运维侧试接入的 Prometheus/Alertmanager 规则样例。
- 已完成：新增 `ops/prometheus/intent-hub-alert-rules.yml`。
- 规则口径：bad case 率超过 30%、模型 fallback、LLM fallback、LLM 预算后台补偿、平均耗时超过 1000ms、最大耗时超过 3000ms。
- 边界：样例不改变运行时代码，不引入 Actuator/Micrometer，不提供生产 scrape/route 配置；真实生产仍需按部署拓扑补 Prometheus scrape、Alertmanager route 和 Grafana dashboard。

## 2026-06-04 补充记录：Grafana 看板样例

- 本轮目标：在 Prometheus 告警规则样例之后，补一份可导入 Grafana 的最小 dashboard 样例。
- 已完成：新增 `ops/grafana/intent-hub-dashboard.json`。
- 看板口径：请求量、bad case 率、平均/最大耗时、decision 分布、模型/LLM fallback、LLM 预算活动、intent 分布和 scene 分布。
- 边界：样例只引用当前 Prometheus 文本出口已有指标，不改变运行时代码；真实生产仍需补 Prometheus scrape、Grafana datasource/provisioning、folder、权限、SLO 和窗口化速率面板。

## 2026-06-04 补充记录：Prometheus scrape 样例

- 本轮目标：在 Prometheus 告警规则与 Grafana 看板样例之后，补齐抓取入口的最小配置片段和说明。
- 已完成：新增 `ops/prometheus/intent-hub-scrape-config.yml` 和 `ops/prometheus/README.md`。
- scrape 口径：抓取 `GET /api/v1/admin/metrics/prometheus`，默认样例 job 为 `intent-hub`、interval 为 30s、timeout 为 5s。
- 边界：样例不提供生产服务发现、TLS/鉴权、Alertmanager route、receiver、SLO 或多实例聚合策略；真实部署需按环境补齐。

## 2026-06-04 补充记录：SLO 与错误预算样例

- 本轮目标：在 scrape、告警规则和 Grafana 看板样例之后，补一份可讨论的 SLO/错误预算样例。
- 已完成：新增 `ops/slo/README.md`。
- SLO 口径：可用性、规则/模型/LLM 延迟、bad case 率、模型 fallback、LLM fallback、LLM 预算补偿和 Bad Case 处理时效。
- 边界：样例不是正式 SLA；生产落地前仍需结合租户等级、真实流量、成本预算和监管要求确认阈值。

## 2026-06-04 补充记录：Alertmanager 路由样例

- 本轮目标：在 Prometheus 告警规则之后，补一份 Alertmanager route/receiver/inhibit 样例。
- 已完成：新增 `ops/alertmanager/alertmanager-route-sample.yml` 和 `ops/alertmanager/README.md`。
- 路由口径：`critical` 进入 P1 通道，`warning` 进入 P2 通道，同一 alert critical 存在时抑制 warning。
- 边界：样例不提供真实 receiver、secret、TLS/鉴权、升级策略或静默策略；真实部署需按组织值班机制补齐。

## 2026-06-04 补充记录：本地观测栈样例

- 本轮目标：在 Prometheus、Alertmanager、Grafana 和 SLO 样例之后，补一套本地可试跑的观测栈编排样例。
- 已完成：新增 `ops/local-observability`，包含 Docker Compose、Prometheus 配置、Alertmanager 配置和 Grafana provisioning。
- 验证口径：本地 Intent Hub 启动后，Prometheus 抓取 `host.docker.internal:8080/api/v1/admin/metrics/prometheus`，Grafana 自动加载 dashboard 样例。
- 边界：样例不是生产部署；生产仍需补持久化、认证授权、TLS、真实 receiver、服务发现、资源限制、备份和高可用。

## 2026-06-04 补充记录：告警 Runbook

- 本轮目标：在告警规则、路由和本地观测栈之后，补一份告警处理手册。
- 已完成：新增 `ops/runbooks/intent-hub-alert-runbook.md` 和 `ops/runbooks/README.md`。
- 覆盖告警：bad case 率高、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时高和最大耗时 critical。
- 边界：Runbook 只覆盖 Intent Hub 识别、路由、观测和治理链路；业务数据修复需转交业务系统 owner。

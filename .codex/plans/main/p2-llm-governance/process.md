# P2-5 LLM 受控兜底

## 恢复胶囊

- 任务需求：继续 P2，完成小流量 LLM 受控兜底的最小闭环。
- 关键决策：LLM 仍是最后一道防线；默认关闭且预算为 0；只有全局治理配置和 scene 级 `llm_policy` 同时允许时才触发；失败按 fallback decision 关闭。
- 当前阶段：已完成；本轮 timeout 绑定、模型服务失败关闭、fallback 指标增强、LLM 预算消费最小计数与持久化审计、模型 adapter 本地 HTTP 冒烟、模型服务健康检查、本地真实联调、Spring AI Alibaba `ChatClient` 预接入和 DashScope 沙箱冒烟准备均已验证但暂不提交。
- 已完成产物：LLM 领域策略门禁、基础设施治理配置、Spring AI Alibaba 优先/HTTP fallback adapter、LLM 预算审计端口与 memory/JDBC 实现、DashScope smoke profile/script、JDBC 策略读取、测试、README/status/HTML/trace 同步。
- 剩余工作：按用户要求等待约 4 小时或明确指令后再集中提交；GitHub 推送只在用户明确发指令时执行。
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
  - 当前产物：`mvn test` 已通过，共 47 个测试。
  - 下一步：已完成。
- [v] 同步 README/status/HTML/trace。
- [~] 提交并推送。
  - 当前产物：本地变更已验证。
  - 下一步：等待提交窗口或用户明确指令；推送 GitHub 需用户单独指令。
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
  - 下一步：已完成；持久化审计已补，后续再做强配额扣减。
- [v] 补 LLM 预算持久化审计。
  - 当前产物：`LlmBudgetAuditPort`、`LlmBudgetUsage`、`InMemoryLlmBudgetAuditRepository`、`JdbcLlmBudgetAuditRepository` 和 Flyway `V2__p2_llm_budget_usage.sql`；按 `tenant_id + scene_id + usage_date + provider + model` 记录外呼尝试次数和消费单位。
  - 下一步：已完成；后续做按日配额强扣减、跨实例并发保护和管理端查询。
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

## 研究发现

- LLM 门禁应在领域策略层先拦截预算为 0 或 timeout 为 0 的场景，避免指标误算 LLM fallback。
- 基础设施 adapter 仍需二次检查全局治理开关、baseUrl 和全局预算，防止误配置导致外部调用。
- 当前 P2-5 还不是完整生产 LLM：已预接入 Spring AI Alibaba `ChatClient`，但没有真实 DashScope 沙箱冒烟和按日配额强扣减；底层 HTTP timeout 绑定、最小预算消费计数和持久化审计已完成。
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

## 错误记录

- 首次 `mvn test` 因新增断言漏掉既有 `POST_ROUTE:NONE` 路径失败，已按现有后置路由行为修正断言后全量通过。
- 首次 PowerShell parser 验证命令使用未初始化 `[ref]` 变量导致验证自身失败；修正验证命令后发现脚本内中文字符串被截断，已重建脚本并通过语法检查。

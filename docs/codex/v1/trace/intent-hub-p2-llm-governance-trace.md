# Intent Hub P2-5 LLM 受控兜底审查

## 审查结论

通过。

P2-5 已完成受控 LLM 兜底的最小闭环：LLM 仍位于 Rule 和 Model 之后，只作为最后一道防线；默认关闭且预算为 0，不会在未配置时触发外部调用。开启时同时受全局治理配置与 scene 级 `llm_policy` 约束，失败后按 `fallbackDecision` 失败关闭。

## 范围

本次审查覆盖：

- 领域层 `LlmPolicy` 与 `LlmRecognizePolicy` 门禁。
- `LlmClientPort` 调用契约。
- 基础设施 `TongyiLlmAdapter` 与治理配置。
- JDBC 已发布 `nlu_strategy.llm_policy` 读取。
- 自动化测试。
- README、status、HTML 和任务记录同步。

## 实现结果

新增基础设施能力：

- `LlmGovernanceProperties`
- `LlmRecognitionRequest`
- `LlmRecognitionResponse`
- `TongyiLlmAdapter` Spring AI Alibaba 优先、HTTP 契约 fallback 的小流量兜底 adapter
- `LlmBudgetAuditPort` / `LlmBudgetUsage`
- `InMemoryLlmBudgetAuditRepository`
- `JdbcLlmBudgetAuditRepository`
- `LlmBudgetReconciliationTask` / `LlmBudgetReconciliationProperties`
- Flyway `V2__p2_llm_budget_usage.sql`

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

DashScope 沙箱 profile：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}
      chat:
        enabled: true
        options:
          model: ${DASHSCOPE_CHAT_MODEL:qwen-plus}

intent-hub:
  llm:
    enabled: true
    base-url: "spring-ai-alibaba"
    timeout-ms: 5000
    max-retries: 0
    daily-budget: 3.0
    min-confidence: 0.60
```

配套脚本：

- `scripts/dashscope-smoke.ps1`：自动创建并发布沙箱 scene，写入 `provider=spring-ai-alibaba` 的 `llmPolicy`，发送一条规则/模型不命中的识别请求，并检查识别路径是否包含 `LlmRecognizePolicy`。

scene 级策略读取：

- `JdbcSceneConfigRepository` 从已发布 `nlu_strategy.llm_policy` 读取：
  - `enabled`
  - `provider`
  - `model`
  - `timeoutMs` / `timeout_ms`
  - `maxRetries` / `max_retries`
  - `dailyBudget` / `daily_budget`
  - `fallbackDecision` / `fallback_decision`

触发门禁：

```text
global enabled + baseUrl + global dailyBudget
+ scene llmPolicy.enabled
+ scene dailyBudget > 0
+ scene timeoutMs > 0
```

只有同时满足上述条件才尝试 LLM 调用。

## 架构一致性

### 双阶段路由

LLM 仍属于“怎么认”的识别策略，不参与后置动作选择。识别结果仍交给后置路由选择 `DownstreamAction`。

### LLM 受控

LLM 不是主力路径。规则和模型命中时不会进入 LLM；未命中且策略允许时才触发。治理关闭、预算为 0、超时为 0 或策略关闭都会阻断 LLM。

### 防腐层

LLM adapter 只返回识别候选 `intentCode/confidence/slots/explanation`，不返回 SQL、不返回业务执行动作，不接触业务数据源。

### DDD 分层

领域层只依赖 `LlmClientPort` 和 `LlmPolicy`，不依赖 Spring AI、DashScope、RestClient 或供应商 SDK。Spring AI Alibaba 与 HTTP 适配均位于 infrastructure。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 61 个。
- 失败 0，错误 0，跳过 0。

新增覆盖点：

- `RecognizeAppServiceTest` 覆盖 LLM provider 异常时失败关闭并记录 `LLM_FALLBACK:REJECTED`。
- `RecognizeAppServiceTest` 覆盖 scene 策略预算为 0 时不进入 LLM。
- `TongyiLlmAdapterTest` 覆盖治理关闭、策略预算为 0、Spring AI Alibaba ChatClient 分支、HTTP 契约 fallback、成功返回候选、有限重试后失败、真实外呼尝试前记录预算消费、远端失败后释放本次预占、日预算耗尽不外呼，以及全局预算收紧优先阻断。`InMemoryLlmBudgetAuditRepositoryTest` 与 `JdbcLlmBudgetAuditRepositoryTest` 覆盖日预算预占成功/失败、查询不双算、预占后明细缺失时暴露 pending 差额、失败释放后可再次预占，以及 stale pending 预占后台补偿。
- `JdbcSceneConfigRepositoryTest` 覆盖从已发布 `nlu_strategy.llm_policy` 读取 LLM 策略。
- 模型服务和 LLM HTTP adapter 已通过 `SimpleClientHttpRequestFactory` 绑定 connect/read timeout。
- `LLM_FALLBACK` 会进入最小指标口径，支持通过 `GET /api/v1/admin/metrics` 和 Prometheus 文本观察 LLM 失败关闭次数；`intent_hub_llm_budget_attempts_total` 与 `intent_hub_llm_budget_consumed_total` 记录 LLM 外呼预算消费尝试；`intent_hub_llm_budget_reconciliations_total` 记录后台补偿校正的 stale reserved 预占数量；`GET /api/v1/admin/metrics/alerts` 可基于 LLM fallback 和预算补偿指标返回基础告警快照；`ops/README.md` 提供运维样例总入口，`ops/production-readiness-checklist.md` 提供生产化落地检查清单，`ops/pilot-rollout-plan.md` 提供试点接入计划，`ops/pilot-execution-record-template.md` 提供试点执行记录模板，`ops/alert-drill-scenarios.md` 提供告警演练场景，`scripts/check-observability-local.ps1` 提供本地观测栈预检脚本，`scripts/validate-observability-compose.ps1` 提供本地观测栈配置校验脚本，`ops/prometheus/intent-hub-scrape-config.yml` 提供 Prometheus scrape 配置片段样例，`ops/prometheus/intent-hub-alert-rules.yml` 提供 Prometheus 规则样例，`ops/alertmanager/alertmanager-route-sample.yml` 提供 Alertmanager 路由样例，`ops/grafana/intent-hub-dashboard.json` 提供 Grafana 看板样例，`ops/slo/README.md` 提供 SLO 与错误预算样例，`ops/local-observability/README.md` 提供本地观测栈试跑样例，`ops/runbooks/intent-hub-alert-runbook.md` 提供告警处理手册。
- `llm_budget_usage` 按 `tenant_id + scene_id + usage_date + provider + model` 记录 LLM 外呼尝试次数和消费单位；`TongyiLlmAdapter` 会在外呼前查询当日用量，达到全局预算与 scene 预算较小值时直接返回空候选。
- DashScope 沙箱 profile 与冒烟脚本已准备完成，凭证只从 `DASHSCOPE_API_KEY` 环境变量读取，不写入仓库。

## 当前限制

- 当前 `TongyiLlmAdapter` 已预接入 Spring AI Alibaba `ChatClient`，并保留 HTTP 契约 fallback；没有 `ChatClient.Builder` 或 provider 不是 `spring-ai-alibaba` 时不会强依赖真实 DashScope。
- 尚未使用真实 DashScope 沙箱密钥完成外部冒烟；当前只完成 profile、脚本和验证步骤准备。
- `timeoutMs` 已进入策略和治理配置，并已绑定到底层 RestClient connect/read timeout。
- 当前已完成 LLM 外呼预算消费最小计数、持久化审计、外呼前日预算原子预占门禁、同步失败释放、默认关闭的 stale pending 后台补偿、补偿指标、基础告警快照、运维样例总入口、生产化落地检查清单、试点接入计划、试点执行记录模板、告警演练场景、本地观测栈预检脚本、本地观测栈配置校验脚本、Prometheus scrape/告警规则样例、Alertmanager 路由样例、Grafana 看板样例、SLO 样例、本地观测栈样例、告警 Runbook 和管理端 confirmed/reserved/pending 查询；真实多实例 PostgreSQL 压测、分布式保护和生产化 Prometheus/Grafana 告警仍待补。

## 后续建议

- 接入真实 DashScope 沙箱密钥，做小流量冒烟并记录 trace、指标和 bad case。
- 补充 DashScope 限流告警和失败分类。
- 将 LLM 调用次数、失败次数、fallback decision、预算消耗和补偿校正数量进一步接入 Prometheus/Grafana 告警、分布式保护和真实多实例压测。

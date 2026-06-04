# Intent Hub 告警 Runbook

本文档对应 `ops/prometheus/intent-hub-alert-rules.yml` 中的 P2.x 告警规则，用于值班时快速判断影响、止血和定位。

## 通用处理原则

1. 先确认影响范围：租户、scene、渠道、时间窗口、是否只影响 LLM 兜底或模型路径。
2. 先止血再定位：必要时回滚配置版本、关闭模型服务路径、关闭 LLM 兜底或收紧预算。
3. 不让 LLM 变主链路：LLM 只能作为最后防线，fallback 激增时优先修规则、模型和配置。
4. 防腐层不越界：Intent Hub 只排查识别、路由、动作指令，不直接修改业务库或业务数据。
5. 保留证据：记录 trace_id、request_id、tenant_id、scene_id、配置版本、recognition_path 和 dashboard 截图。

## 快速入口

- 指标快照：`GET /api/v1/admin/metrics`
- Prometheus 文本：`GET /api/v1/admin/metrics/prometheus`
- 告警快照：`GET /api/v1/admin/metrics/alerts`
- 健康检查：`GET /api/v1/admin/health`
- LLM 预算查询：`GET /api/v1/admin/llm/budget-usage`
- Trace 查询：`GET /api/v1/admin/observability/traces/{traceId}`
- Bad Case 查询：`GET /api/v1/admin/observability/bad-cases`

## IntentHubBadCaseRateHigh

触发条件：

```promql
intent_hub_bad_cases_total / clamp_min(intent_hub_requests_total, 1) > 0.30
```

影响判断：

- 是否集中在某个 tenant、scene、channel。
- 是否在最近一次配置发布后出现。
- 是否由 `REJECTED`、`CLARIFY`、低置信或 fallback 失败关闭驱动。

止血动作：

- 回滚最近配置版本。
- 临时提高高频意图规则优先级。
- 对高风险 scene 暂时关闭 LLM 兜底，避免成本被异常样本放大。

定位步骤：

1. 查询 bad case 列表，按 scene、intent、status 聚合。
2. 抽样查看 trace，确认 recognition_path 是 Rule、Model 还是 LLM 失败。
3. 对照最近配置发布记录，检查 synonym、slot、routing rule、nlu_strategy。
4. 将确认样本进入标注流转，形成训练或规则补充任务。

## IntentHubModelFallbackDetected

触发条件：

```promql
intent_hub_model_fallbacks_total > 0
```

影响判断：

- 是否模型服务整体不可用。
- 是否只有某个模型 baseUrl、timeout 或 provider 配置异常。
- 规则主链路是否仍可正常识别。

止血动作：

- 关闭模型服务路径，回退 Rule -> LLM 受控兜底或 Rule-only。
- 回滚模型服务 baseUrl/timeout 配置。
- 如果模型服务不稳定，优先保持规则主链路可用。

定位步骤：

1. 查看 `GET /api/v1/admin/health` 中 `model_service.healthy`。
2. 检查模型服务 `/health` 和 `/recognize`。
3. 检查网络、DNS、timeout、模型服务日志。
4. 抽样 trace，确认 `MODEL_FALLBACK:CLOSED` 是否集中出现。

## IntentHubLlmFallbackDetected

触发条件：

```promql
intent_hub_llm_fallbacks_total > 0
```

影响判断：

- LLM 是否仍只作为最后防线。
- fallback 是否由预算、timeout、provider 异常或 prompt guard 触发。
- 是否因为规则/模型召回下降导致 LLM 被动承担主流量。

止血动作：

- 将 scene 级 `llm_policy.enabled` 关闭。
- 降低 scene 级预算或全局预算。
- 回滚触发 LLM 激增的配置版本。

定位步骤：

1. 查询 LLM 预算使用，确认 confirmed、reserved、pending。
2. 抽样 trace，查看 `LLM_FALLBACK:{fallbackDecision}`。
3. 检查 `llm_policy`、全局治理开关、timeout、maxRetries、provider。
4. 将 LLM fallback 样本转为规则补充或模型训练候选。

## IntentHubLlmBudgetReconciliationDetected

触发条件：

```promql
intent_hub_llm_budget_reconciliations_total > 0
```

影响判断：

- 是否有 stale pending 预占未释放。
- 是否 provider timeout、adapter 异常或服务重启导致。
- 是否影响当天 LLM 可用预算。

止血动作：

- 暂停高风险 scene 的 LLM 兜底。
- 缩短 provider timeout 或减少 maxRetries。
- 若 pending 持续增加，临时关闭 LLM 预算后台补偿之外的真实外呼。

定位步骤：

1. 查询 `GET /api/v1/admin/llm/budget-usage`，查看 pendingUnits。
2. 检查 `llm_budget_usage` 中 `__budget__/__daily__` 预占行。
3. 对照 provider 调用日志和 adapter 异常。
4. 复核后台补偿配置是否默认关闭或周期过长。

## IntentHubAverageLatencyHigh

触发条件：

```promql
intent_hub_latency_millis_avg > 1000
```

影响判断：

- 是所有流量变慢，还是某个 recognition path 变慢。
- 是否模型服务、LLM provider、数据库查询或下游动作路由导致。
- 是否与配置发布、模型服务发布或网络变更相关。

止血动作：

- 关闭慢路径：优先关闭异常模型服务或 LLM 兜底。
- 回滚最近配置版本。
- 临时提高规则命中覆盖，减少模型和 LLM 调用。

定位步骤：

1. 查看平均耗时和最大耗时走势。
2. 抽样 trace，比对 Rule、Model、LLM 路径。
3. 检查模型服务和 LLM provider timeout。
4. 检查 PostgreSQL、Redis、网关、网络延迟。

## IntentHubMaxLatencyCritical

触发条件：

```promql
intent_hub_latency_millis_max > 3000
```

影响判断：

- 是否存在单个请求长尾，还是持续长尾。
- 是否触发用户可感知超时。
- 是否有外部依赖卡死或 timeout 未生效。

止血动作：

- 立即关闭外部慢依赖路径，包括模型服务或 LLM。
- 收紧 timeout，避免请求堆积。
- 必要时限流高风险 tenant/scene。

定位步骤：

1. 查找长尾请求对应 trace_id。
2. 查看 recognition_path 是否包含 Model 或 LLM。
3. 检查 connect/read timeout 是否按配置生效。
4. 复盘是否需要 P95/P99 histogram 指标和链路 span。

## 复盘清单

- 是否需要新增规则、同义词、槽位或训练样本。
- 是否需要调整 scene routing、nlu_strategy 或 llm_policy。
- 是否需要补充 Prometheus 规则、Grafana 面板或 SLO 阈值。
- 是否需要把 recognition_path 从字符串升级为结构化 span/event。
- 是否需要对高等级租户配置独立限流、预算和告警路由。

# Intent Hub 告警演练场景

本文档用于执行 `ops/pilot-rollout-plan.md` Day 6 的 6 条 P2.x 告警演练。目标是验证告警链路和 Runbook，而不是制造真实生产故障。

## 演练原则

- 只在 dev、staging 或明确隔离的试点环境执行。
- 优先通过沙箱流量、临时测试配置或 Prometheus 规则临时阈值触发。
- 不对真实业务库执行写入、修复或回滚。
- 不把 LLM 放大为主流量路径；LLM 相关演练必须使用极小预算和测试 scene。
- 演练后必须恢复配置、确认告警恢复，并填写 `ops/pilot-execution-record-template.md`。

## 通用准备

| 检查项 | 要求 |
| --- | --- |
| Intent Hub 健康 | `GET /api/v1/admin/health` 返回 `UP` |
| Metrics | `GET /api/v1/admin/metrics/prometheus` 有指标 |
| Prometheus | `intent-hub` target 为 `UP` |
| Alertmanager | 测试 receiver 可收到通知 |
| Grafana | dashboard 使用试点 datasource |
| 记录模板 | 已复制 `ops/pilot-execution-record-template.md` |

## 1. IntentHubBadCaseRateHigh

触发目标：

```promql
intent_hub_bad_cases_total / clamp_min(intent_hub_requests_total, 1) > 0.30
```

推荐触发方式：

- 使用测试租户和测试 scene 连续发送未知意图或缺槽请求。
- 或在演练 Prometheus 中临时把阈值从 `0.30` 降低到当前可触发值。

验证点：

- Prometheus 中告警进入 pending/firing。
- Alertmanager 收到 `severity=warning`。
- Grafana bad case 率面板升高。
- Runbook 能定位到 bad case、trace 和最近配置。

恢复方式：

- 停止测试流量。
- 如临时改过阈值，恢复原始规则。
- 等待告警恢复或手动确认 Prometheus rule 状态。

禁止动作：

- 不把未知意图样本写入正式训练集。
- 不为了降低 bad case 率删除 trace 或 bad case 记录。

## 2. IntentHubModelFallbackDetected

触发目标：

```promql
intent_hub_model_fallbacks_total > 0
```

推荐触发方式：

- 在测试 scene 打开模型服务路径，并把模型服务 baseUrl 指向不可达的沙箱地址。
- 或临时停止 dev/staging 模型服务，再发送规则不命中的测试请求。

验证点：

- 识别链路记录 `MODEL_FALLBACK:CLOSED`。
- `GET /api/v1/admin/health` 中模型服务健康状态符合预期。
- Prometheus 告警进入 pending/firing。
- Runbook 能定位到模型服务 health、timeout、baseUrl 或网络问题。

恢复方式：

- 恢复模型服务地址或重启模型服务。
- 关闭测试 scene 的模型服务路径。
- 确认后续请求不再新增 model fallback。

禁止动作：

- 不在生产 scene 上制造模型不可用。
- 不因为模型失败而让 LLM 接管主流量。

## 3. IntentHubLlmFallbackDetected

触发目标：

```promql
intent_hub_llm_fallbacks_total > 0
```

推荐触发方式：

- 使用测试 scene，开启极小 LLM 预算和测试 provider。
- 将 provider 指向不可达沙箱地址，发送规则和模型均不命中的请求。
- 或临时将测试 scene 的 LLM timeout 调得极小，触发受控 fallback。

验证点：

- recognition path 包含 `LLM_FALLBACK:{fallbackDecision}`。
- `GET /api/v1/admin/llm/budget-usage` 可看到预算活动。
- Alertmanager 收到 warning。
- Runbook 能确认 LLM 仍是最后一道防线。

恢复方式：

- 关闭测试 scene 的 `llm_policy.enabled`。
- 恢复 provider、timeout 和预算。
- 确认 LLM fallback 不再增加。

禁止动作：

- 不使用真实生产 DashScope key 做故障演练。
- 不提高预算来“确保告警触发”。
- 不把用户真实输入发给演练 provider。

## 4. IntentHubLlmBudgetReconciliationDetected

触发目标：

```promql
intent_hub_llm_budget_reconciliations_total > 0
```

推荐触发方式：

- 优先使用单元/集成环境或可控测试 profile 制造 stale pending 预占。
- 若试点环境无法安全制造 stale pending，可采用临时降低 Prometheus 阈值或以人工记录方式验证 Runbook。

验证点：

- `intent_hub_llm_budget_reconciliations_total` 出现增长或规则被安全触发。
- `GET /api/v1/admin/llm/budget-usage` 可解释 confirmed、reserved、pending。
- Runbook 能说明 pending 来源和补偿边界。

恢复方式：

- 恢复规则阈值。
- 确认 pending 不持续增长。
- 关闭测试 scene 的 LLM 外呼路径。

禁止动作：

- 不直接改生产 `llm_budget_usage` 数据。
- 不手工删除预算审计记录。
- 不在不了解补偿语义时扩大后台补偿频率。

## 5. IntentHubAverageLatencyHigh

触发目标：

```promql
intent_hub_latency_millis_avg > 1000
```

推荐触发方式：

- 使用测试模型服务引入可控延迟，并让测试 scene 走模型路径。
- 或临时将演练规则阈值调低到当前平均耗时之下。

验证点：

- Grafana 平均耗时面板升高。
- Prometheus 告警进入 pending/firing。
- Runbook 能区分 Rule、Model、LLM 路径。
- 能说明是否需要 P95/P99 histogram。

恢复方式：

- 移除测试延迟。
- 关闭测试 scene 的慢路径。
- 恢复 Prometheus 阈值。

禁止动作：

- 不在生产入口制造慢请求。
- 不通过压垮数据库或网关来触发平均耗时告警。

## 6. IntentHubMaxLatencyCritical

触发目标：

```promql
intent_hub_latency_millis_max > 3000
```

推荐触发方式：

- 在测试模型服务中制造单次长尾响应。
- 或在演练规则中临时降低 `intent_hub_latency_millis_max` 阈值。

验证点：

- 告警 `severity=critical`。
- Alertmanager 路由到 P1 测试通道。
- critical 存在时同 alertname warning 被抑制。
- Runbook 能定位长尾 trace、路径和 timeout。

恢复方式：

- 移除测试长尾。
- 恢复 Prometheus 阈值。
- 确认 P1 测试通道不再收到重复告警。

禁止动作：

- 不制造真实用户可感知超时。
- 不延长生产 timeout 来观察告警。

## 演练完成标准

- 6 条告警至少完成触发或安全替代验证。
- 每条告警都有通知、Runbook 执行、恢复和问题记录。
- 所有临时阈值、receiver、测试 provider 和测试 scene 配置已恢复。
- 试点执行记录中明确是否允许进入下一环境。

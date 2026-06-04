# Intent Hub 观测告警试点执行记录模板

本文档用于记录 `ops/pilot-rollout-plan.md` 的真实执行证据。每次试点建议复制本模板，按环境命名为 `pilot-execution-record-<env>.md`，再填入实际结果。

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 试点环境 |  |
| 试点租户 |  |
| 试点 scene |  |
| Intent Hub 版本/提交 |  |
| 实例地址 |  |
| Prometheus 地址 |  |
| Alertmanager 地址 |  |
| Grafana 地址 |  |
| 执行时间窗口 |  |
| 执行人 |  |
| 参与人 |  |

## Day 1：范围与连通性确认

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `GET /api/v1/admin/health` 返回 `UP` |  |  |
| `GET /api/v1/admin/metrics/prometheus` 返回 200 |  |  |
| 响应包含 `intent_hub_requests_total` |  |  |
| Prometheus 到 Intent Hub 网络可达 |  |  |
| metrics 响应无明显敏感明文 |  |  |

记录：

```text

```

## Day 2：Prometheus Scrape

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| scrape job 已加载 |  |  |
| target 状态为 `UP` |  |  |
| `intent_hub_requests_total` 查询有结果 |  |  |
| 多实例时 `instance` 可区分 |  |  |
| scrape interval/timeout 已确认 |  |  |

PromQL 记录：

```promql
intent_hub_requests_total
```

结果摘要：

```text

```

## Day 3：Prometheus Rule

| 告警 | 状态 | 证据 |
| --- | --- | --- |
| `IntentHubBadCaseRateHigh` |  |  |
| `IntentHubModelFallbackDetected` |  |  |
| `IntentHubLlmFallbackDetected` |  |  |
| `IntentHubLlmBudgetReconciliationDetected` |  |  |
| `IntentHubAverageLatencyHigh` |  |  |
| `IntentHubMaxLatencyCritical` |  |  |

规则加载记录：

```text

```

## Day 4：Alertmanager Route

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| warning 路由到 P2 测试通道 |  |  |
| critical 路由到 P1 测试通道 |  |  |
| 默认 receiver 可接收兜底告警 |  |  |
| critical 抑制同 alertname warning |  |  |
| receiver secret 未写入仓库 |  |  |

通知记录：

```text

```

## Day 5：Grafana Dashboard

| 面板 | 结果 | 证据 |
| --- | --- | --- |
| 请求量 |  |  |
| Bad Case 率 |  |  |
| 平均/最大耗时 |  |  |
| Decision 分布 |  |  |
| Model/LLM fallback |  |  |
| LLM 预算活动 |  |  |
| Intent/Scene 分布 |  |  |

Dashboard 链接：

```text

```

## Day 6：告警演练

| 告警 | 触发方式 | 通知结果 | Runbook 结果 | 恢复结果 |
| --- | --- | --- | --- | --- |
| `IntentHubBadCaseRateHigh` |  |  |  |  |
| `IntentHubModelFallbackDetected` |  |  |  |  |
| `IntentHubLlmFallbackDetected` |  |  |  |  |
| `IntentHubLlmBudgetReconciliationDetected` |  |  |  |  |
| `IntentHubAverageLatencyHigh` |  |  |  |  |
| `IntentHubMaxLatencyCritical` |  |  |  |  |

演练备注：

```text

```

## Day 7：复盘

### 通过项

- 

### 问题项

- 

### 阈值调整

- 

### Receiver 调整

- 

### Dashboard 调整

- 

### Runbook 调整

- 

### 安全与合规问题

- 

## 结论

| 项目 | 结论 |
| --- | --- |
| 是否通过试点 |  |
| 是否进入下一环境 |  |
| 进入下一环境的前置条件 |  |
| 阻塞项 |  |
| 后续 owner |  |

## 附件索引

| 附件 | 说明 |
| --- | --- |
|  |  |

## 注意事项

- 不记录真实 receiver secret、token、签名密钥。
- 不粘贴包含手机号、身份证、订单敏感字段的原始 trace 或 bad case。
- 涉及业务数据修复时，只记录已转交业务系统 owner，不在 Intent Hub 内执行修复。

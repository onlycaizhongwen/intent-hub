# Intent Hub P2-3 指标采集与观测接口审查

## 审查结论

通过。

P2-3 已完成最小指标采集闭环，能在不改变现有健康检查口径、不引入新运维依赖的情况下，为识别链路提供 JSON 快照、Prometheus 文本格式指标出口和基础告警快照。后续 P2-4/P2-5 已把模型 fallback、LLM fallback 失败关闭、LLM 预算消费尝试和预算补偿纳入指标与告警口径。

## 范围

本次审查覆盖：

- 应用层指标端口与快照模型。
- 识别链路指标记录点。
- 内存指标实现。
- Admin Metrics API。
- 自动化测试。
- README、status、P1 设计和 HTML 阅读版同步。

## 实现结果

新增应用层：

- `IntentMetricsPort`
- `MetricsSnapshot`
- `MetricsAppService`
- `MetricsAlert`
- `MetricsAlertSnapshot`
- `MetricsAlertAppService`

新增基础设施：

- `InMemoryIntentMetricsRepository`

新增接口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/metrics` | GET | 返回 JSON 指标快照 |
| `/api/v1/admin/metrics/prometheus` | GET | 返回 Prometheus text/plain 指标 |
| `/api/v1/admin/metrics/alerts` | GET | 返回基础告警快照 |

配套运维样例：

- `ops/prometheus/intent-hub-alert-rules.yml`：提供 Prometheus/Alertmanager 告警规则样例，覆盖 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时。
- `ops/grafana/intent-hub-dashboard.json`：提供 Grafana dashboard 样例，覆盖请求量、bad case 率、耗时、decision 分布、fallback、LLM 预算活动、intent 和 scene 分布。

当前指标：

- 请求总数。
- Bad Case 候选数。
- 模型 fallback 次数。
- LLM fallback 次数。
- LLM 预算消费尝试次数与消费单位。
- LLM 预算后台补偿校正数量。
- 基础告警：bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时。
- 总耗时、平均耗时、最大耗时。
- 按 decision 计数。
- 按 intent 计数。
- 按 scene 计数。

## 架构一致性

### 双阶段路由

指标记录发生在识别结果生成之后，仅观察 `IntentResult` 与 `Envelope`，不会参与前置路由、规则识别或后置路由决策。

### LLM 受控

P2-3 没有新增 LLM 调用。当前 `totalModelFallbacks` 根据 `recognitionPath` 中的 `MODEL_FALLBACK` 统计，`totalLlmFallbacks` 根据 `LLM_FALLBACK` 统计；后续 P2-5 增加 `totalLlmBudgetAttempts`、`totalLlmBudgetConsumed` 与 `totalLlmBudgetReconciliations`，仅在 LLM adapter 真实外呼尝试或后台补偿时记账。`/metrics/alerts` 只消费已有指标快照，不触发 LLM。

### 防腐层

指标接口只暴露 Intent Hub 自身运行指标，不触碰业务库，不生成下游业务动作。

### DDD 分层

应用层定义指标端口和快照模型；基础设施层提供内存实现；接口层负责 Admin API。领域层没有新增 Spring、Web、JDBC 或观测 SDK 依赖。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 26 个。
- 失败 0，错误 0，跳过 0。

覆盖点：

- `RecognizeAppServiceTest` 验证识别链路会调用 metrics port。
- `InMemoryIntentMetricsRepositoryTest` 验证模型 fallback、LLM fallback 和 LLM 预算消费分别计数。
- `AdminMetricsControllerTest` 验证 JSON 快照、Prometheus 文本出口和基础告警快照。
- 既有 P1、P2-1、P2-2 测试仍全部通过。

## 当前限制

- P2-3 暂不引入 Actuator/Micrometer，不暴露 `/actuator/prometheus`。
- 指标为进程内内存指标，服务重启后清零。
- 多实例部署时需要后续通过 Prometheus scrape 或 Micrometer/OpenTelemetry 做聚合。
- 已提供进程内基础告警快照、Prometheus Alertmanager 规则样例和 Grafana dashboard 样例；暂未提供生产化 scrape 配置、Alertmanager route、Grafana folder/provisioning 和 SLO。
- 当前模型/LLM fallback 统计依赖 `recognitionPath` 字符串，后续可改为结构化 recognition span/event。

## 后续建议

P2 后续建议：

- 将 `IntentMetricsPort` 桥接到 Micrometer 或 OpenTelemetry Metrics。
- 将 Grafana dashboard 样例接入真实环境，并扩展拒识率、澄清率、异步接收率、P95/P99 耗时、窗口化速率和 bad case 堆积口径。
- 将基础告警快照和 Prometheus 规则样例桥接到真实 Alertmanager 或 Grafana Alerting，并补 P95/P99、窗口化速率和 bad case 堆积口径。
- 多实例部署时由 Prometheus 统一聚合，避免依赖单实例内存快照。

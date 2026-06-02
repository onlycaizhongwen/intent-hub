# Intent Hub P2-3 指标采集与观测接口审查

## 审查结论

通过。

P2-3 已完成最小指标采集闭环，能在不改变现有健康检查口径、不引入新运维依赖的情况下，为识别链路提供 JSON 快照和 Prometheus 文本格式指标出口。

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

新增基础设施：

- `InMemoryIntentMetricsRepository`

新增接口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/v1/admin/metrics` | GET | 返回 JSON 指标快照 |
| `/api/v1/admin/metrics/prometheus` | GET | 返回 Prometheus text/plain 指标 |

当前指标：

- 请求总数。
- Bad Case 候选数。
- LLM fallback 次数。
- 总耗时、平均耗时、最大耗时。
- 按 decision 计数。
- 按 intent 计数。
- 按 scene 计数。

## 架构一致性

### 双阶段路由

指标记录发生在识别结果生成之后，仅观察 `IntentResult` 与 `Envelope`，不会参与前置路由、规则识别或后置路由决策。

### LLM 受控

P2-3 没有新增 LLM 调用。`totalLlmFallbacks` 仅根据 `recognitionPath` 中是否包含 LLM 路径做统计，为后续受控兜底比例评估提供指标口径。

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
- `AdminMetricsControllerTest` 验证 JSON 快照和 Prometheus 文本出口。
- 既有 P1、P2-1、P2-2 测试仍全部通过。

## 当前限制

- P2-3 暂不引入 Actuator/Micrometer，不暴露 `/actuator/prometheus`。
- 指标为进程内内存指标，服务重启后清零。
- 多实例部署时需要后续通过 Prometheus scrape 或 Micrometer/OpenTelemetry 做聚合。
- 暂未提供 Grafana dashboard JSON、告警规则和 SLO。
- 当前 LLM fallback 统计依赖 `recognitionPath` 字符串，后续可改为结构化 recognition span/event。

## 后续建议

P2 后续建议：

- 将 `IntentMetricsPort` 桥接到 Micrometer 或 OpenTelemetry Metrics。
- 增加 Grafana dashboard：请求量、拒识率、澄清率、异步接收率、bad case 生成率、LLM fallback 率、平均/P95 耗时。
- 增加基础告警：拒识率突增、P95 耗时超阈、bad case 堆积、LLM fallback 超预算。
- 多实例部署时由 Prometheus 统一聚合，避免依赖单实例内存快照。

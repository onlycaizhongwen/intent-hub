# Intent Hub SLO 样例

本文档给出 Intent Hub P2.x 的 SLO 与错误预算样例，用于后续接入 Prometheus、Grafana 和告警策略时统一口径。

## 目标

Intent Hub 的核心承诺不是“尽量调用模型”，而是稳定完成受控识别：

- 双阶段路由稳定：先选怎么认，再选谁来干。
- LLM 受控：LLM 仅作为最后兜底，不能成为主流量路径。
- 防腐层可靠：输出适配层只发指令，不触碰业务数据。
- Bad Case 可回流：低置信、拒识、澄清和失败关闭样本可被持续观测。

## SLO 建议

| 类别 | 指标 | 建议目标 | 当前 P2 支撑 | 说明 |
| --- | --- | --- | --- | --- |
| 可用性 | 识别接口可用率 | 月度 >= 99.9% | 待生产网关/探活指标 | 需结合网关 5xx、应用健康检查和真实请求结果计算。 |
| 延迟 | 规则命中 P95 | <= 50 ms | 待 P95 指标 | 需求文档已定义目标，当前仅有平均/最大耗时样例。 |
| 延迟 | 模型识别 P95 | <= 300 ms | 待 P95 指标 | 需区分 Rule、Model、LLM recognition path。 |
| 延迟 | LLM 兜底超时 | <= scene 策略 timeout | 部分支持 | 当前已绑定 LLM HTTP timeout，但缺少分路径耗时直方图。 |
| 质量 | bad case 率 | <= 30% | 已支持 | 当前可通过 `bad_cases_total / requests_total` 观测。 |
| 稳定性 | 模型 fallback | 生产稳态应为 0 | 已支持 | 任何正数都应触发排查模型服务健康、超时和配置。 |
| 受控 LLM | LLM fallback | 生产稳态应低频可解释 | 已支持 | LLM 是最后防线，fallback 激增通常代表规则/模型/配置失效。 |
| 预算 | LLM 预算补偿 | 生产稳态应为 0 | 已支持 | 非 0 说明存在 stale pending 预占，需要排查 provider timeout 或 adapter 异常。 |
| 回流 | Bad Case 处理时效 | 重要场景 24h 内闭环 | 待流程指标 | 当前已有状态流转，后续需要按状态时间统计。 |

## 错误预算样例

以月度 99.9% 可用性为例：

- 月度总分钟数按 30 天计算：43200 分钟。
- 可用性错误预算：43.2 分钟/月。
- 若同一租户或核心 scene 连续 5 分钟 5xx/超时异常，应计入错误预算消耗。

质量类错误预算建议单独管理：

- bad case 率连续 5 分钟超过 30%，进入质量预算消耗。
- 模型 fallback 或 LLM 预算补偿出现正数，进入稳定性风险事件，不直接等同于可用性错误预算。
- LLM fallback 需要结合触发条件判断，避免把“受控兜底成功”误判为事故。

## PromQL 方向

当前 P2 指标为 gauge 样例，生产化时建议切换到 counter/histogram 或由 Micrometer/OpenTelemetry 暴露标准指标。

可先用现有样例指标验证链路：

```promql
intent_hub_bad_cases_total / clamp_min(intent_hub_requests_total, 1)
```

```promql
intent_hub_model_fallbacks_total
```

```promql
intent_hub_llm_fallbacks_total
```

```promql
intent_hub_llm_budget_reconciliations_total
```

```promql
intent_hub_latency_millis_avg
```

生产化后建议补：

- `histogram_quantile(0.95, rate(intent_hub_latency_millis_bucket[5m]))`
- `rate(intent_hub_requests_total[5m])`
- `rate(intent_hub_bad_cases_total[5m])`
- 按 `tenant_id`、`scene_id`、`recognition_path`、`decision` 做标签化聚合。

## 告警分级建议

| 级别 | 条件 | 处理建议 |
| --- | --- | --- |
| P1 | 核心 scene 识别入口不可用或持续超时 | 立即止血，必要时回滚配置版本或关闭外部模型/LLM 路径。 |
| P2 | bad case 率持续 > 30% 或模型 fallback 持续出现 | 排查近期配置发布、模型服务健康和路由策略。 |
| P3 | LLM fallback 增加但预算未超、主链路正常 | 分析样本，补规则/模型训练数据。 |
| P3 | LLM 预算补偿非 0 | 排查 provider timeout、adapter 异常和后台补偿周期。 |

## 边界

本文件是 SLO 样例，不是正式 SLA 承诺。生产落地前仍需结合业务优先级、租户等级、真实流量、成本预算和监管要求确认阈值。

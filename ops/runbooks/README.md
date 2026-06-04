# Intent Hub Runbook

本目录沉淀 Intent Hub 运维处理手册。

## 文件

- `intent-hub-alert-runbook.md`：对应当前 P2.x Prometheus 告警规则的处理手册。

## 使用方式

当 Alertmanager 收到 Intent Hub 告警后，先根据 `alertname` 进入对应章节：

- `IntentHubBadCaseRateHigh`
- `IntentHubModelFallbackDetected`
- `IntentHubLlmFallbackDetected`
- `IntentHubLlmBudgetReconciliationDetected`
- `IntentHubAverageLatencyHigh`
- `IntentHubMaxLatencyCritical`

## 边界

Runbook 只覆盖 Intent Hub 识别、路由、观测和治理链路，不包含下游业务系统的数据修复流程。涉及业务数据修复时，应转交业务系统 owner。

# Intent Hub 运维样例总入口

本目录沉淀 Intent Hub P2.x 的观测、告警、SLO 和本地试跑样例。它们用于把当前 Admin Metrics 与 Prometheus 文本出口接入运维体系，但不是生产完整配置。

## 接入顺序

建议按下面顺序阅读和试接入：

1. [Prometheus 抓取配置](prometheus/intent-hub-scrape-config.yml)
   先确认 Intent Hub 暴露 `GET /api/v1/admin/metrics/prometheus`，Prometheus 能抓取到 `intent_hub_requests_total` 等指标。
2. [Prometheus 告警规则](prometheus/intent-hub-alert-rules.yml)
   在 scrape 可用后加载规则，覆盖 bad case 率、模型 fallback、LLM fallback、LLM 预算补偿、平均耗时和最大耗时。
3. [Alertmanager 路由样例](alertmanager/alertmanager-route-sample.yml)
   将 Prometheus 告警按 `critical` 和 `warning` 分流到真实值班通道。
4. [Grafana 看板样例](grafana/intent-hub-dashboard.json)
   导入 dashboard，观察请求量、bad case 率、耗时、decision、fallback、预算和 scene/intent 分布。
5. [SLO 与错误预算样例](slo/README.md)
   用作正式 SLA、告警阈值和错误预算评审的讨论基线。
6. [本地观测栈样例](local-observability/README.md)
   使用 Docker Compose 在本机串联 Prometheus、Alertmanager 和 Grafana，验证端到端观测链路。
7. [告警 Runbook](runbooks/intent-hub-alert-runbook.md)
   告警触发后按 alertname 进入对应章节，执行影响判断、止血、定位和复盘。
8. [生产化落地检查清单](production-readiness-checklist.md)
   从样例进入真实环境前，逐项确认服务发现、鉴权、TLS、receiver、SLO、高可用和演练。
9. [观测告警试点接入计划](pilot-rollout-plan.md)
   选择 dev/staging 试点环境，按一周节奏完成抓取、规则、路由、看板和 Runbook 演练。
10. [试点执行记录模板](pilot-execution-record-template.md)
    真实试点执行时复制填写，统一留存 target、rule、receiver、dashboard、告警演练和复盘证据。
11. [告警演练场景](alert-drill-scenarios.md)
    为 6 条 P2.x 告警提供触发、验证、恢复和禁止动作，支撑 Day 6 演练。
12. [本地观测栈预检脚本](../scripts/check-observability-local.ps1)
    启动 Docker Compose 前检查本地配置文件、Intent Hub health/metrics endpoint 和 Docker 命令。
13. [本地观测栈配置校验脚本](../scripts/validate-observability-compose.ps1)
    不启动容器，校验 Docker Compose 文件、Prometheus 引用和 Grafana provisioning 引用。
14. [外部联调冒烟记录模板](external-integration-smoke-record-template.md)
    真实模型服务或 DashScope 联调时复制填写，统一留存 Secret 引用、preflight、鉴权、trace、预算和安全复核证据。
15. [模型服务端到端冒烟脚本](../scripts/smoke-model-service-e2e.ps1)
    自动打包 Intent Hub、启动模型服务容器、启动 Intent Hub jar，并验证模型服务健康状态和 `ModelRecognitionPolicy` 识别路径。

## 当前能力边界

- 已提供 Admin Metrics JSON、Prometheus 文本出口和基础告警快照。
- 已提供 Prometheus scrape、告警规则、Alertmanager route、Grafana dashboard、SLO、本地观测栈和 Runbook 样例。
- 已提供生产化落地检查清单，用于确认样例进入真实环境前的必改项。
- 已提供试点接入计划，用于在低风险环境获取真实观测与告警演练证据。
- 已提供试点执行记录模板，用于沉淀真实试点证据和复盘结论。
- 已提供外部联调冒烟记录模板，用于沉淀模型服务与 DashScope/LLM 外部 smoke 的 Secret、鉴权、trace、指标和安全复核证据。
- 已提供告警演练场景，用于安全触发和验证 P2.x 告警链路。
- 已提供本地观测栈预检脚本，用于启动 compose 前发现基础环境缺口。
- 已提供本地观测栈配置校验脚本，用于启动 compose 前验证配置引用。
- 已提供模型服务端到端冒烟脚本，用于回归验证 Docker 模型服务容器与 Intent Hub jar 的真实联调链路。
- 已提供外部联调预检脚本，用于真实 smoke 前检查 Intent Hub、模型服务健康和 Secret 引用存在性，且不打印密钥值。
- 暂未引入 Actuator/Micrometer/OpenTelemetry runtime 桥接。
- 暂未提供生产服务发现、TLS/鉴权、真实 receiver secret、Grafana 持久化、正式 SLA 审批、真实远端模型服务/DashScope 外呼证据、多实例压测和高可用部署。

## 生产化前必改项

- 将样例 `targets`、receiver URL、环境标签和 Grafana datasource 替换为真实环境配置。
- 补齐 API 网关或 Prometheus 到 Intent Hub metrics endpoint 的鉴权与网络策略。
- 按租户等级、scene 等级、业务时段和错误预算调整告警阈值。
- 为 Grafana、Prometheus 和 Alertmanager 配置持久化、备份、权限、TLS、审计和升级策略。
- 多实例部署时由 Prometheus 聚合指标，不依赖单实例内存快照作为最终判断。

## 与核心架构的关系

- 双阶段路由：运维指标只观察识别结果和路径，不参与“怎么认”或“谁来干”的决策。
- LLM 受控：LLM fallback、预算消费和预算补偿必须可观测，LLM 不应成为主流量路径。
- 防腐层：运维样例只处理 Intent Hub 自身指标与告警，不访问业务库、不执行 SQL、不修复业务数据。

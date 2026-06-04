# Intent Hub Grafana 样例

本目录提供 Intent Hub P2.x 最小观测看板样例。

## 文件

- `intent-hub-dashboard.json`：Grafana dashboard 导入样例。

## 前提

- Prometheus 已抓取 Intent Hub 的 `GET /api/v1/admin/metrics/prometheus`。
- Grafana 已配置 Prometheus datasource。

## 覆盖面

- 请求总数与 bad case 总数。
- bad case 率。
- 平均耗时与最大耗时。
- decision 分布。
- 模型 fallback 与 LLM fallback。
- LLM 预算尝试、消费单位和后台补偿。
- intent 与 scene 分布。

## 边界

该文件是运维样例，不是生产 provisioning 配置。生产环境仍需补齐 datasource、folder、权限、SLO、窗口化速率、P95/P99 和多实例聚合策略。

# Intent Hub Prometheus 样例

本目录提供 Intent Hub P2.x 最小 Prometheus 运维样例。

## 文件

- `intent-hub-scrape-config.yml`：Prometheus scrape 配置片段样例。
- `intent-hub-alert-rules.yml`：Prometheus 告警规则样例，可配合 `../alertmanager/alertmanager-route-sample.yml` 路由。

## 前提

- Intent Hub 已启动并暴露 `GET /api/v1/admin/metrics/prometheus`。
- Prometheus 能访问 Intent Hub 服务地址。

## 使用方式

将 `intent-hub-scrape-config.yml` 中的 `scrape_configs` 合并到真实 Prometheus 配置，按部署环境修改：

- `targets`：替换为真实服务地址或服务发现配置。
- `environment`：替换为真实环境标签。
- `scrape_interval` 与 `scrape_timeout`：按生产采集频率调整。

将 `intent-hub-alert-rules.yml` 按 Prometheus rule file 方式加载，并参考 `../alertmanager/README.md` 补齐真实 route、receiver 和静默策略。

## 边界

这些文件是运维样例，不是生产完整配置。生产环境仍需补齐服务发现、TLS/鉴权、Alertmanager route、receiver、Grafana dashboard provisioning、SLO 和多实例聚合策略。

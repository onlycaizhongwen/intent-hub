# Intent Hub 本地观测栈样例

本目录提供一组本地 Docker Compose 样例，用于把 Intent Hub 的 Prometheus 文本指标、告警规则、Alertmanager 和 Grafana dashboard 串起来验证。

## 前提

- 本机已安装 Docker。
- Intent Hub 已在宿主机启动，默认地址为 `http://localhost:8080`。
- Intent Hub 已暴露 `GET /api/v1/admin/metrics/prometheus`。

## 启动

启动前可先执行预检：

```powershell
.\scripts\check-observability-local.ps1 -BaseUrl "http://localhost:8080"
```

```powershell
cd ops/local-observability
docker compose up -d
```

访问入口：

- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000`

Grafana 默认账号密码：

- 用户名：`admin`
- 密码：`admin`

## 验证

Prometheus Targets 页面应看到 `intent-hub` target 为 `UP`。

可在 Prometheus 查询：

```promql
intent_hub_requests_total
```

Grafana 启动后会自动加载 Intent Hub dashboard 样例。

## 文件说明

- `docker-compose.yml`：本地 Prometheus、Alertmanager、Grafana 编排样例。
- `prometheus.yml`：本地 Prometheus 完整配置样例。
- `alertmanager.yml`：本地 Alertmanager 空 webhook 样例，仅用于本地启动。
- `grafana/provisioning/datasources/prometheus.yml`：Grafana Prometheus datasource provisioning。
- `grafana/provisioning/dashboards/intent-hub.yml`：Grafana dashboard provisioning。

## 边界

该目录仅用于本地试跑，不是生产部署配置。生产环境仍需补齐持久化存储、认证授权、TLS、真实告警接收器、服务发现、资源限制、备份和高可用策略。

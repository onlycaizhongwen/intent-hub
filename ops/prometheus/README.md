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

安全类告警已经单独标记 `category=security`：

- `IntentHubConfigPermissionDenied`：基于 `increase(intent_hub_permission_denied_total[5m]) > 0`，对应审计事件 `CONFIG_PERMISSION_DENIED`。
- `IntentHubAdminJwtAuthFailed`：基于 `increase(intent_hub_admin_jwt_auth_failures_total[5m]) > 0`，对应审计事件 `ADMIN_JWT_AUTH_FAILED`。

这两个规则使用 5 分钟增量窗口，避免累计指标大于 0 后长期保持 firing。生产环境可按 Admin 入口基线、租户等级和 IAM 发布窗口调高阈值或延长 `for`。

## 边界

这些文件是运维样例，不是生产完整配置。生产环境仍需补齐服务发现、TLS/鉴权、Alertmanager route、receiver、Grafana dashboard provisioning、SLO 和多实例聚合策略。
## JWKS 安全告警补充

Admin JWT 的 JWKS 路径已补充两条 Prometheus 规则，均标记 `category=security`，可复用安全告警路由：

- `IntentHubAdminJwksFetchFailed`：基于 `increase(intent_hub_admin_jwks_fetch_failures_total[5m]) > 0`，用于发现 IAM/OIDC JWKS endpoint、DNS、TLS、代理或 `jwksFetchTimeoutMs` 异常。
- `IntentHubAdminJwksStaleHit`：基于 `increase(intent_hub_admin_jwks_stale_hits_total[5m]) > 0`，用于发现 JWKS 刷新失败后进入 stale grace 旧缓存兜底。
- `IntentHubAdminOidcDiscoveryFetchFailed`：基于 `increase(intent_hub_admin_oidc_discovery_fetch_failures_total[5m]) > 0`，用于发现 OIDC discovery metadata endpoint、`jwks_uri` 缺失、issuer 不一致、DNS/TLS/代理或 discovery 配置异常。

这三条规则不携带 issuer、kid、url、tenant 或 actor 等高基数字段。生产环境接入时应结合真实 IAM 发布窗口、JWKS TTL、stale grace 时长、discovery endpoint 稳定性和值班策略调整 `for`、阈值和通知升级策略。

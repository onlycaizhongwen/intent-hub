# Intent Hub Alertmanager 样例

本目录提供 Intent Hub P2.x 最小 Alertmanager route/receiver 样例。

## 文件

- `alertmanager-route-sample.yml`：Alertmanager 路由、receiver 和 inhibit 规则样例。

## 前提

- Prometheus 已加载 `ops/prometheus/intent-hub-alert-rules.yml`。
- Alertmanager 已由 Prometheus 指向。
- 团队已有真实告警接收器，例如企业微信、钉钉、飞书、PagerDuty、Opsgenie 或内部告警网关。

## 路由建议

- `critical`：进入 P1 通道，短 `group_wait`、短 `repeat_interval`。
- `category=security`：进入安全值班通道，覆盖权限拒绝、Admin JWT 认证失败、JWKS 拉取失败和 JWKS stale 缓存命中。
- `warning`：进入 P2 通道，允许更长聚合窗口。
- 默认 receiver：平台值班或兜底通道。
- inhibit：同一 `alertname` 下 critical 存在时抑制 warning，减少重复噪音。

## 必改项

- 将 `alert-router.example.internal` 替换为真实告警接收地址。
- 根据组织值班机制拆分 P1/P2/P3 receiver，并确认安全告警 receiver 是否需要进入 SOC、IAM 或平台安全值班。
- 补齐 TLS、鉴权、代理、重试、receiver secret 管理。
- 根据真实租户等级、scene 等级和 SLO，调整 route matcher。

## 边界

该文件是 Alertmanager route 样例，不是生产完整配置。生产环境仍需结合组织值班机制、通知渠道、升级策略、静默策略、审计要求和敏感信息治理进行确认。

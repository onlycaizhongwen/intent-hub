# Intent Hub P2-28 安全指标 Prometheus/Alertmanager 规则审查

## 审查结论

P2-28 安全指标 Prometheus/Alertmanager 规则样例已完成，通过。

本阶段承接 P2-25 的 `CONFIG_PERMISSION_DENIED` 指标和 P2-27 的 `ADMIN_JWT_AUTH_FAILED` 指标，把两类安全事件从“内置 metrics/alerts 可见”推进到“可由真实 Prometheus rule 与 Alertmanager route 消费”。本次没有修改 Java 运行时代码，不改变 Admin JWT、权限校验或审计语义。

## 本次交付物

- `ops/prometheus/intent-hub-alert-rules.yml`
  - 新增 `IntentHubConfigPermissionDenied`。
  - 新增 `IntentHubAdminJwtAuthFailed`。
  - 两条安全告警均标记 `category=security`。
- `ops/alertmanager/alertmanager-route-sample.yml`
  - 新增 `intent-hub-security` receiver。
  - 新增 `category="security"` route。
- `ops/runbooks/intent-hub-alert-runbook.md`
  - 新增权限拒绝和 Admin JWT 认证失败处理章节。
- `scripts/validate-observability-compose.ps1`
  - 校验 10 条 Prometheus 告警规则存在。
  - 校验安全规则使用 5 分钟增量窗口。
- `ops/prometheus/README.md`、`ops/alertmanager/README.md`、`ops/README.md`
  - 同步安全告警接入说明。

## 指标与规则

权限拒绝：

```promql
increase(intent_hub_permission_denied_total[5m]) > 0
```

Admin JWT 认证失败：

```promql
increase(intent_hub_admin_jwt_auth_failures_total[5m]) > 0
```

采用 5 分钟增量窗口，而不是 `metric > 0` 累计判断，避免服务启动后只要出现过一次安全事件就长期 firing。生产环境可根据 Admin 入口基线、IAM 发布窗口和租户等级调整阈值、`for` 和接收通道。

## 一致性审查

- 与 P2-25 一致：Prometheus 规则消费 `intent_hub_permission_denied_total`，排查入口是 `CONFIG_PERMISSION_DENIED` 审计事件。
- 与 P2-27 一致：Prometheus 规则消费 `intent_hub_admin_jwt_auth_failures_total`，排查入口是 `ADMIN_JWT_AUTH_FAILED` 审计事件。
- 与安全边界一致：规则和 Runbook 明确不记录 Authorization header、JWT 原文、secret、完整 claims 或高基数字段。
- 与防腐层一致：告警处理只围绕 Intent Hub 配置权限、认证入口和动作指令，不直接修改业务库或业务数据。
- 与当前观测策略一致：保持 ops 样例方式，不引入 Actuator/Micrometer/OpenTelemetry runtime 依赖。

## 验证证据

已执行本地观测配置校验：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-observability-compose.ps1
```

结果：通过。校验覆盖本地 compose 配置、Prometheus rule 引用、Grafana provisioning 引用、10 条告警规则存在，以及安全规则使用 5 分钟增量窗口。

已尝试执行 Prometheus rule 语法校验：

```powershell
docker run --rm -v "${PWD}/ops/prometheus:/rules" prom/prometheus:latest promtool check rules /rules/intent-hub-alert-rules.yml
```

结果：未完成。当前环境没有本地 `prom/prometheus` 镜像，Docker Hub 拉取 `prom/prometheus:latest` 时返回 registry EOF，因此本轮没有 promtool 通过证据。待网络或镜像缓存可用后，应重跑该命令作为进入真实 Prometheus 前的补充验证。

## 风险与边界

- 本次是可部署样例，不代表已经接入 dev/staging/production 的真实 Prometheus、Alertmanager receiver 或 SOC 通道。
- 本轮未取得 promtool 通过证据，进入真实环境前仍需补跑 Prometheus rule 语法校验。
- 当前安全告警阈值偏敏感，适合 P2 最小治理阶段；真实环境应结合 Admin 调用基线和 IAM 发布窗口调整。
- 当前指标仍是全局聚合，不按 actor、sourceIp、path、tenant/scene 拆 Prometheus 标签，避免高基数风险；细分排查依赖审计日志。
- Alertmanager receiver URL 仍为样例地址，需要真实环境替换并补 TLS、鉴权、secret 管理和静默策略。

## 后续建议

- 在 dev/staging 加载 Prometheus rule，记录 target UP、rules load、Alertmanager receiver、告警触发与恢复证据。
- 将安全 receiver 对接组织 SOC/IAM/平台安全值班通道，并明确升级策略。
- 继续推进对象类型级权限，减少配置对象编辑面的过宽授权。
- 继续推进完整 IAM/OIDC/JWKS 接入，让 Admin JWT Filter 从 HS256 最小入口升级到企业级身份集成。

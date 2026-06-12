# Intent Hub P2-40 Admin OIDC Discovery 告警规则审查

## 结论

通过。P2-40 已将 P2-39 新增的 OIDC discovery fetch failure 指标接入 Prometheus/Alertmanager 本地样例、Runbook 和观测配置校验脚本，形成从运行时指标到运维告警的最小闭环。

## 本次交付

- `ops/prometheus/intent-hub-alert-rules.yml`
  - 新增 `IntentHubAdminOidcDiscoveryFetchFailed`。
  - 使用 `increase(intent_hub_admin_oidc_discovery_fetch_failures_total[5m]) > 0`。
  - 标记 `category=security`，复用安全告警路由。
- `scripts/validate-observability-compose.ps1`
  - 新增 discovery 告警规则必检项。
  - 将 discovery failure 纳入安全类 5 分钟增量窗口校验。
- `ops/runbooks/intent-hub-alert-runbook.md`
  - 新增 `IntentHubAdminOidcDiscoveryFetchFailed` 处理章节。
  - 明确区分 discovery metadata 故障、`jwks_uri` 指向的 JWKS endpoint 故障和 stale cache 降级。
- `ops/prometheus/README.md` / `ops/README.md`
  - 补充 discovery failure 告警说明和运维入口摘要。

## 告警语义

```promql
increase(intent_hub_admin_oidc_discovery_fetch_failures_total[5m]) > 0
```

该规则用于发现：

- OIDC discovery endpoint 不可达。
- discovery JSON 解析失败。
- discovery metadata 缺少 `jwks_uri`。
- discovery `issuer` 与显式配置 `issuer` 不一致。
- DNS、TLS、代理或 `jwksFetchTimeoutMs` 配置异常。

## 边界

- 本阶段是本地 Prometheus/Alertmanager 样例和脚本校验，不等同于真实 dev/staging Alertmanager 已加载。
- 告警规则保持低基数，不增加 issuer、kid、url、tenant、actor 等标签。
- discovery failure 告警只覆盖 metadata 拉取/解析/校验；JWKS endpoint 拉取失败仍由 `IntentHubAdminJwksFetchFailed` 覆盖。

## 验证证据

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-observability-compose.ps1
```

结果：通过。校验脚本确认 `IntentHubAdminOidcDiscoveryFetchFailed` 存在，且 security 类告警均使用 5 分钟 `increase(...[5m]) > 0` 增量窗口；Docker compose 配置、Prometheus rule file 引用、Grafana provisioning 引用保持通过。

## 后续建议

- 在真实 dev/staging Prometheus/Alertmanager 中加载规则并记录试点证据。
- 在真实 IAM/OIDC sandbox 中制造 discovery metadata 缺 `jwks_uri`、issuer 不一致、JWKS endpoint 不可达三类场景，验证告警分流是否符合预期。

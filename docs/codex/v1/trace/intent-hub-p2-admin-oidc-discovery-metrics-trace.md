# Intent Hub P2-39 Admin OIDC Discovery 指标审查

## 结论

通过。P2-39 已为 Admin OIDC discovery 拉取补齐独立指标，能区分 JWKS endpoint 拉取问题和 discovery metadata 拉取/解析/校验问题，便于后续接入 Prometheus 告警和真实 IAM/OIDC sandbox 排障。

## 本次交付

- `AdminJwksMetricsRecorder`
  - 新增 `recordDiscoveryFetch()`。
  - 新增 `recordDiscoveryFetchFailure()`。
- `AdminJwtVerifier`
  - discovery HTTP 请求前记录 fetch attempt。
  - discovery HTTP 非 2xx、JSON 解析失败、缺少 `jwks_uri`、issuer 校验失败等 discovery 链路失败均记录 failure。
  - 已缓存 `jwks_uri` 后不重复记录 discovery fetch，避免把 JWKS 缓存命中误算为 discovery 请求。
- `IntentMetricsPort` / `MetricsSnapshot` / `InMemoryIntentMetricsRepository`
  - 新增 discovery fetch/failure 计数。
- `MetricsAppService`
  - Prometheus 文本新增：
    - `intent_hub_admin_oidc_discovery_fetches_total`
    - `intent_hub_admin_oidc_discovery_fetch_failures_total`
- `AdminSecurityConfiguration`
  - 将 verifier discovery 指标接入全局 `IntentMetricsPort`。

## 指标语义

| 指标 | 含义 |
| --- | --- |
| `intent_hub_admin_oidc_discovery_fetches_total` | Admin JWT RS256/JWKS 通过 `oidcDiscoveryUrl` 拉取 discovery metadata 的次数 |
| `intent_hub_admin_oidc_discovery_fetch_failures_total` | discovery metadata 拉取、解析或安全校验失败次数 |

## 边界

- 指标保持低基数，不增加 issuer、kid、url、tenant、actor 等标签。
- 当前只新增指标和 Prometheus 文本导出，尚未新增 Prometheus/Alertmanager 告警规则。
- discovery fetch failure 包含 HTTP 失败、响应解析失败、缺 `jwks_uri` 和 issuer 不一致等链路失败；JWKS endpoint 本身失败仍计入既有 JWKS fetch failure。
- discovery URL 已解析并缓存后，后续验签不重复拉取 discovery metadata。

## 验证证据

```powershell
mvn -pl intent-hub-interfaces,intent-hub-infrastructure -am test '-Dtest=AdminJwtVerifierTest,AdminMetricsControllerTest,InMemoryIntentMetricsRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`InMemoryIntentMetricsRepositoryTest` 2 个、`AdminMetricsControllerTest` 3 个、`AdminJwtVerifierTest` 16 个，共 21 个相关测试通过。

## 后续建议

- P2-40 可补 `IntentHubAdminOidcDiscoveryFetchFailed` Prometheus/Alertmanager 告警规则与 Runbook。
- 在真实 IAM/OIDC sandbox 中验证 discovery fetch 指标是否能区分 IAM discovery endpoint 和 JWKS endpoint 故障。

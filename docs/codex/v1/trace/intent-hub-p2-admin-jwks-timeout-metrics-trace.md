# Intent Hub P2-35 Admin JWKS Timeout 与指标审查

## 审查结论

P2-35 Admin JWKS timeout 与指标最小闭环已完成，通过。

本阶段承接 P2-31 到 P2-34 的 Admin RS256/JWKS 能力，在保留默认关闭 Admin JWT Filter 和 Spring AI/业务配置边界不变的前提下，为 JWKS URL 拉取增加请求超时配置，并把 JWKS fetch、fetch failure、cache hit、stale hit 纳入既有 Admin metrics/Prometheus 文本。

## 本次交付物

- `AdminJwtProperties`
  - 新增 `jwksFetchTimeoutMs`。
  - 默认值为 `2000` 毫秒。
  - `<=0` 时不设置 JDK request timeout。
- `AdminJwtVerifier`
  - 使用 JDK `HttpRequest.timeout(...)` 限制 `jwksUrl` 拉取等待时间。
  - 继续保留 TTL、stale grace 和 RS256/JWKS 验签语义。
- `AdminJwksMetricsRecorder`
  - 新增轻量观察接口，verifier 默认 no-op。
  - Spring 装配层将其桥接到 `IntentMetricsPort`，避免 verifier 直接依赖应用指标仓储。
- `IntentMetricsPort` / `MetricsSnapshot` / `MetricsAppService`
  - 新增 JWKS fetch、fetch failure、cache hit、stale hit 计数。
  - Prometheus 文本新增：
    - `intent_hub_admin_jwks_fetches_total`
    - `intent_hub_admin_jwks_fetch_failures_total`
    - `intent_hub_admin_jwks_cache_hits_total`
    - `intent_hub_admin_jwks_stale_hits_total`
- `InMemoryIntentMetricsRepository`
  - 新增上述 4 个指标的内存计数实现。

## 配置语义

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      jwks-url: https://iam.example.com/.well-known/jwks.json
      jwks-cache-ttl-seconds: 300
      jwks-stale-grace-seconds: 300
      jwks-fetch-timeout-ms: 2000
```

## 一致性审查

- 与 P2-31 一致：不引入 Spring Security Resource Server，仍使用现有 servlet filter + JDK crypto/JWKS 路线。
- 与 P2-33 一致：TTL 命中仍复用缓存，并记录 cache hit。
- 与 P2-34 一致：刷新失败在宽限期内继续使用 stale JWKS，并记录 fetch failure 与 stale hit。
- 与可观测体系一致：继续复用现有 `/api/v1/admin/metrics` 与 `/api/v1/admin/metrics/prometheus`，不引入 Micrometer/Actuator 新依赖。

## 验证证据

已执行定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces,intent-hub-infrastructure -am test '-Dtest=AdminJwtVerifierTest,AdminMetricsControllerTest,InMemoryIntentMetricsRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。合计 15 个测试通过，覆盖 JWKS timeout、fetch/cache/stale 指标、Prometheus 文本和内存指标仓储计数。

已执行接口层组合回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest,AdminMetricsControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminConfigControllerTest` 21 个、`AdminMetricsControllerTest` 3 个、`AdminJwtVerifierTest` 12 个，合计 36 个测试通过。

## 风险与边界

- 当前指标是全局聚合计数，不按 issuer、kid、url、tenant 或 scene 打标签，避免高基数风险。
- 当前只记录计数，没有记录 JWKS fetch latency 分布。
- 当前 timeout 作用于 JDK HTTP request，不覆盖 DNS、代理、TLS 细分阶段的独立指标。
- 当前仍未实现 OIDC discovery 和真实 IAM sandbox smoke。

## 后续建议

- 补 Prometheus/Alertmanager 规则：JWKS fetch failure 或 stale hit 在 5 分钟窗口内持续出现时告警。
- 补真实 IAM/OIDC sandbox smoke，验证 TLS、DNS、issuer/audience、kid 与 key rotation。
- 后续如接入 Micrometer，可将 JWKS fetch latency 迁移为 histogram/timer。

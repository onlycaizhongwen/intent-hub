# Intent Hub P2-34 Admin JWKS 刷新失败旧缓存宽限审查

## 审查结论

P2-34 Admin JWKS 刷新失败旧缓存宽限最小闭环已完成，通过。

本阶段承接 P2-31 RS256/JWKS、P2-32 `jwksUrl` 本地 HTTP smoke 和 P2-33 JWKS 缓存 TTL。目标是在 JWKS URL 到期刷新时，如果 IAM/OIDC JWKS endpoint 短暂 5xx、网络抖动或返回不可解析内容，允许在可配置宽限期内继续使用上一份已成功拉取并解析过的 JWKS，避免 Admin API 因身份系统短时抖动整体不可用。

## 本次交付物

- `AdminJwtProperties`
  - 新增 `jwksStaleGraceSeconds`。
  - 默认值为 `300` 秒。
- `AdminJwtVerifier`
  - 新增 `cachedJwksStaleUntil`。
  - 成功拉取 JWKS 后，会先解析校验，再更新缓存。
  - `jwksUrl` TTL 到期后仍会尝试刷新。
  - 刷新失败时，如果当前时间仍在 `cachedJwksStaleUntil` 之前，则回退使用上一份缓存 JWKS。
  - 宽限期到期后，刷新失败会继续返回 `admin jwt jwks fetch failed`。
  - 负数宽限期按 `0` 处理。
- `AdminJwtVerifierTest`
  - 新增 `reusesStaleJwksWhenRefreshFailsWithinGraceWindow`，覆盖刷新失败但宽限期内继续验签成功。
  - 新增 `rejectsJwksUrlWhenRefreshFailsAfterGraceWindow`，覆盖宽限期为 0 时刷新失败立即拒绝。

## 配置语义

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      jwks-url: https://iam.example.com/.well-known/jwks.json
      jwks-cache-ttl-seconds: 300
      jwks-stale-grace-seconds: 300
      issuer: https://iam.example.com
      audience: intent-hub-admin
```

语义说明：

- `jwks-cache-ttl-seconds` 控制正常缓存命中窗口。
- `jwks-stale-grace-seconds` 控制 TTL 到期且刷新失败后的旧缓存可用窗口。
- 宽限窗口从“上一次成功拉取的缓存过期时间”开始计算。
- 设置为 `0` 表示禁用刷新失败回退。

## 一致性审查

- 与 P2-31 一致：仍不引入 Spring Security Resource Server；HS256 兼容路径不变。
- 与 P2-32 一致：`jwksUrl` 仍通过真实 HTTP endpoint 拉取；本阶段只增强失败语义。
- 与 P2-33 一致：TTL 到期仍会触发刷新；只有刷新失败时才启用 stale grace。
- 与安全边界一致：不会无限期接受旧 key；宽限期到期后必须重新成功拉取 JWKS。

## 验证证据

已执行干净编译定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am clean test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 11 个测试通过，覆盖 RS256/JWKS、JWKS URL、TTL、刷新失败宽限回退与宽限后拒绝。

已执行接口层 JWT + Admin Controller 回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminConfigControllerTest` 21 个测试、`AdminJwtVerifierTest` 11 个测试，合计 32 个测试通过。

## 风险与边界

- 当前刷新仍在请求线程内同步执行，不是后台异步刷新。
- 当前没有 JWKS 拉取超时配置，仍使用 JDK `HttpClient` 默认行为。
- 当前没有 JWKS 缓存命中、刷新失败、stale 回退次数的独立指标。
- stale grace 是生产可用性保护，不是 key rotation 的完整治理；真实 IAM/OIDC 接入仍需要演练 key 轮换、证书链、DNS、代理和 TLS。

## 后续建议

- 增加 `jwksFetchTimeoutMs`，避免 IAM endpoint 卡死时拖慢 Admin API 请求线程。
- 增加 JWKS fetch/cache/stale 指标，接入 Prometheus 和安全运维 runbook。
- 补真实 IAM/OIDC sandbox smoke，验证 issuer/audience/kid/JWKS URL/TLS 的完整链路。

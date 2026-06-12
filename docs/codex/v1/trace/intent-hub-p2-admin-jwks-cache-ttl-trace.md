# Intent Hub P2-33 Admin JWKS 缓存 TTL 审查

## 审查结论

P2-33 Admin JWKS 缓存 TTL 最小闭环已完成，通过。

本阶段承接 P2-31 的 RS256/JWKS 入口和 P2-32 的 `jwksUrl` 本地 HTTP smoke，给 JWKS URL 拉取结果增加可配置 TTL。目标是让企业 IAM/OIDC key rotation 有最小刷新路径：TTL 内复用缓存，TTL 到期后重新拉取 JWKS。

## 本次交付物

- `AdminJwtProperties`
  - 新增 `jwksCacheTtlSeconds`。
  - 默认值为 `300` 秒。
- `AdminJwtVerifier`
  - 增加 `cachedJwksExpiresAt`。
  - `jwksUrl` 拉取结果仅在 TTL 未过期时复用。
  - TTL 到期后重新请求 JWKS endpoint。
  - 负数 TTL 会按 0 处理。
- `AdminJwtVerifierTest`
  - 保留默认 TTL 下连续验签只请求一次 JWKS endpoint。
  - 新增 `refreshesJwksUrlWhenCacheTtlExpires`，设置 `jwksCacheTtlSeconds=0`，验证连续两次验签会请求两次 JWKS endpoint。

## 配置语义

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      jwks-url: https://iam.example.com/.well-known/jwks.json
      jwks-cache-ttl-seconds: 300
      issuer: https://iam.example.com
      audience: intent-hub-admin
```

## 一致性审查

- 与 P2-31 一致：RS256/JWKS 仍不引入 Spring Security Resource Server，HS256 兼容路径不变。
- 与 P2-32 一致：`jwksUrl` 仍通过真实 HTTP endpoint 拉取，新增 TTL 不改变成功验签语义。
- 与 key rotation 路线一致：TTL 到期后可以重新获取新的 JWKS，为后续双 key 轮换窗口、失败回退和后台刷新打基础。

## 验证证据

已执行干净编译定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am clean test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 9 个测试通过，新增覆盖 `jwksCacheTtlSeconds=0` 时重新拉取 JWKS。

已执行接口层 JWT + Admin Controller 回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminConfigControllerTest` 21 个测试、`AdminJwtVerifierTest` 9 个测试，合计 30 个测试通过。

## 风险与边界

- 当前 TTL 刷新是请求线程内同步刷新，不是后台异步刷新。
- 当前没有“刷新失败时继续使用旧 key”的宽限策略；如果 TTL 到期且 JWKS endpoint 不可用，本次验证会失败。
- 当前没有针对 JWKS 拉取耗时、失败次数和缓存命中的独立指标。
- 当前没有完整 OIDC discovery，仍需显式配置 `jwksUrl`。

## 后续建议

- 补刷新失败回退旧 JWKS 的宽限窗口，避免 IAM endpoint 短抖导致 Admin API 全部不可用。
- 补 JWKS 拉取 timeout、失败指标、缓存命中指标和 key rotation 演练脚本。
- 后续接入真实 IAM/OIDC sandbox 时，把 `jwksCacheTtlSeconds` 纳入联调配置模板。

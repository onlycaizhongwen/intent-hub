# Intent Hub P2-32 Admin JWKS URL 本地 smoke 审查

## 审查结论

P2-32 Admin JWKS URL 本地 smoke 已完成，通过。

本阶段承接 P2-31 的 RS256/JWKS 最小入口，补齐 `jwksUrl` 的真实 HTTP 拉取验证，避免 RS256 能力只停留在内联 `jwksJson`。本次使用 JDK 自带 `HttpServer` 在测试进程中启动本地 JWKS endpoint，生成 RSA key pair、签发 RS256 token，并验证 `AdminJwtVerifier` 可以从 HTTP JWKS endpoint 获取公钥完成验签。

## 本次交付物

- `AdminJwtVerifierTest.verifiesRs256TokenWithJwksUrlAndCachesResponse`
  - 启动本地 HTTP JWKS endpoint：`/.well-known/jwks.json`。
  - 生成 RSA key pair 并返回 JWKS。
  - 使用 `jwksUrl` 配置 verifier。
  - 使用 RS256 token 完成验签。
  - 同一个 verifier 连续验签两次，验证 JWKS endpoint 只被请求 1 次，证明实例内缓存生效。

## 一致性审查

- 与 P2-31 一致：仍不引入 Spring Security Resource Server，不改变 HS256 兼容路径。
- 与 P2-27 一致：`jwksUrl` 获取或验签失败仍沿既有 JWT 认证失败路径返回 403 并进入审计指标。
- 与真实 IAM/OIDC 接入路线一致：验证了 `jwksUrl` 的 HTTP 拉取链路，为后续连接企业 IAM 的 `.well-known/jwks.json` 做准备。

## 验证证据

已执行干净编译定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am clean test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 8 个测试通过，新增覆盖 `jwksUrl` 本地 HTTP 拉取与实例内缓存。

已执行接口层 JWT + Admin Controller 回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminConfigControllerTest` 21 个测试、`AdminJwtVerifierTest` 8 个测试，合计 29 个测试通过。

## 风险与边界

- 当前 smoke 使用本地 HTTP endpoint，不代表真实企业 IAM endpoint 的 TLS、网络策略、证书链、代理、DNS 或访问控制均已验证。
- 当前 JWKS 缓存仍为 verifier 实例内无 TTL 缓存；尚未覆盖 key rotation、缓存过期、失败回退和强制刷新。
- 当前未增加 JWKS URL 拉取超时配置和专用指标；真实环境接入前仍建议补超时、熔断、刷新和观测项。

## 后续建议

- 使用真实 IAM/OIDC sandbox endpoint 做一次 `jwksUrl + issuer + audience` 联调 smoke。
- 补 JWKS 缓存 TTL、刷新失败保留旧 key、key rotation 双 key 兼容窗口。
- 将 JWKS URL、issuer、audience 和 roles claim 映射写入外部联调配置模板。

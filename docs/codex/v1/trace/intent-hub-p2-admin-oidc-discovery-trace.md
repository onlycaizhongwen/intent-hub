# Intent Hub P2-37 Admin OIDC Discovery 审查

## 结论

通过。P2-37 已在既有 Admin JWT RS256/JWKS 能力上补充最小 OIDC Discovery 入口：当未配置显式 `jwksUrl` 时，可以通过 discovery 文档解析 `jwks_uri`，再复用现有 JWKS URL 拉取、TTL、stale grace、timeout、指标和验签链路。

## 本次交付

- `AdminJwtProperties`
  - 新增 `oidcDiscoveryUrl`。
- `AdminJwtVerifier`
  - JWKS 配置优先级保持为 `jwksJson > jwksUrl > oidcDiscoveryUrl`。
  - `oidcDiscoveryUrl` 只解析 discovery JSON 中的 `jwks_uri`。
  - 解析到 `jwks_uri` 后复用既有 `fetchJwks(...)`，不新增独立 key 缓存语义。
- `AdminJwtVerifierTest`
  - 新增 `verifiesRs256TokenWithOidcDiscoveryJwksUri`，使用本地 HTTP server 提供 `/.well-known/openid-configuration` 与 JWKS endpoint。
  - 新增 `rejectsOidcDiscoveryWithoutJwksUri`，明确 discovery 文档缺少 `jwks_uri` 时拒绝验签。

## 配置示例

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      issuer: https://iam.example.com
      audience: intent-hub-admin
      oidc-discovery-url: https://iam.example.com/.well-known/openid-configuration
      jwks-cache-ttl-seconds: 300
      jwks-stale-grace-seconds: 300
      jwks-fetch-timeout-ms: 2000
```

如果同时配置 `jwksJson` 或 `jwksUrl`，会优先使用更直接的配置，不会触发 discovery。

## 边界

- 当前仅实现 discovery 文档中的 `jwks_uri` 解析，不实现完整 OIDC discovery provider metadata 校验。
- 当前不会从 discovery 文档自动覆盖 `issuer` 或 `audience`；这两个安全约束仍由显式配置控制。
- 当前 discovery URL 结果按 verifier 实例缓存，不单独暴露指标；后续真实 IAM/OIDC sandbox 可再评估是否需要 discovery fetch 指标。
- 当前仍不引入 Spring Security Resource Server 依赖，继续保持现有 servlet filter 与 JDK 标准库实现。

## 验证证据

```powershell
mvn -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 14 个测试通过，覆盖 HS256、RS256/JWKS、JWKS URL、JWKS TTL、stale grace、timeout、OIDC discovery、错误 key、缺少 kid、无效签名和过期 token。

## 后续建议

- 在真实 IAM/OIDC sandbox 中执行 `oidcDiscoveryUrl + issuer + audience` smoke。
- 评估是否需要 discovery metadata 的 issuer 一致性校验开关。
- 若真实环境 discovery endpoint 独立故障较多，可补充 discovery fetch 指标与告警。

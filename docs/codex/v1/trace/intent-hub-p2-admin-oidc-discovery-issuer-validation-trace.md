# Intent Hub P2-38 Admin OIDC Discovery Issuer 校验审查

## 结论

通过。P2-38 在 P2-37 的 OIDC Discovery 最小入口上补齐 issuer 一致性校验：当显式配置 `issuer` 且启用 discovery issuer 校验时，discovery 文档中的 `issuer` 必须与配置值一致，否则拒绝继续解析 `jwks_uri` 和验签。

## 本次交付

- `AdminJwtProperties`
  - 新增 `oidcDiscoveryIssuerValidationEnabled`，默认 `true`。
- `AdminJwtVerifier`
  - 在解析 discovery 文档后、读取 `jwks_uri` 前执行 issuer 一致性校验。
  - 仅在配置了 `issuer` 时校验 discovery issuer。
  - 不从 discovery 文档自动覆盖运行时 `issuer` 或 `audience`。
- `AdminJwtVerifierTest`
  - 新增 `rejectsOidcDiscoveryWhenIssuerDoesNotMatchConfiguredIssuer`。
  - 新增 `allowsOidcDiscoveryIssuerMismatchWhenValidationIsDisabled`。

## 配置示例

```yaml
intent-hub:
  admin:
    jwt:
      issuer: https://iam.example.com
      oidc-discovery-url: https://iam.example.com/.well-known/openid-configuration
      oidc-discovery-issuer-validation-enabled: true
```

兼容少数非标准 IAM 或测试环境时，可以显式关闭：

```yaml
intent-hub:
  admin:
    jwt:
      oidc-discovery-issuer-validation-enabled: false
```

## 边界

- 默认策略是安全优先：显式配置了 `issuer` 时，discovery issuer 不一致会拒绝。
- 关闭 issuer 校验只影响 discovery metadata 校验，不影响 JWT payload 中 `iss` 与显式 `issuer` 的校验。
- 当前仍不实现完整 OIDC provider metadata 校验，也不自动发现或覆盖 `audience`。
- 当前 discovery fetch 仍未单独接入指标；JWKS 拉取、缓存、失败和 stale 指标继续复用既有链路。

## 验证证据

```powershell
mvn -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 16 个测试通过，覆盖 OIDC discovery `jwks_uri` 解析、缺少 `jwks_uri` 拒绝、discovery issuer 不一致拒绝，以及关闭 issuer 校验时的兼容路径。

## 后续建议

- 在真实 IAM/OIDC sandbox 中执行 `oidcDiscoveryUrl + issuer + audience` smoke。
- 继续评估 discovery fetch 是否需要独立指标与告警。
- 若接入标准 Spring Security Resource Server，应保持当前“显式配置优先、不自动放宽 issuer/audience”的安全语义。

# Intent Hub P2-31 Admin JWT RS256/JWKS 入口审查

## 审查结论

P2-31 Admin JWT RS256/JWKS 最小入口已完成，通过。

本阶段承接 P2-26 Admin JWT Filter 与 P2-27 JWT 认证失败审计指标，把 Admin JWT 从 HS256 最小共享密钥模式扩展到企业 IAM/OIDC 常见的 RS256 + JWKS 公钥验签入口。实现保持默认关闭，不引入 Spring Security Resource Server 依赖，也不破坏原有 HS256、`secretRef`、issuer/audience 和 roles claim 语义。

## 本次交付物

- `AdminJwtProperties`
  - 新增 `jwksJson`：用于本地、测试或配置中心下发的内联 JWKS。
  - 新增 `jwksUrl`：用于从 IAM/OIDC Provider 的 JWKS endpoint 拉取公钥集合。
- `AdminJwtVerifier`
  - 根据 JWT header `alg` 分流：
    - `HS256`：继续使用既有 `secret/secretRef` HMAC 验签。
    - `RS256`：使用 JWKS 中的 RSA 公钥验签。
  - 支持 JWT header `kid` 匹配 JWKS key。
  - 当 JWKS 内存在多个 RSA key 且 token 无 `kid` 时拒绝，避免误选公钥。
  - JWKS URL 拉取结果在 verifier 实例内做最小缓存。
- 测试
  - 保留 HS256 正反向用例。
  - 新增 RS256 + JWKS 正向验签。
  - 新增 JWKS key 不匹配拒绝。
  - 新增多 RSA key 但 token 缺少 `kid` 时拒绝。

## 配置语义

默认仍关闭：

```yaml
intent-hub:
  admin:
    jwt:
      enabled: false
```

HS256 兼容路径：

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      secret-ref: ADMIN_JWT_SECRET
      issuer: https://iam.example.com
      audience: intent-hub-admin
```

RS256/JWKS 路径：

```yaml
intent-hub:
  admin:
    jwt:
      enabled: true
      jwks-url: https://iam.example.com/.well-known/jwks.json
      issuer: https://iam.example.com
      audience: intent-hub-admin
      actor-claim: sub
      roles-claim: roles
```

## 一致性审查

- 与 P2-26 一致：Admin JWT Filter 默认关闭，开启后仍保护 `/api/v1/admin/config/**`，JWT actor/roles 仍优先于 header/body/query。
- 与 P2-27 一致：RS256/JWKS 验签失败仍通过既有 Filter 路径返回 403，并进入 `ADMIN_JWT_AUTH_FAILED` 审计和指标。
- 与 P2-28 一致：认证失败仍可被 Prometheus `intent_hub_admin_jwt_auth_failures_total` 规则消费。
- 与 P2-29/P2-30 一致：RS256 token 中的 roles claim 可继续承载 scoped role、对象类型级 editor role，并进入工作台权限和 review history 链路。

## 验证证据

已执行干净编译定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am clean test '-Dtest=AdminJwtVerifierTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminJwtVerifierTest` 7 个测试通过，覆盖 HS256、`secretRef`、issuer/audience、RS256/JWKS、错误公钥拒绝、多 key 无 `kid` 拒绝和过期 token 拒绝。

已执行接口层 JWT + Admin Controller 回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am test '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：通过。`AdminConfigControllerTest` 21 个测试、`AdminJwtVerifierTest` 7 个测试，合计 28 个测试通过。

## 风险与边界

- 当前仍不是完整 Spring Security Resource Server，不支持自动 OIDC discovery、复杂 claim mapping、token introspection 或细粒度 scope policy。
- `jwksUrl` 使用 verifier 实例内最小缓存，尚未实现 TTL、后台刷新、失败回退和轮换双 key 宽限窗口。
- 当前只支持 RS256，不支持 ES256、PS256 等算法。
- 当前没有把 JWKS 拉取超时做成独立配置；后续如接真实 IAM，应补 timeout、重试、熔断和可观测指标。

## 后续建议

- 在真实企业 IAM/OIDC 联调时优先使用 `jwksUrl + issuer + audience`，并记录 JWKS 拉取、key rotation、过期 token、错误 audience 的 smoke 证据。
- 后续如引入 Spring Security Resource Server，应保留当前 `AdminRequestContext` 作为业务层身份上下文适配点。
- 将 roles/group claim 映射规则写入 Admin Portal/IAM 接入文档，避免不同 IAM provider 的 claim 命名漂移。

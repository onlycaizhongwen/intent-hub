# Intent Hub P2-26 Admin JWT Filter 审查

## 审查结论

P2-26 Admin JWT Filter 最小闭环已完成，通过。

本阶段在不引入 Spring Security 依赖的前提下，为 Admin 配置接口补充默认关闭的 HMAC-SHA256 Bearer JWT 校验过滤器。JWT 只在显式开启后保护 `/api/v1/admin/config/**`，并把可信 `actor/roles` 写入请求上下文；既有 `X-IntentHub-Actor`、`X-IntentHub-Roles`、请求体和 query roles 兼容路径继续保留。

## 本次交付物

- `AdminJwtProperties`
  - 新增 `intent-hub.admin.jwt.*` 配置项，默认 `enabled=false`。
  - 支持 `secret`、`secretRef`、`actorClaim`、`rolesClaim`、`issuer`、`audience` 和受保护路径前缀。
- `AdminJwtVerifier`
  - 使用 JDK `Mac` 校验 HS256 签名。
  - 校验 `exp`、可选 `iss`、可选 `aud`。
  - 支持 roles 数组和逗号分隔字符串。
  - 复用 `SecretRefResolver`，明文 `secret` 优先于 `secretRef`。
- `AdminJwtAuthenticationFilter`
  - 默认关闭；开启后只保护 `/api/v1/admin/config/**`。
  - 成功后写入 request attribute，失败返回统一 403 JSON。
- `AdminSecurityConfiguration`
  - 注册 `AdminJwtProperties`、`AdminJwtVerifier` 和 `AdminJwtAuthenticationFilter` Bean。
- `AdminRequestContext`
  - 优先读取 JWT Filter 写入的 actor/roles，再回退 header/body/query。

## 契约与边界

- 当前是最小 Admin JWT Filter，不是完整 Spring Security/IAM 接入。
- 当前只支持 HS256，不支持 RS256/JWKS、OIDC discovery、refresh token 或会话态。
- 当前 Filter 只负责认证和角色来源可信化，细粒度授权仍在应用层 `ConfigPermission` / `ConfigRoleMatcher`。
- 默认关闭，不影响现有本地脚本和未带 JWT 的开发调用。
- Filter 返回的 403 JSON 与现有统一 403 契约保持同形：`code=FORBIDDEN`、`message`、`status=403`。

## 验证证据

新增或扩展测试：

- `AdminJwtVerifierTest`
  - 有效 HS256 token 可提取 actor 和 roles。
  - `secretRef`、issuer、audience 和逗号分隔 roles 可用。
  - 错误签名会被拒绝。
  - 过期 token 会被拒绝。
- `AdminConfigControllerTest`
  - JWT Filter 开启后，JWT actor/roles 优先于 header 和请求体。
  - 无效 JWT 返回结构化 HTTP 403。

已执行定向验证：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces -am '-Dtest=AdminJwtVerifierTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：通过。`AdminConfigControllerTest` 18 个测试、`AdminJwtVerifierTest` 4 个测试，合计 22 个测试。

已执行三层回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 36 个测试、基础设施层 61 个测试、接口层 37 个测试，合计 134 个测试。

## 风险与后续

- 生产环境应优先使用 `secretRef`，避免在配置文件中落明文密钥。
- 若接入企业 IAM/OIDC，后续应升级为 RS256/JWKS 或接入标准 Spring Security Resource Server。
- 当前没有记录 JWT 失败审计；后续可将认证失败也接入 `CONFIG_PERMISSION_DENIED` 或独立安全事件。
- 当前未细化 source IP、requestId、traceId；可在网关或过滤器层补充安全审计上下文。

# Intent Hub P2-27 Admin JWT 认证失败审计与指标审查

## 审查结论

P2-27 Admin JWT 认证失败审计与指标最小闭环已完成，通过。

本阶段承接 P2-26 的 Admin JWT Filter，把 JWT 缺失、签名错误、过期等认证失败从“只返回 403”推进到“可查审计、可观测指标、可告警快照”。实现不记录 Authorization header 或 token 原文，不新增数据库表，也不引入新的安全或观测依赖。

## 本次交付物

- `AdminJwtAuthenticationFilter`
  - 认证失败时记录 `ADMIN_JWT_AUTH_FAILED` 审计事件。
  - 审计 detail 只记录 `method`、`path`、`reason`。
  - 同步递增 Admin JWT 认证失败指标。
- `AdminSecurityConfiguration`
  - Filter Bean 注入 `AuditLogPort` 与 `IntentMetricsPort`。
- `IntentMetricsPort`
  - 新增 `recordAdminJwtAuthFailure(reason)` 默认方法。
- `MetricsSnapshot`
  - 新增 `totalAdminJwtAuthFailures` 聚合计数。
- `InMemoryIntentMetricsRepository`
  - 记录 Admin JWT 认证失败次数。
- `MetricsAppService`
  - Prometheus 文本新增 `intent_hub_admin_jwt_auth_failures_total`。
- `MetricsAlertAppService`
  - 当认证失败次数大于 0 时返回 `ADMIN_JWT_AUTH_FAILED` WARN 告警。

## 契约与边界

- JWT 成功路径不写审计，避免高频成功认证造成审计噪声。
- 认证失败审计不记录 token、Authorization header、secret 或 roles claim 原文。
- 指标保持全局聚合，不按 actor、IP、path、reason 拆标签，避免高基数风险。
- 本阶段仍是最小 JWT Filter，不是完整 IAM/OIDC/JWKS 接入。
- 认证失败与应用层授权失败分开计数：
  - `ADMIN_JWT_AUTH_FAILED` 表示 JWT 认证失败。
  - `CONFIG_PERMISSION_DENIED` 表示已进入应用层后的配置权限拒绝。

## 验证证据

新增或扩展测试：

- `AdminConfigControllerTest.returnsForbiddenWhenAdminJwtIsInvalid`
  - 无效 JWT 返回结构化 403。
  - 写入 `ADMIN_JWT_AUTH_FAILED` 审计事件。
  - 审计 detail 不包含 `authorization` 或 `token`。
  - 递增 `totalAdminJwtAuthFailures`。
- `InMemoryIntentMetricsRepositoryTest`
  - 覆盖 `recordAdminJwtAuthFailure` 后快照计数递增。
- `AdminMetricsControllerTest`
  - metrics snapshot 返回 `totalAdminJwtAuthFailures`。
  - Prometheus 文本包含 `intent_hub_admin_jwt_auth_failures_total`。
  - alert snapshot 包含 `ADMIN_JWT_AUTH_FAILED`。

已执行定向验证：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-interfaces,intent-hub-infrastructure -am '-Dtest=AdminConfigControllerTest,AdminMetricsControllerTest,InMemoryIntentMetricsRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：通过。相关测试 21 个通过。

已执行三层回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 36 个测试、基础设施层 61 个测试、接口层 37 个测试，合计 134 个测试。

## 风险与后续

- 当前审计 actor 仍为 `unknown`；完整 IAM 接入后可补真实 principal、source IP、requestId/traceId。
- 当前告警为内置快照，真实 Prometheus/Alertmanager 规则和阈值仍需在 dev/staging 验证。
- 后续如切换企业 IAM/OIDC，应将失败原因标准化，避免把 provider 原始错误直接暴露给外部调用方。

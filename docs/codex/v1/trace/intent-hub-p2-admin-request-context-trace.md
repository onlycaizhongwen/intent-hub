# Intent Hub P2-20 Admin 请求上下文角色来源审查

## 审查结论

P2-20 Admin 请求上下文角色来源最小闭环已完成，通过。

本阶段承接 P2-17/P2-18/P2-19，把 Admin 配置治理动作的 actor/roles 来源从“只依赖请求体或查询参数”推进到“优先读取网关/IAM 注入的请求头”。这不是完整 IAM 集成，但已经形成后续接入 APISIX、Spring Cloud Gateway、JWT Filter 或企业 IAM 的接口层适配点。

## 本次交付物

- `AdminRequestContext`
  - 新增 Admin 请求上下文读取工具。
  - 优先读取 `X-IntentHub-Actor` 作为操作者。
  - 优先读取 `X-IntentHub-Roles` 作为角色列表。
  - roles 支持英文逗号分隔，自动 trim、去空、去重。
  - 非 HTTP 直接调用或请求头缺失时，回退到原请求体/query 参数，保持既有测试、脚本和内部调用兼容。
- `AdminConfigController`
  - `approve`、`reject-review`、`cancel-review`、`publish` 改为优先使用 Admin 请求上下文 actor/roles。
  - `review-workspace` 改为优先使用 Admin 请求上下文 roles，再回退查询参数 roles。
- `AdminConfigControllerTest`
  - 新增动作接口 header 优先测试。
  - 新增 review-workspace header roles 测试。

## Header 契约

动作接口可由网关/IAM 注入：

```http
X-IntentHub-Actor: iam-approver
X-IntentHub-Roles: CONFIG_OPERATOR, CONFIG_APPROVER
```

语义：

- `X-IntentHub-Actor` 存在且非空时覆盖请求体 `actor`。
- `X-IntentHub-Roles` 存在且非空时覆盖请求体 `roles` 或 query `roles`。
- 请求头缺失时保留原兼容行为。

## 一致性审查

- 与 P2-17 一致：角色门禁仍由应用层 `CONFIG_APPROVER` / `CONFIG_PUBLISHER` 执行，接口层只负责解析调用方上下文。
- 与 P2-18 一致：工作台动作可见性与动作执行使用同一角色来源优先级，避免页面显示和实际执行不一致。
- 与 P2-19 一致：如果 header roles 不满足动作要求，仍返回统一 403 JSON。
- 与防腐层一致：本次只处理 Admin 配置治理身份上下文，不接触业务数据和下游业务系统。

## 验证证据

新增或扩展测试：

- `AdminConfigControllerTest.prefersAdminRequestContextHeadersForActorAndRoles`
  - 请求体中传入错误 actor/roles。
  - 请求头传入 `X-IntentHub-Actor=iam-approver` 与 `X-IntentHub-Roles=CONFIG_OPERATOR, CONFIG_APPROVER`。
  - 验证 approve 成功，且版本 `approvedBy=iam-approver`。
- `AdminConfigControllerTest.usesAdminRequestContextRolesForReviewWorkspace`
  - 请求头传入 `X-IntentHub-Roles=CONFIG_PUBLISHER`。
  - 验证 review-workspace 返回 `PUBLISH_COMPAT` 可用动作。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 32 个测试、基础设施层 59 个测试、接口层 26 个测试，合计 117 个测试。

## 风险与边界

- 当前 header 名称是 Intent Hub 内部最小契约，尚未对齐真实网关、IAM 或 JWT claim 标准。
- 当前未校验 header 的签名、来源可信度或网关转发边界；生产接入时必须确保外部用户不能伪造这些 header。
- 当前 roles 仍是全局角色，未包含 tenant/scene/environment 范围。
- 请求体/query roles 仍保留兼容，后续进入强 IAM 模式时应逐步废弃或仅允许测试环境使用。

## 后续建议

- 在网关或 Spring Security Filter 中完成真实认证，并只由可信边界注入 `X-IntentHub-Actor` / `X-IntentHub-Roles`。
- 增加 tenant/scene scoped role，例如 `CONFIG_APPROVER:tenant:scene` 或独立 claim。
- 在统一错误响应中增加 `traceId/requestId`，便于权限问题排查。
- 补 Admin API 文档，明确请求体 roles 为兼容字段，不作为生产权限来源。

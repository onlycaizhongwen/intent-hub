# Intent Hub P2-17 配置评审权限模型审查

## 审查结论

P2-17 配置评审权限模型最小闭环已完成，通过。

本阶段在不引入完整登录/鉴权体系的前提下，为 Admin 配置评审动作补最小角色门禁：审批类动作要求 `CONFIG_APPROVER`，发布动作要求 `CONFIG_PUBLISHER`。应用层保留旧内部调用兼容；Admin API 通过请求体 `roles` 传入角色后开始具备明确权限边界。

## 本次交付物

- `ConfigVersionActionRequest.roles`
  - Admin 请求体新增角色列表。
  - 旧构造器保留，兼容既有测试与非 HTTP 内部调用。
- `ConfigVersionAppService`
  - `approve(..., roles)` 要求 `CONFIG_APPROVER`。
  - `rejectReview(..., roles)` 要求 `CONFIG_APPROVER`。
  - `cancelReview(..., roles)` 要求 `CONFIG_APPROVER`。
  - `publish(..., roles)` 要求 `CONFIG_PUBLISHER`。
- `AdminConfigController`
  - approve/reject/cancel/publish 均透传请求角色到应用层。

## 权限规则

| 动作 | 最小角色 |
| --- | --- |
| approve | `CONFIG_APPROVER` |
| reject-review | `CONFIG_APPROVER` |
| cancel-review | `CONFIG_APPROVER` |
| publish | `CONFIG_PUBLISHER` |

应用层 roles 为 `null` 时视为内部兼容调用，不拦截；Admin API 无请求体或 roles 为空时，会按空角色列表处理并拦截受控动作。

## 验证证据

已新增/扩展测试：

- `ConfigVersionAppServiceTest.enforcesReviewAndPublishRolesWhenRolesAreProvided`
  - 错误角色审批被 `CONFIG_APPROVER` 门禁阻断。
  - 正确审批角色可 approve。
  - 审批角色不能 publish。
  - 发布角色可 publish。
- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - Controller approve 无正确角色会被阻断。
  - Controller publish 无 `CONFIG_PUBLISHER` 会被阻断。
  - 带正确角色后可发布。
- `AdminConfigControllerTest.exposesReviewRejectAndCancelContracts`
  - reject/cancel 均使用 `CONFIG_APPROVER` 通过。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 31 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 113 个测试。

## 一致性审查

- 与 P2-10 审批状态机一致：权限只决定能否执行动作，不改变状态流转规则。
- 与 P2-15 条件发布一致：publish 会先经过角色门禁，再进入快照 hash 校验与状态校验。
- 与 P2-16 审批元数据一致：审批人仍来自 actor，角色只用于授权判断。
- 与防腐层边界一致：权限模型只保护配置治理动作，不触碰业务数据或下游动作。

## 风险与边界

- 当前 roles 由请求体传入，尚未接入真实登录态、JWT、IAM 或网关鉴权。
- 当前角色粒度是全局最小模型，未区分 tenant、scene、环境和动作范围。
- 当前失败以 `SecurityException` 抛出，尚未映射为统一 HTTP 403 响应体。
- 当前 Admin Portal 可用动作仍是后端建议，尚未按用户角色过滤。

## 后续建议

- 接入真实身份来源，将 roles 从请求体迁移到登录态或网关上下文。
- 在 review-workspace 按角色过滤可用动作并返回权限阻断原因。
- 增加 tenant/scene 级权限模型，例如 `CONFIG_APPROVER:tenant:scene`。
- 增加统一异常处理，将权限失败映射为 403。

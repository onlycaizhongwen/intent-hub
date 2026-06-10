# Intent Hub P2-23 配置只读权限分层审查

## 审查结论

P2-23 配置只读权限分层最小闭环已完成，通过。

本阶段在 P2-21/P2-22 的 scoped role 基础上新增 `CONFIG_VIEWER`，把配置详情、校验、diff、dry-run、导出、GitOps 审查包、审计查询、配置对象列表和评审工作台等读操作纳入统一只读权限模型。

## 本次交付物

- `ConfigPermission`
  - 新增配置权限聚合工具。
  - 定义 `CONFIG_VIEWER`、`CONFIG_EDITOR`、`CONFIG_APPROVER`、`CONFIG_PUBLISHER`。
  - 统一表达只读、编辑、审批、发布权限校验。
- `ConfigRoleMatcher`
  - 新增 `hasAnyRole(...)`，支持多个角色候选的 scoped 匹配。
- `ConfigVersionAppService`
  - `get`、`validate`、`diff`、`dryRunPublish`、`exportBundle`、`exportGitOps` 支持只读 roles 重载。
- `ConfigObjectAppService`
  - `list` 支持只读 roles 重载。
- `ConfigAuditAppService`
  - `listVersionAudits` 支持只读 roles 重载。
- `ConfigReviewWorkspaceAppService`
  - 工作台入口要求具备只读权限。
  - 审批/发布动作仍按 `CONFIG_APPROVER` / `CONFIG_PUBLISHER` 过滤。
- `AdminConfigController`
  - 读接口接入 `roles` query 与 `X-IntentHub-Roles` header。
  - 保留直接调用兼容重载，HTTP 映射方法仍走受控路径。

## 角色语义

只读角色：

```text
CONFIG_VIEWER
CONFIG_VIEWER:demo:order-scene
CONFIG_VIEWER:demo:*
CONFIG_VIEWER:*:order-scene
CONFIG_VIEWER:*:*
```

继承读权限的角色：

```text
CONFIG_EDITOR
CONFIG_APPROVER
CONFIG_PUBLISHER
```

继承规则：

- `CONFIG_VIEWER` 可执行只读操作，但不能编辑、审批、发布。
- `CONFIG_EDITOR`、`CONFIG_APPROVER`、`CONFIG_PUBLISHER` 继承只读权限。
- 全局角色和 scoped role 都可继承只读权限。
- scoped role 继续使用 `ROLE:tenant:scene` 与 `*` 通配语义。

## 一致性审查

- 与 P2-19 一致：只读权限失败仍抛出 `SecurityException`，由接口层统一映射为 HTTP 403。
- 与 P2-20 一致：Admin HTTP 入口优先读取 `X-IntentHub-Roles`，再回退 query roles。
- 与 P2-21 一致：只读权限复用同一 scoped role 匹配语义。
- 与 P2-22 一致：编辑权限继续由 `CONFIG_EDITOR` 控制，只读权限不扩大编辑能力。

## 验证证据

新增或扩展测试：

- `ConfigVersionAppServiceTest.enforcesScopedViewerRoleForReadOnlyConfigOperationsWhenRolesAreProvided`
  - 错误 scene 的 viewer role 被拒绝。
  - `CONFIG_VIEWER:demo:order-scene` 可读取版本详情。
  - `CONFIG_EDITOR`、`CONFIG_APPROVER`、`CONFIG_PUBLISHER` 均可继承读权限。
  - diff、dry-run、export、GitOps 导出均覆盖只读权限。
- `ConfigVersionAppServiceTest.enforcesScopedViewerRoleForConfigObjectListAndAuditsWhenRolesAreProvided`
  - 配置对象列表和审计查询纳入只读权限。
- `AdminConfigControllerTest.mapsReadOnlyPermissionFailureToForbiddenHttpResponse`
  - 错误 scoped viewer role 调用版本详情返回 HTTP 403。
- `AdminConfigControllerTest.acceptsScopedViewerRoleFromAdminRequestContextHeaders`
  - `X-IntentHub-Roles=CONFIG_VIEWER:demo:order-scene` 可读取版本详情。
  - `CONFIG_APPROVER:demo:order-scene` 可继承读取配置对象列表。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 36 个测试、基础设施层 59 个测试、接口层 31 个测试，合计 126 个测试。

## 风险与边界

- 当前 `CONFIG_VIEWER` 仍来自请求上下文中的字符串角色，尚未接入 Spring Security/JWT/IAM。
- 当前只读权限按 tenant/scene 控制，尚未按对象类型、字段级敏感度或导出范围细分。
- 版本创建、导入、提交评审、回滚仍未纳入更细权限模型。
- 权限失败尚未写入安全审计事件或独立指标。

## 后续建议

- 接入 Spring Security/JWT Filter，把可信 IAM claim 映射为 scoped role。
- 为权限失败增加安全审计事件和 Prometheus 指标。
- 拆分对象类型级权限，例如 `CONFIG_ROUTE_EDITOR`、`CONFIG_ACTION_EDITOR`。
- 继续补结构化 review history，为 Admin Portal 提供完整审批轨迹。

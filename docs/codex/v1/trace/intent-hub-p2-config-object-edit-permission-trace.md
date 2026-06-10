# Intent Hub P2-22 配置对象编辑权限审查

## 审查结论

P2-22 配置对象编辑权限最小闭环已完成，通过。

本阶段承接 P2-21 的 tenant/scene scoped role 语义，把配置对象的单条写入、批量写入和删除纳入同一权限模型，避免 Admin 管理端绕开配置评审动作已经建立的多租户范围约束。

## 本次交付物

- `ConfigObjectAppService`
  - 新增 `CONFIG_EDITOR` 写入角色门禁。
  - `upsert`、`bulkUpsert`、`delete` 均支持 roles 重载。
  - roles 为 `null` 时保留应用层内部兼容调用；Admin API 传入空 roles 时按无权限处理。
- `ConfigObjectRequest` / `ConfigObjectBulkRequest`
  - 新增 `roles` 字段。
  - 保留旧构造器，降低已有单元测试和内部直接调用迁移成本。
- `AdminConfigController`
  - 配置对象 upsert/bulk/delete 调用 `AdminRequestContext` 解析 actor 与 roles。
  - 支持 `X-IntentHub-Actor` 和 `X-IntentHub-Roles` 优先于请求体/query。
- `AdminConfigControllerTest`
  - 覆盖配置对象编辑权限失败的结构化 403。
  - 覆盖 Header scoped editor role 成功写入配置对象。

## 角色语义

兼容角色：

```text
CONFIG_EDITOR
```

范围角色：

```text
CONFIG_EDITOR:demo:order-scene
CONFIG_EDITOR:demo:*
CONFIG_EDITOR:*:order-scene
CONFIG_EDITOR:*:*
```

匹配规则复用 `ConfigRoleMatcher`：

- 全局 `CONFIG_EDITOR` 可编辑所有 tenant/scene。
- scoped role 必须使用 `ROLE:tenant:scene` 三段式。
- tenant 或 scene 可用 `*` 通配。
- 不同 tenant/scene 的 scoped role 不允许越权。

## 一致性审查

- 与 P2-17 一致：应用层仍是权限判定位置，接口层只负责传递上下文。
- 与 P2-19 一致：权限失败仍抛出 `SecurityException`，由接口层统一映射为 HTTP 403 JSON。
- 与 P2-20 一致：Admin 请求优先读取 `X-IntentHub-Actor` 与 `X-IntentHub-Roles`，再回退请求体/query。
- 与 P2-21 一致：配置对象编辑与审批/发布动作复用同一个 scoped role 匹配器，避免多套范围判断规则。

## 验证证据

新增或扩展测试：

- `ConfigVersionAppServiceTest.enforcesScopedEditorRoleWhenRolesAreProvidedForConfigObjects`
  - 错误 scene 的 `CONFIG_EDITOR:demo:other-scene` 被拒绝。
  - `CONFIG_EDITOR:demo:order-scene` 允许单条写入。
  - `CONFIG_EDITOR:demo:*` 允许批量写入。
  - `CONFIG_EDITOR:*:order-scene` 允许删除。
- `AdminConfigControllerTest.mapsConfigObjectEditPermissionFailureToForbiddenHttpResponse`
  - 错误 scoped editor role 调用对象写入返回 HTTP 403。
  - 响应包含 `code=FORBIDDEN`、`status=403` 和明确权限提示。
- `AdminConfigControllerTest.acceptsScopedEditorRoleFromAdminRequestContextHeaders`
  - `X-IntentHub-Roles=CONFIG_EDITOR:demo:order-scene` 可覆盖请求体中的错误角色并完成写入。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 34 个测试、基础设施层 59 个测试、接口层 29 个测试，合计 122 个测试。

## 风险与边界

- 当前 `CONFIG_EDITOR` 仍来自请求上下文中的字符串角色，尚未接入 Spring Security/JWT/IAM 策略源。
- 当前只覆盖配置对象写入和删除；配置导出、审计查询、版本读取、diff/dry-run 等只读动作尚未拆分只读权限。
- 当前权限失败没有额外写入安全审计事件；只能通过 HTTP 响应、测试和调用链日志间接观测。
- 当前配置对象编辑仍只限制 `DRAFT` 版本，未引入字段级或对象类型级权限。

## 后续建议

- 接入 Spring Security/JWT Filter，把可信 IAM claim 映射为 `CONFIG_EDITOR:tenant:scene` 等 scoped role。
- 增加只读权限分层，例如 `CONFIG_VIEWER` 控制导出、diff、dry-run、审计查询。
- 增加权限失败安全事件或指标，便于发现越权尝试。
- 后续可按对象类型拆分更细权限，例如策略编辑、路由编辑、下游动作编辑分离。

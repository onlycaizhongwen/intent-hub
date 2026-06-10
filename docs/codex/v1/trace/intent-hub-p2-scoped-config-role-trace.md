# Intent Hub P2-21 tenant/scene 级配置权限审查

## 审查结论

P2-21 tenant/scene 级配置权限最小闭环已完成，通过。

本阶段承接 P2-17 到 P2-20 的配置评审权限模型，在保留全局角色兼容的基础上，新增 tenant/scene scoped role 匹配能力，避免多租户场景下只能使用全局 `CONFIG_APPROVER` / `CONFIG_PUBLISHER`。

## 本次交付物

- `ConfigRoleMatcher`
  - 新增配置治理角色匹配器。
  - 支持旧全局角色：`CONFIG_APPROVER`、`CONFIG_PUBLISHER`。
  - 支持 scoped role：`ROLE:tenantId:sceneId`。
  - 支持通配范围：`ROLE:tenantId:*`、`ROLE:*:sceneId`、`ROLE:*:*`。
- `ConfigVersionAppService`
  - approve/reject/cancel/publish 的角色校验改为 tenant/scene 感知。
  - 权限失败提示同时给出全局角色和具体 scoped role 示例。
- `ConfigReviewWorkspaceAppService`
  - 工作台动作过滤复用同一 scoped role 语义。
  - 页面可见动作与动作接口执行权限保持一致。

## 角色语义

兼容角色：

```text
CONFIG_APPROVER
CONFIG_PUBLISHER
```

范围角色：

```text
CONFIG_APPROVER:demo:order-scene
CONFIG_APPROVER:demo:*
CONFIG_APPROVER:*:order-scene
CONFIG_APPROVER:*:*
CONFIG_PUBLISHER:demo:order-scene
```

匹配规则：

- 全局角色直接放行，保持旧脚本和旧测试兼容。
- scoped role 必须由 3 段组成：`role:tenant:scene`。
- tenant 或 scene 可使用 `*` 表示通配。
- 不同 tenant/scene 的 scoped role 不允许越权。

## 一致性审查

- 与 P2-17 一致：动作仍区分审批类 `CONFIG_APPROVER` 和发布类 `CONFIG_PUBLISHER`。
- 与 P2-18 一致：工作台动作过滤和动作接口使用同一角色匹配器。
- 与 P2-19 一致：权限不足仍返回 403，只是 message 增加 scoped role 提示。
- 与 P2-20 一致：header、请求体、query 中解析出的 roles 均可使用 scoped role。
- 与多租户设计一致：tenant/scene 已进入权限匹配，但不改变 DB schema 和配置版本主键。

## 验证证据

新增或扩展测试：

- `ConfigVersionAppServiceTest.acceptsTenantSceneScopedReviewAndPublishRoles`
  - 错误 scene 的 approver role 被拒绝。
  - `CONFIG_APPROVER:demo:order-scene` 可批准。
  - `CONFIG_PUBLISHER:demo:*` 可发布。
- `ConfigVersionAppServiceTest.filtersReviewWorkspaceActionsWhenRolesAreProvided`
  - scoped approver role 可看到审批动作。
  - 错误角色隐藏审批动作，并返回 scoped role 提示。
- `AdminConfigControllerTest.acceptsScopedRolesFromAdminRequestContextHeaders`
  - `X-IntentHub-Roles=CONFIG_APPROVER:demo:order-scene` 可完成 approve。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 33 个测试、基础设施层 59 个测试、接口层 27 个测试，合计 119 个测试。

## 风险与边界

- 当前 scoped role 仍来自调用上下文中的字符串列表，尚未接入真实 IAM 策略表、JWT claim 标准或 Spring Security authority。
- 当前只覆盖配置评审动作，不覆盖配置对象编辑、导出、审计查询等只读/写入权限。
- 当前没有权限审计明细表，权限失败只通过响应和日志链路间接观察。
- 当前 role 字符串使用 `:` 分隔，后续如果企业 IAM 使用不同 claim 结构，需要在接口层做映射。

## 后续建议

- 接入真实 Spring Security/JWT Filter，把 IAM claim 映射为 scoped role。
- 将 `SUBMIT_REVIEW`、配置对象编辑、GitOps 导出、审计查询纳入更细权限模型。
- 增加权限失败审计或安全事件指标。
- 后续可考虑从字符串角色升级为结构化 `ConfigPermission` 对象，便于环境、租户、场景和动作范围扩展。

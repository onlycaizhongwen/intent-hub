# Intent Hub P2-24 权限失败安全审计审查

## 审查结论

P2-24 权限失败安全审计最小闭环已完成，通过。

本阶段承接 P2-21 至 P2-23 的 scoped role 与只读/编辑/审批/发布权限模型，在权限拒绝时补充 `CONFIG_PERMISSION_DENIED` 审计事件。权限失败仍通过既有 `SecurityException` 抛出，并由接口层统一映射为 HTTP 403 JSON，外部响应契约不变。

## 本次交付物

- `ConfigPermission`
  - 权限拒绝时通过 `AuditLogPort` 写入 `CONFIG_PERMISSION_DENIED`。
  - 审计 detail 包含 `action`、`requiredRole`、`roleHint`、`roles`。
  - roles 为 `null` 时记录 `__internal__`，空列表记录 `__empty__`，其余记录去重后的角色列表。
- `ConfigVersionAppService`
  - 版本读取、校验、diff、dry-run、导出、GitOps 导出、审批、驳回、撤回和发布的权限检查接入审计端口。
- `ConfigObjectAppService`
  - 配置对象读取与写入权限失败接入审计端口。
- `ConfigAuditAppService`
  - 审计查询权限失败接入审计端口。
- `ConfigReviewWorkspaceAppService`
  - 工作台读取权限失败接入审计端口。
- `IntentHubBeanConfiguration`
  - `ConfigReviewWorkspaceAppService` Spring Bean 注入 `AuditLogPort`，保证运行时工作台拒绝也能留痕。

## 审计事件

事件固定字段：

```text
action=CONFIG_PERMISSION_DENIED
targetType=CONFIG_PERMISSION
targetId={sceneId}
actor=unknown
```

detail 字段：

```text
action={被拒绝的业务动作}
requiredRole={需要的基础角色}
roleHint={全局角色或 tenant/scene scoped role 提示}
roles={调用方提供的角色快照}
```

当前 actor 暂记为 `unknown`，因为权限工具位于应用层公共校验边界，尚未接入统一认证上下文。后续接入 Spring Security/JWT 后，可将可信 actor 传入权限校验并写入审计。

## 一致性审查

- 与 P2-19 一致：权限失败仍返回统一 HTTP 403 JSON，响应结构未改变。
- 与 P2-20 一致：角色来源仍由 Admin 请求上下文解析后传入应用层。
- 与 P2-21 一致：审计记录保留 scoped role 匹配提示，便于排查 tenant/scene 越权尝试。
- 与 P2-22 一致：配置对象编辑失败也会进入同一安全审计事件。
- 与 P2-23 一致：只读权限失败、继承读权限失败均使用同一事件口径。

## 验证证据

新增或扩展测试：

- `ConfigVersionAppServiceTest.enforcesReviewAndPublishRolesWhenRolesAreProvided`
  - 审批角色不足会写入 `CONFIG_PERMISSION_DENIED`。
  - 发布角色不足会写入 `CONFIG_PERMISSION_DENIED`。
- `ConfigVersionAppServiceTest.enforcesScopedViewerRoleForReadOnlyConfigOperationsWhenRolesAreProvided`
  - 只读 scoped role 越权会写入拒绝审计。
- `ConfigVersionAppServiceTest.enforcesScopedEditorRoleWhenRolesAreProvidedForConfigObjects`
  - 配置对象编辑 scoped role 越权会写入拒绝审计。
- `ConfigVersionAppServiceTest.enforcesScopedViewerRoleForConfigObjectListAndAuditsWhenRolesAreProvided`
  - 配置对象列表只读越权会写入拒绝审计。
- `AdminConfigControllerTest.mapsPermissionFailureToForbiddenHttpResponse`
  - HTTP 403 响应不变，同时审计端口记录审批拒绝事件。
- `AdminConfigControllerTest.mapsConfigObjectEditPermissionFailureToForbiddenHttpResponse`
  - HTTP 403 响应不变，同时审计端口记录对象编辑拒绝事件。
- `AdminConfigControllerTest.mapsReadOnlyPermissionFailureToForbiddenHttpResponse`
  - HTTP 403 响应不变，同时审计端口记录只读拒绝事件。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 36 个测试、基础设施层 59 个测试、接口层 31 个测试，合计 126 个测试。

## 风险与边界

- 当前拒绝事件 actor 暂为 `unknown`，真实用户身份仍需 Spring Security/JWT/IAM 接入后补齐。
- 当前未新增独立安全事件表或指标，复用 `audit_log` 与 `AuditLogPort`。
- 当前审计 detail 记录角色快照，不记录原始 token、header 全量或敏感凭据。
- 当前没有新增限流、告警规则或安全看板；拒绝事件的告警消费仍待后续观测治理补充。

## 后续建议

- 接入 Spring Security/JWT Filter，把可信 actor 与 scoped role 从 IAM claim 映射到 Admin 请求上下文。
- 为 `CONFIG_PERMISSION_DENIED` 增加指标与告警，例如按 tenant/scene/action 统计短时间越权尝试。
- 后续可将 actor、source IP、traceId/requestId 等可信上下文纳入拒绝审计 detail。
- 继续补结构化 review history，为审批过程和权限拒绝形成统一可追溯视图。

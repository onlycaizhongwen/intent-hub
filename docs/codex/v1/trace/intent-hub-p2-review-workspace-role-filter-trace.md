# Intent Hub P2-18 评审工作台按角色过滤动作审查

## 审查结论

P2-18 评审工作台按角色过滤动作最小闭环已完成，通过。

本阶段承接 P2-17 的动作级角色门禁，把同一套 `CONFIG_APPROVER` / `CONFIG_PUBLISHER` 规则前移到 Admin 评审工作台数据面：后端不仅在执行动作时阻断无权限调用，也会在 `review-workspace` 中隐藏调用方无权执行的受控动作，并返回明确的权限阻断原因。

## 本次交付物

- `ConfigReviewWorkspaceAppService`
  - 新增带 `roles` 的 `getWorkspace(...)` 重载。
  - 保留旧 `getWorkspace(...)` 兼容内部调用，旧调用不做角色过滤。
  - 受控动作按角色过滤：
    - `APPROVE` / `REJECT_REVIEW` / `CANCEL_REVIEW` / `CANCEL_APPROVAL` 要求 `CONFIG_APPROVER`。
    - `PUBLISH` / `PUBLISH_COMPAT` 要求 `CONFIG_PUBLISHER`。
  - 被过滤动作会进入 `blockedReasons`，例如 `APPROVE requires role CONFIG_APPROVER`。
- `AdminConfigController`
  - `GET /api/v1/admin/config/versions/{version}/review-workspace` 新增可选查询参数 `roles`。
  - Admin HTTP 缺省 roles 时按空角色处理，不展示受控动作。

## 权限语义

| 工作台动作 | 最小角色 |
| --- | --- |
| `SUBMIT_REVIEW` | 暂不受控 |
| `APPROVE` | `CONFIG_APPROVER` |
| `REJECT_REVIEW` | `CONFIG_APPROVER` |
| `CANCEL_REVIEW` | `CONFIG_APPROVER` |
| `CANCEL_APPROVAL` | `CONFIG_APPROVER` |
| `PUBLISH` | `CONFIG_PUBLISHER` |
| `PUBLISH_COMPAT` | `CONFIG_PUBLISHER` |
| `VIEW_DIFF` / `DRY_RUN` / `EXPORT_GITOPS` / `ROLLBACK_TARGET` | 暂不受控 |

## 一致性审查

- 与 P2-17 一致：工作台动作过滤复用同一组角色常量与动作语义，不引入第二套权限模型。
- 与 P2-11 一致：`review-workspace` 仍是页面可消费的数据面，继续返回版本、校验、dry-run、审计、动作和阻断原因。
- 与兼容性要求一致：应用层旧方法保留，内部调用不因未传 roles 被破坏；Admin API 缺省 roles 时进入受控模式。
- 与防腐层边界一致：本次只过滤配置治理动作，不读取、不写入业务数据，也不触碰下游业务执行。

## 验证证据

新增或扩展测试：

- `ConfigVersionAppServiceTest.filtersReviewWorkspaceActionsWhenRolesAreProvided`
  - 验证旧应用层调用仍返回未过滤动作。
  - 验证错误角色不展示审批动作，并返回权限阻断原因。
  - 验证 `CONFIG_APPROVER` 可看到审批类动作。
- `AdminConfigControllerTest.exposesReviewWorkspaceForAdminPortal`
  - 验证 Admin 工作台缺省 roles 不展示 `PUBLISH_COMPAT`。
  - 验证 `CONFIG_PUBLISHER` 可看到发布类动作。
  - 验证 `CONFIG_APPROVER` 与 `CONFIG_PUBLISHER` 对 `CANCEL_APPROVAL` / `PUBLISH` 分别生效。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 32 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 114 个测试。

## 风险与边界

- roles 仍来自查询参数，尚未接入真实登录态、JWT、IAM 或网关鉴权上下文。
- 权限失败仍由动作接口抛出 `SecurityException`，尚未统一映射为 HTTP 403 响应体。
- 当前角色粒度仍是全局最小模型，未区分 tenant、scene、环境和动作范围。
- `SUBMIT_REVIEW`、`ROLLBACK_TARGET`、只读类动作暂未纳入角色门禁，后续需要结合产品流程决定是否受控。

## 后续建议

- 接入真实登录态/IAM，将 roles 从请求体/查询参数迁移到认证上下文。
- 增加统一异常处理，把权限失败映射为标准 403 JSON 响应。
- 扩展 tenant/scene 级权限，例如 `CONFIG_APPROVER:tenant:scene`。
- 为 `SUBMIT_REVIEW`、`ROLLBACK_TARGET` 和只读 GitOps 导出补更细的权限策略。

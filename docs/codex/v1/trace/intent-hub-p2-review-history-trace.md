# Intent Hub P2-30 结构化 review history 审查

## 审查结论

P2-30 结构化 review history 最小闭环已完成，通过。

本阶段承接 P2-11 Admin 配置评审工作台聚合契约，以及 P2-12 至 P2-16 的评审流转、审批快照哈希和审批元数据能力。目标是让工作台不再只暴露原始审计日志，而是直接返回前端可消费的结构化评审历史，便于展示审批时间线、发布轨迹和关键审计字段。

## 本次交付物

- `ConfigReviewHistoryEntry`
  - 新增结构化评审历史记录。
  - 字段包含 `auditId`、`stage`、`action`、`actor`、`status`、`reason`、`snapshotHash`、`requiredRole`、`alternativeRole`、`objectType`、`occurredAt` 和 `detail`。
- `ConfigReviewWorkspace`
  - 新增 `reviewHistory` 字段。
  - 原始 `audits` 字段保留，兼容已有调用方。
- `ConfigReviewWorkspaceAppService`
  - 从版本审计记录派生结构化 review history。
  - 已映射 `CONFIG_REVIEW_SUBMITTED`、`CONFIG_APPROVED`、`CONFIG_REVIEW_REJECTED`、`CONFIG_REVIEW_CANCELLED`、`CONFIG_PUBLISHED`、`CONFIG_PERMISSION_DENIED`。
- 测试
  - 应用层覆盖工作台返回 `PUBLISHED`、`APPROVED`、`REVIEW_SUBMITTED` 结构化阶段。
  - 接口层覆盖 `review-workspace` JSON 响应中的 `reviewHistory`。

## 映射语义

| 审计 action | review stage | status |
| --- | --- | --- |
| `CONFIG_REVIEW_SUBMITTED` | `REVIEW_SUBMITTED` | `REVIEWING` |
| `CONFIG_APPROVED` | `APPROVED` | `APPROVED` |
| `CONFIG_REVIEW_REJECTED` | `REJECTED` | `DRAFT` |
| `CONFIG_REVIEW_CANCELLED` | `CANCELLED` | `DRAFT` |
| `CONFIG_PUBLISHED` | `PUBLISHED` | `PUBLISHED` |
| `CONFIG_PERMISSION_DENIED` | `PERMISSION_DENIED` | 来自审计 detail |

## 一致性审查

- 与 P2-11 一致：`review-workspace` 仍是 Admin Portal 的聚合数据面，新增 `reviewHistory` 不移除 `audits`。
- 与 P2-12 一致：驳回和撤回可映射为 `REJECTED`、`CANCELLED`，并通过 `reason` 支撑前端展示。
- 与 P2-13/P2-14 一致：批准事件可暴露 `snapshotHash`，用于关联审批快照。
- 与 P2-24/P2-29 一致：权限拒绝事件的 `requiredRole`、`alternativeRole`、`objectType` 可进入结构化历史；但当前版本工作台的版本审计查询通常只查询 `CONFIG_VERSION` 目标，因此 `CONFIG_PERMISSION_DENIED` 只有在审计归属落到版本维度时才会出现在当前列表中。

## 验证证据

已执行定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-interfaces -am '-Dtest=ConfigVersionAppServiceTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：通过。应用层 25 个测试、接口层 21 个测试通过。

已执行三层回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 38 个测试、基础设施层 61 个测试、接口层 40 个测试，合计 139 个测试。

## 风险与边界

- 当前 review history 从审计日志派生，不新增独立审批历史表；复杂多人会签、审批评论线程和 SLA 统计仍需后续模型扩展。
- 当前不改变审计写入策略；权限拒绝事件是否出现在某个版本的 review history，取决于审计事件本身的 target 归属。
- 当前保留 `detail` 原始字段，后续若引入更多敏感上下文，仍需遵守审计脱敏规则。

## 后续建议

- Admin Portal 时间线优先消费 `reviewHistory`，仅在排障视图展示原始 `audits`。
- 后续补真实 IAM/OIDC/JWKS 时，将 actor、roles、tenant/scene 权限来源补强到统一可信上下文。
- 如果进入多人审批，建议新增审批任务或审批意见模型，而不是继续把所有状态压入审计 detail。

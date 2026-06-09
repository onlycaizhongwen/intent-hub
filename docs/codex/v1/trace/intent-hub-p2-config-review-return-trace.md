# Intent Hub P2-12 配置评审驳回与撤回审查

## 审查结论

P2-12 配置评审驳回与撤回最小闭环已完成，通过。

本阶段补齐配置评审失败或范围变化时的回退路径，让 P2-10/P2-11 的审批链路不再只能正向推进。评审人员可以驳回 `REVIEWING` 版本回到 `DRAFT`，审批后也可以在发布前撤回批准回到 `DRAFT`，从而恢复配置编辑能力。

## 本次交付物

- `ConfigVersionAppService.rejectReview(...)`
  - 仅允许 `REVIEWING` 版本被驳回。
  - 驳回后状态回到 `DRAFT`。
  - 写入审计动作 `CONFIG_REVIEW_REJECTED`，保留 reason。
- `ConfigVersionAppService.cancelReview(...)`
  - 允许 `REVIEWING` 或 `APPROVED` 版本撤回到 `DRAFT`。
  - 写入审计动作 `CONFIG_REVIEW_CANCELLED`，保留 previousStatus 与 reason。
- `ConfigVersionActionRequest.reason`
  - Action 请求体新增可选 reason 字段。
  - 保持旧构造方式兼容，仅传 actor 仍可使用。
- Admin API
  - `POST /api/v1/admin/config/versions/{version}/reject-review`
  - `POST /api/v1/admin/config/versions/{version}/cancel-review`
- 工作台动作扩展
  - `REVIEWING`：新增 `REJECT_REVIEW`、`CANCEL_REVIEW`。
  - `APPROVED`：新增 `CANCEL_APPROVAL`。

## 验证证据

已新增应用层测试：

- `ConfigVersionAppServiceTest.returnsReviewingOrApprovedVersionToDraft`
  - 验证 `REVIEWING -> DRAFT` 驳回。
  - 验证 `APPROVED -> DRAFT` 撤回。
  - 验证审计动作包含 `CONFIG_REVIEW_REJECTED` 与 `CONFIG_REVIEW_CANCELLED`。

已新增接口层测试：

- `AdminConfigControllerTest.exposesReviewRejectAndCancelContracts`
  - 验证 `reject-review` 和 `cancel-review` Controller 契约。
- 扩展 `AdminConfigControllerTest.exposesReviewWorkspaceForAdminPortal`
  - 验证 `REVIEWING` 工作台暴露 `REJECT_REVIEW` 与 `CANCEL_REVIEW`。
  - 验证 `APPROVED` 工作台暴露 `PUBLISH` 与 `CANCEL_APPROVAL`。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 28 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 110 个测试。

## 一致性审查

- 与编辑约束一致：回退到 `DRAFT` 后才允许配置对象继续编辑。
- 与发布门禁一致：`REVIEWING` 仍不能直接发布，必须 approve；驳回后回到草稿。
- 与审计能力一致：驳回和撤回都进入既有 audit log，不新增表结构。
- 与运行时识别链路隔离：`REVIEWING/APPROVED/DRAFT` 均不会被运行时作为 `PUBLISHED` 配置读取。

## 风险与边界

- 当前 reason 是审计 detail 字段，不是结构化审批意见表。
- 当前没有区分“主动撤回”和“管理员强制退回”的权限。
- 当前没有审批快照哈希，无法证明驳回/批准时看到的配置内容未漂移。
- 当前没有多人审批、会签、超时自动撤回等高级流程。

## 后续建议

- 增加审批快照哈希：approve/publish 时校验配置内容未漂移。
- 增加 reviewer/publisher 权限模型，避免同一人自审自发。
- 增加结构化 review comment 和 review history 查询接口。
- 将工作台可用动作与权限模型结合，避免前端只依赖状态判断。

# Intent Hub P2-16 审批元数据强字段化审查

## 审查结论

P2-16 审批元数据强字段化已完成，通过。

本阶段在 P2-14 `approved_snapshot_hash` 强字段基础上，继续为 `config_version` 补充 `approved_by` 与 `approved_at`。审批人、审批时间和批准快照哈希组成一组可直接查询的审批快照元数据，减少后续 Admin Portal、审计看板和权限模型对 audit detail 的反向推断依赖。

## 本次交付物

- `config_version.approved_by`
- `config_version.approved_at`
- Flyway `V5__p2_config_approval_metadata.sql`
- `ConfigVersionInfo.approvedBy/approvedAt`
- memory/JDBC 配置版本仓储写入和读取审批元数据
- 版本详情与 review-workspace 通过 `ConfigVersionInfo` 显式返回审批人和审批时间

## 验证证据

已新增/扩展测试：

- `ConfigVersionAppServiceTest.blocksPublishWhenApprovedSnapshotChanged`
  - 验证 approve 后 `approvedBy=approver`。
  - 验证 `approvedAt` 非空。
- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - 验证版本详情返回 `approvedBy/approvedAt`。
- `AdminConfigControllerTest.exposesReviewWorkspaceForAdminPortal`
  - 验证 review-workspace 返回 `approvedBy/approvedAt`。
- `JdbcSceneConfigRepositoryTest`
  - 测试 schema 补齐 `approved_by/approved_at`，保持 JDBC 查询兼容。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 30 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 112 个测试。

## 一致性审查

- 与 P2-14 一致：审批元数据和 `approvedSnapshotHash` 均在 approve 时写入版本强字段。
- 与 P2-15 一致：发布 expected hash 仍只校验配置内容快照，不改变审批人/审批时间语义。
- 与审计一致：`CONFIG_APPROVED` 审计仍保留，强字段用于查询和工作台展示，不替代审计流水。
- 与运行时识别隔离：新增字段只服务配置治理，不影响 `PUBLISHED` 配置读取和识别链路。

## 风险与边界

- 当前没有 `approved_role`、审批意见或审批批次字段。
- 当前没有多人审批模型，`approvedBy/approvedAt` 表示最近一次单人批准。
- 驳回/撤回回到 `DRAFT` 时暂未清空审批元数据，后续如果产品要求“回草稿即清空批准痕迹”，需要明确语义后再改。
- 权限模型尚未落地，当前 actor 仍由请求方传入。

## 后续建议

- 增加审批权限模型，限制谁可以 approve/reject/cancel/publish。
- 补结构化 review comment/history，承接多人协作评审。
- 明确撤回审批时是否清空 `approved_by/approved_at/approved_snapshot_hash`。
- 在 Admin Portal 展示审批人、审批时间、批准 hash 和当前 hash 的对比状态。

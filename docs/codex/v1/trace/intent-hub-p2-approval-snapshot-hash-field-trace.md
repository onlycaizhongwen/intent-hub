# Intent Hub P2-14 审批快照哈希强字段化审查

## 审查结论

P2-14 审批快照哈希强字段化已完成，通过。

本阶段将 P2-13 中依赖审计 detail 的 `snapshotHash` 升级为 `config_version.approved_snapshot_hash` 强字段，并在版本详情与评审工作台中显式返回当前配置哈希和已批准哈希。发布 `APPROVED` 版本时优先使用强字段校验，旧数据仍兼容读取审计 detail。

## 本次交付物

- `config_version.approved_snapshot_hash`
  - 新增 Flyway `V4__p2_config_approval_snapshot_hash.sql`。
  - JDBC 查询、映射与更新均支持该字段。
- `ConfigVersionInfo`
  - 新增 `approvedSnapshotHash` 与 `currentSnapshotHash`。
  - 保留兼容构造器，降低既有测试和调用方改造成本。
- `ConfigVersionAppService`
  - `get(...)` 返回当前配置包哈希。
  - `approve(...)` 批准时写入强字段。
  - `publish(...)` 对 `APPROVED` 版本优先使用强字段做漂移校验，并兼容旧审计 detail。
- `review-workspace`
  - 通过聚合版本对象显式暴露当前哈希与批准哈希，方便 Admin Portal 做二次确认和差异提示。

## 验证证据

已扩展应用层、基础设施层和接口层测试：

- `ConfigVersionAppServiceTest.blocksPublishWhenApprovedSnapshotChanged`
  - 验证批准后 `approvedSnapshotHash` 与 `currentSnapshotHash` 非空且一致。
  - 验证批准后配置漂移仍会阻断发布。
- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - 验证版本详情返回批准哈希与当前哈希。
- `AdminConfigControllerTest.exposesReviewWorkspaceForAdminPortal`
  - 验证评审工作台可见批准哈希与当前哈希。
- `JdbcSceneConfigRepositoryTest`
  - 测试 schema 已补 `approved_snapshot_hash` 字段，覆盖 JDBC 兼容。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 29 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 111 个测试。

## 一致性审查

- 与 P2-13 一致：快照哈希仍在批准时生成，发布前仍比对批准快照与当前配置包。
- 与历史数据兼容：如果 `approved_snapshot_hash` 为空，发布校验仍可回退到最近一次 `CONFIG_APPROVED.detail.snapshotHash`。
- 与评审工作台一致：前端不再需要从审计记录中解析 hash，可直接读取版本对象字段。
- 与运行时识别隔离：新增字段只服务配置治理，不影响已发布配置读取、识别路径和下游动作执行。

## 风险与边界

- 当前仅固化 `approved_snapshot_hash`，尚未新增 `approved_by`、`approved_at` 强字段。
- 当前 canonical 逻辑仍为最小稳定字符串化，不是标准 JSON Canonicalization Scheme。
- 当前发布接口尚未要求调用方提交 `expectedSnapshotHash`，前端二次确认仍需后续补齐。
- 数据库层尚未增加防绕过约束，生产级治理仍依赖 DB 权限、审计和 GitOps 流程共同约束。

## 后续建议

- 补 `approved_by`、`approved_at` 字段，形成完整审批强查询模型。
- 在 publish 请求中加入 `expectedSnapshotHash`，实现条件发布和前端二次确认。
- 引入标准 JSON canonicalization，减少跨语言、跨导出工具的 hash 差异。
- 补审批权限模型、多人审批和结构化 review comment/history。

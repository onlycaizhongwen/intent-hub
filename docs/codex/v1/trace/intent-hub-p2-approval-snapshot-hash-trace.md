# Intent Hub P2-13 审批快照哈希审查

## 审查结论

P2-13 审批快照哈希最小闭环已完成，通过。

本阶段补齐配置批准后的内容漂移防护：`approve` 时计算当前配置包 SHA-256 快照哈希并写入审计，发布 `APPROVED` 版本时重新计算当前配置包哈希并与最近一次批准快照比对。如果批准后配置内容发生变化，发布会被阻断。

## 本次交付物

- `ConfigVersionAppService.approve(...)`
  - 批准前计算配置包快照哈希。
  - 审计动作 `CONFIG_APPROVED` 的 detail 增加 `snapshotHash`。
- `ConfigVersionAppService.publish(...)`
  - 当版本状态为 `APPROVED` 时，要求批准快照哈希存在。
  - 发布前重新计算当前配置包哈希。
  - 如当前哈希与批准哈希不一致，抛出 `approved config snapshot has changed`。
- 配置包 canonical hash
  - 对 intent、slot、synonym、strategy、route、downstream action 列表做稳定字符串化。
  - Map key 排序后参与 SHA-256，降低字段顺序造成的误判。

## 验证证据

已新增应用层测试：

- `ConfigVersionAppServiceTest.blocksPublishWhenApprovedSnapshotChanged`
  - 先提交评审并批准配置版本。
  - 再模拟底层配置内容漂移。
  - 发布时断言被 `approved config snapshot has changed` 阻断。

已扩展接口层测试：

- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - 验证 `CONFIG_APPROVED` 审计 detail 中包含 `snapshotHash`。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 29 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 111 个测试。

## 一致性审查

- 与 P2-10 审批状态机一致：只对 `APPROVED` 发布路径强制校验快照，历史 DRAFT 兼容发布路径不受影响。
- 与 P2-12 回退一致：驳回/撤回回到 `DRAFT` 后，后续重新提交和批准会生成新的快照哈希。
- 与审计能力兼容：快照哈希存放在既有 audit log detail，不新增 DB migration。
- 与运行时识别链路隔离：哈希仅服务配置治理，不影响 `PUBLISHED` 配置读取和识别请求。

## 风险与边界

- 当前快照哈希存储在审计 detail 中，不是独立强约束字段；后续如果审计归档或清理，需要保留最近批准快照。
- 当前 canonical 字符串化是最小实现，不是 RFC 标准 JSON Canonicalization Scheme。
- 当前没有把 snapshotHash 暴露进工作台模型的专门字段，前端可先从审计记录读取。
- 当前不防止直接绕过应用层修改数据库并同时伪造审计记录；生产级治理仍需 DB 权限和 GitOps 流程约束。

## 后续建议

- 在 `config_version` 增加 `approved_snapshot_hash`、`approved_by`、`approved_at` 字段，形成强查询字段。
- 引入标准 JSON canonicalization 或基于数据库导出 JSON 的稳定哈希。
- 在 `review-workspace` 中显式返回 `approvedSnapshotHash` 与 `currentSnapshotHash`。
- 将 publish 改为 `expectedSnapshotHash` 条件发布，配合前端二次确认。

# Intent Hub P2-15 发布 expectedSnapshotHash 条件校验审查

## 审查结论

P2-15 发布 `expectedSnapshotHash` 条件校验已完成，通过。

本阶段在 P2-14 已显式返回 `currentSnapshotHash` 的基础上，为发布接口补充调用方期望快照哈希校验。Admin Portal 或 API 调用方可以把工作台读取到的当前哈希随发布请求提交，服务端发布前再次比对当前配置包哈希，避免用户基于旧页面或旧快照误发布已经变化的配置。

## 本次交付物

- `ConfigVersionAppService.publish(...)`
  - 保留原有 `publish(tenantId, sceneId, version, actor)` 兼容方法。
  - 新增 `publish(..., expectedSnapshotHash)` 重载。
  - 当 `expectedSnapshotHash` 非空且与当前配置快照不一致时，发布被阻断。
- `ConfigVersionActionRequest`
  - 新增 `expectedSnapshotHash` 字段。
  - 保留旧构造器，兼容既有测试和调用。
- `AdminConfigController.publish(...)`
  - 从请求体读取 `expectedSnapshotHash` 并传入应用层。
- APPROVED 发布路径
  - 继续保留 P2-13/P2-14 的批准快照漂移校验。
  - `expectedSnapshotHash` 是调用方二次确认，不替代批准快照防漂移。

## 验证证据

已新增/扩展测试：

- `ConfigVersionAppServiceTest.blocksPublishWhenExpectedSnapshotHashDoesNotMatchCurrentSnapshot`
  - 错误 `expectedSnapshotHash` 会被 `expected snapshot hash does not match current config snapshot` 阻断。
  - 正确 `currentSnapshotHash` 可发布成功。
- `ConfigVersionAppServiceTest.supportsReviewApprovalAndGitOpsExportBeforePublish`
  - 使用批准后返回的 `currentSnapshotHash` 发布成功，覆盖兼容路径。
- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - Controller 请求体中的 `expectedSnapshotHash` 生效。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 30 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 112 个测试。

## 一致性审查

- 与 P2-14 一致：发布接口复用版本对象上的 `currentSnapshotHash`，不新增另一套 hash 计算口径。
- 与 P2-13 一致：`APPROVED` 发布仍先校验批准快照未漂移，再校验调用方 expected hash。
- 与 P1/P2 兼容：未传 `expectedSnapshotHash` 的旧调用仍可按原规则发布；`REVIEWING` 禁止直接发布、DRAFT 兼容发布均不变。
- 与防腐层边界一致：该校验仅约束配置治理发布，不触碰业务数据和下游动作执行。

## 风险与边界

- 当前 `expectedSnapshotHash` 仍是可选字段，尚未强制 Admin Portal 必传。
- 当前没有 HTTP 错误码细分，异常仍按现有 Controller 异常机制处理。
- 当前未补 OpenAPI/接口文档样例，后续 Admin Portal 对接时需要补请求示例。
- 当前 canonical 逻辑仍为最小稳定字符串化，不是标准 JSON Canonicalization Scheme。

## 后续建议

- 在 Admin Portal 发布按钮前强制携带 `currentSnapshotHash`。
- 为发布接口补 OpenAPI/README 示例，明确错误 hash 的处理方式。
- 补 `approved_by/approved_at` 强字段和结构化审批历史。
- 后续可将 `expectedSnapshotHash` 从可选逐步升级为 APPROVED 发布必填。

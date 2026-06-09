# Intent Hub P2-9 配置发布治理增强审查

## 审查结论

P2-9 最小配置发布治理闭环已完成，通过。

本阶段在既有 Admin 配置版本创建、校验、发布、回滚、导入导出与审计能力之上，补齐配置版本差异对比、发布前 dry-run 报告和 GitOps 文件结构建议。该能力让配置发布从“能发布”进一步变成“发布前可评审、可看差异、可进入 Git 审查流程”。

需要强调：本次没有改变现有 `publish` / `rollback` 语义，也没有新增审批状态流转表。`DRAFT -> REVIEWING -> APPROVED -> PUBLISHED` 仍是后续 Admin Portal / GitOps 治理阶段的预留方向。

## 本次交付物

- `ConfigDiffEntry`
  - 表达单个配置对象的差异项。
  - 字段包含 `objectType`、`objectId`、`changeType`、`before`、`after`。
  - `changeType` 支持 `ADDED`、`MODIFIED`、`REMOVED`。
- `ConfigDiffResult`
  - 表达两个配置版本之间的完整差异。
  - 返回新增、修改、删除计数。
- `ConfigDryRunReport`
  - 表达发布前 dry-run 报告。
  - 包含发布前校验结果、可选 diff 和 GitOps 文件建议。
- `ConfigVersionAppService.diff(...)`
  - 按业务标识对比配置对象，而不是按数组下标对比。
  - 当前对象标识：
    - `INTENT`: `intentCode`
    - `SLOT`: `intentCode.slotCode`
    - `SYNONYM`: `term`
    - `STRATEGY`: `strategyCode`
    - `ROUTE`: `routeStage.routeTarget`
    - `DOWNSTREAM_ACTION`: `actionCode`
  - 对缺少业务标识的对象使用 `__index_N` 兜底，避免 diff 过程异常。
- `ConfigVersionAppService.dryRunPublish(...)`
  - 复用现有发布前校验。
  - 可传入 `baseVersion` 生成差异报告。
  - 返回建议的 GitOps 文件结构：
    - `config/{tenantId}/{sceneId}/{version}/version.json`
    - `intents.json`
    - `slots.json`
    - `synonyms.json`
    - `strategies.json`
    - `routes.json`
    - `downstream-actions.json`
- `AdminConfigController`
  - 新增 `GET /api/v1/admin/config/versions/{version}/diff?tenantId=...&sceneId=...&fromVersion=...`
  - 新增 `POST /api/v1/admin/config/versions/{version}/dry-run?tenantId=...&sceneId=...&baseVersion=...`

## 验证证据

已新增应用层测试：

- `ConfigVersionAppServiceTest.comparesConfigVersionsByObjectIdentity`
  - 验证同一 `intentCode` 的名称变化识别为 `MODIFIED`。
  - 验证新增意图识别为 `ADDED`。
  - 验证删除意图识别为 `REMOVED`。
- `ConfigVersionAppServiceTest.dryRunPublishReturnsValidationDiffAndGitOpsFilePlan`
  - 验证 dry-run 会复用跨对象引用校验。
  - 验证不可发布配置返回 `publishable=false`。
  - 验证报告包含 diff 和 GitOps 文件建议。

已新增接口层测试：

- `AdminConfigControllerTest.exposesDiffAndDryRunContracts`
  - 验证 Admin Controller 暴露 diff 与 dry-run 契约。
  - 验证返回内容包含可读的变更项与 GitOps 路径建议。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-interfaces -am test
```

结果：通过。应用层、基础设施层与接口层相关模块均构建成功，当前相关模块测试总数为 105。

## 一致性审查

- 与 P0 双阶段路由不冲突：本阶段只治理配置版本，不改变识别前置路由或后置动作路由。
- 与 LLM 受控策略不冲突：dry-run 只读取并校验 `llmPolicy` 配置，不触发 LLM 外呼。
- 与防腐层约束不冲突：GitOps 文件建议只面向 Intent Hub 配置包，不包含业务库连接串、SQL 或业务数据。
- 与现有发布回滚兼容：`publish` 和 `rollback` 仍使用既有接口与状态，不强制引入审批状态。
- 与多租户/多场景兼容：diff、dry-run 和 GitOps 路径均显式包含 `tenantId + sceneId + version`。

## 风险与边界

- 当前 diff 是对象级整体对比，不是字段级 JSON Patch；如果后续 Admin Portal 需要高亮字段差异，可在 `ConfigDiffEntry` 上继续扩展 `fieldChanges`。
- 当前 GitOps 是文件结构建议，不会真正写入仓库、创建 PR 或校验 Git 签名。
- 当前没有实现审批状态 `REVIEWING/APPROVED`，因此不能替代正式变更审批流。
- 当前 dry-run 不锁定配置版本，发布前仍可能存在并发编辑风险；后续可通过版本状态、乐观锁或 Git PR 合并锁解决。
- 当前 diff 对缺少业务标识的对象使用下标兜底，适合兼容异常数据，但不应作为长期配置建模方式。后续应继续强化 Admin 写入校验，要求关键对象必须具备稳定业务标识。

## 后续建议

- 在 Admin Portal 增加 diff 可视化页面，按对象类型折叠展示新增、修改、删除。
- 引入审批状态预留字段或状态机：`DRAFT -> REVIEWING -> APPROVED -> PUBLISHED`。
- 将 dry-run 报告作为发布按钮前置条件，并把报告快照写入审计。
- 实现 GitOps 导出命令或接口，将配置 bundle 拆分成建议目录结构，并配合 PR 审查。
- 为发布增加乐观锁或版本状态检查，避免 dry-run 后配置被继续修改。

# Intent Hub P2-10 配置审批与 GitOps 导出审查

## 审查结论

P2-10 配置审批状态机与 GitOps 审查包导出最小闭环已完成，通过。

本阶段承接 P2-9 的 diff 与 dry-run 能力，补齐 `DRAFT -> REVIEWING -> APPROVED -> PUBLISHED` 的最小状态流转，以及面向 GitOps/PR 审查的配置包导出契约。这样配置治理从“发布前可看报告”进一步变成“可提交评审、可批准、可导出审查包”。

## 本次交付物

- `ConfigVersionPort.updateStatus(...)`
  - 把状态更新作为端口能力暴露给应用层。
  - 状态流转规则仍由应用层控制。
- `ConfigVersionAppService.submitReview(...)`
  - 仅允许 `DRAFT` 版本提交评审。
  - 提交前复用发布前校验。
  - 成功后状态变为 `REVIEWING`，审计动作为 `CONFIG_REVIEW_SUBMITTED`。
- `ConfigVersionAppService.approve(...)`
  - 仅允许 `REVIEWING` 版本批准。
  - 批准前复用发布前校验。
  - 成功后状态变为 `APPROVED`，审计动作为 `CONFIG_APPROVED`。
- `ConfigVersionAppService.publish(...)`
  - 保持旧兼容：历史 DRAFT 版本仍可直接发布。
  - 新增门禁：`REVIEWING` 版本不能直接发布，必须先 approve。
- `ConfigGitOpsExport`
  - 表达 GitOps 审查包导出结果。
  - 包含文件路径清单和按文件路径组织的内容。
- `ConfigVersionAppService.exportGitOps(...)`
  - 导出 `version.json`、`intents.json`、`slots.json`、`synonyms.json`、`strategies.json`、`routes.json`、`downstream-actions.json`。
  - 附带 `dry-run.json`，包含校验结果、可发布状态、可选 diff 和 GitOps 文件建议。
  - 记录审计动作 `CONFIG_GITOPS_EXPORTED`。
- Admin API
  - `POST /api/v1/admin/config/versions/{version}/submit-review`
  - `POST /api/v1/admin/config/versions/{version}/approve`
  - `GET /api/v1/admin/config/versions/{version}/gitops`

## 验证证据

已新增应用层测试：

- `ConfigVersionAppServiceTest.supportsReviewApprovalAndGitOpsExportBeforePublish`
  - 验证 `DRAFT -> REVIEWING`。
  - 验证 `REVIEWING` 不能直接发布。
  - 验证 GitOps 导出包含 `version.json` 与 `dry-run.json`。
  - 验证 dry-run 中 diff 可识别修改项。
  - 验证 `REVIEWING -> APPROVED -> PUBLISHED`。
  - 验证审计动作包含提交评审、GitOps 导出、批准和发布。

已新增接口层测试：

- `AdminConfigControllerTest.exposesReviewApprovalAndGitOpsContracts`
  - 验证 Controller 暴露提交评审、GitOps 导出和批准契约。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 27 个测试、基础设施层 59 个测试、接口层 21 个测试，合计 107 个测试。

## 一致性审查

- 与已发布配置读取兼容：运行时仍只读取 `PUBLISHED` 版本，`REVIEWING/APPROVED` 不会进入识别链路。
- 与既有发布兼容：为避免破坏现有 P1/P2 smoke 与脚本，`DRAFT` 仍允许直接发布；新审批链路对 `REVIEWING` 增加必须批准的门禁。
- 与 P2-9 dry-run 兼容：GitOps 导出的 `dry-run.json` 复用同一校验与 diff 语义。
- 与防腐层约束兼容：GitOps 导出只包含 Intent Hub 配置包，不包含业务库连接串、SQL 或业务数据。
- 与审计能力兼容：新增评审、批准和导出动作均写入既有 audit log 端口。

## 风险与边界

- 当前没有新增审批人、审批意见、驳回、撤回、过期、多人审批等完整工作流字段。
- 当前 GitOps 是 API 导出结构，不会真正写入 Git 仓库、创建分支、提交 commit 或发起 PR。
- 当前 `APPROVED` 版本仍可通过既有对象编辑约束避免直接编辑；后续如果要支持撤回或回到草稿，需要补显式状态转移。
- 当前发布前没有乐观锁或配置快照哈希；dry-run/approve 后如果底层数据被异常修改，仍依赖发布前再校验兜底。

## 后续建议

- 增加 `rejectReview` / `cancelReview`，补齐评审闭环。
- 为审批动作增加 comment、reviewer、approvedAt、reviewSnapshotHash。
- 将 GitOps 导出结果写成真实目录或 tar/zip，供 CI 生成 PR。
- 为 publish 增加 `expectedStatus` 或 `expectedHash`，防止 approve 后配置漂移。
- 在 Admin Portal 做 diff + dry-run + approve 三联页面。

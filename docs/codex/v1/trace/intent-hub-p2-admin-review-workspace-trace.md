# Intent Hub P2-11 Admin 配置评审工作台聚合契约审查

## 审查结论

P2-11 Admin 配置评审工作台聚合契约已完成，通过。

当前仓库没有独立前端工程、模板目录或静态 Admin Portal 页面，因此本阶段先完成页面可消费的数据面：一次返回版本状态、发布前校验、dry-run、diff、审计记录、可用动作和阻断原因。后续无论做 Web UI、低代码后台还是外部运营平台，都可以直接消费该聚合接口。

## 本次交付物

- `ConfigReviewWorkspace`
  - 表达配置评审工作台页面需要的一屏数据。
  - 包含 `version`、`validation`、`dryRun`、`audits`、`availableActions`、`blockedReasons`。
- `ConfigReviewWorkspaceAppService`
  - 聚合 `ConfigVersionAppService` 和 `ConfigAuditAppService`。
  - 读取版本详情、校验结果、dry-run 报告和最近审计记录。
  - 根据版本状态和校验结果计算页面可用动作。
- Admin API
  - `GET /api/v1/admin/config/versions/{version}/review-workspace?tenantId=...&sceneId=...&baseVersion=...`

## 可用动作语义

- 所有状态默认提供：
  - `VIEW_DIFF`
  - `DRY_RUN`
  - `EXPORT_GITOPS`
- `DRAFT` 且校验通过：
  - `SUBMIT_REVIEW`
  - `PUBLISH_COMPAT`
- `REVIEWING` 且校验通过：
  - `APPROVE`
- `APPROVED` 且校验通过：
  - `PUBLISH`
- `PUBLISHED` 且校验通过：
  - `ROLLBACK_TARGET`

`blockedReasons` 会暴露当前页面需要提示给运营人员的阻断原因，例如：

- 发布前校验错误。
- `REVIEWING config version must be approved before publish`。
- `ARCHIVED config version is read-only`。

## 验证证据

已新增接口层测试：

- `AdminConfigControllerTest.exposesReviewWorkspaceForAdminPortal`
  - 验证 `DRAFT` 工作台返回 `VIEW_DIFF`、`DRY_RUN`、`EXPORT_GITOPS`、`SUBMIT_REVIEW`、`PUBLISH_COMPAT`。
  - 验证提交评审后工作台返回 `APPROVE`，且不返回 `PUBLISH`。
  - 验证 `REVIEWING` 状态下包含发布阻断原因。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 27 个测试、基础设施层 59 个测试、接口层 22 个测试，合计 108 个测试。

## 一致性审查

- 与 P2-9 一致：工作台复用 dry-run/diff，不重新定义发布前校验语义。
- 与 P2-10 一致：工作台按 `DRAFT/REVIEWING/APPROVED/PUBLISHED` 输出可用动作，不绕过审批状态机。
- 与运行时识别链路隔离：接口只读取配置治理数据，不影响已发布配置读取和识别请求。
- 与防腐层约束兼容：工作台只展示 Intent Hub 配置和审计，不读取业务数据。

## 风险与边界

- 本阶段不是完整前端 UI，没有新增 React/Vue/模板页面。
- 当前可用动作是后端建议，前端仍需按用户权限、菜单权限和真实审批策略二次控制。
- 当前没有返回字段级 diff 摘要；页面如需字段高亮，仍需扩展 P2-9 diff 模型。
- 当前没有分页审计或大配置包裁剪；超大配置包页面性能后续需要单独优化。

## 后续建议

- 建立 Admin Portal 前端工程或静态管理页，消费 `review-workspace` 契约。
- 在工作台页面上串联 diff、dry-run、submit-review、approve、publish。
- 增加权限模型：谁可编辑、谁可提交、谁可批准、谁可发布。
- 为大配置包增加对象类型过滤、diff 分页和字段级变化摘要。

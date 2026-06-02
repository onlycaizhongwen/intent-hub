# P2-2 Bad Case 标注流转与样本导出

## 恢复胶囊

- 任务需求：继续 P2，补齐 bad case 标注、关闭、导出和训练样本格式的最小闭环。
- 关键决策：不做破坏性 DB migration；P2-2 先复用 `bad_case.status` 表达 `OPEN/ANNOTATED/CLOSED/EXPORTED`，标注详情以 API 返回和后续扩展为主。
- 当前阶段：已完成，本地已提交，远端推送待网络恢复。
- 已完成产物：应用端口/服务、memory/JDBC 实现、Admin API、测试、README/status/design/plan/HTML/trace 文档同步。
- 剩余工作：网络恢复后推送并确认远端 main。
- 重要发现：P1 已有 `BadCaseRecord`、`BadCaseQuery` 和 `GET /api/v1/admin/observability/bad-cases`；JDBC 查询当前由 `JdbcRecognitionTraceRepository` 实现，bad case 写入由 `JdbcBadCaseRepository` 实现。

## 步骤列表

- [v] 建立 P2-2 任务记录。
- [v] 实现 bad case 标注/关闭/导出。
  - 当前产物：`BadCaseWorkflowAppService`、`BadCaseWorkflowPort`、memory/JDBC 实现、Admin API。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-application`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补测试。
- [v] 同步 README/status/HTML/design/trace。
- [~] 运行测试并提交。
  - 当前产物：`mvn test` 已通过，共 24 个测试。
  - 下一步：网络恢复后推送并确认远端 main。
  - 涉及文件：全仓变更。

## 研究发现

- bad case 表当前字段：`trace_id`、`request_id`、`tenant_id`、`scene_id`、`intent_code`、`decision`、`confidence`、`reason`、`input_snapshot`、`status`、`created_at`。
- 最小流转可以先使用 `status`，避免在 P2-2 引入迁移复杂度。
- 导出训练样本可用现有字段生成：`traceId`、`tenantId`、`sceneId`、`text`、`intentCode`、`decision`、`confidence`、`reason`。
- P2-2 不做破坏性 migration；JDBC 标注复用 `intent_code` 与 `reason`，后续再补独立标注历史表。
- 新增接口：`POST /api/v1/admin/observability/bad-cases/{traceId}/annotate`、`POST /api/v1/admin/observability/bad-cases/{traceId}/close`、`GET /api/v1/admin/observability/bad-cases/export`。
- `mvn test` 通过，共 24 个测试。
- 本地提交：已完成，以当前 HEAD 为准，提交主题为 `Close the bad case feedback loop`。
- 推送记录：两次执行 `git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 push origin main` 均因 GitHub 443 超时失败；`git ls-remote origin refs/heads/main` 因 `SSL_ERROR_SYSCALL, errno 10054` 失败。

## 错误记录

- PowerShell 下不要把 `intent-hub-*` 当路径 glob 传给 `rg`。

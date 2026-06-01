# 恢复胶囊

- 任务需求：将用户确认的意图中枢技术选型固化进正式规划文档和 HTML 阅读版。
- 关键决策：技术选型从“建议”升级为“已确认基线”；APISIX/SCG 与 FastAPI/Triton 保留条件化二选一。
- 当前阶段：已完成。
- 已完成产物：`docs/codex/v1/trace/intent-hub-tech-selection-confirmation.md`、`docs/codex/v1/designs/intent-hub-design.md`、`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`、`docs/codex/v1/intent-hub-lifecycle.html`、`docs/codex/v1/status.md`。
- 剩余工作：无。
- 重要发现：现有主设计和 HTML 中仍存在“建议选型/正式落地前仍需确认”的旧表述。

## 步骤列表

- [v] 读取现有状态、设计文档、HTML 阅读版。
- [v] 注册技术选型确认任务。
- [v] 固化技术选型确认文档并同步相关正式文档。
  - 当前产物：`.codex/plans/main/tech-selection-confirmation/process.md`
  - 下一步：写入 `docs/codex/v1/trace/intent-hub-tech-selection-confirmation.md` 并更新设计/HTML/status。
  - 涉及文件：`docs/codex/v1/trace/`、`docs/codex/v1/designs/`、`docs/codex/v1/intent-hub-lifecycle.html`、`docs/codex/v1/status.md`
- [v] 验证关键词覆盖和文档路径。

## 研究发现

- 选型基线应保留“团队运维能力决定网关落点”“模型规模决定 FastAPI/Triton 落点”两类执行条件。
- P1 应先以 Spring Boot API、PostgreSQL、Redis、Nacos、轻量规则和模型 stub 建立最小闭环，Kafka、真实模型服务、真实 LLM 可按场景逐步接入。
- 验证已覆盖关键词：Spring Boot 4.x、Nacos 3.x、PostgreSQL 16+、Redis 7.x、Kafka、Spring AI、OpenTelemetry、APISIX、Spring Cloud Gateway、FastAPI、Triton。
- 当前目录不是 git 仓库，`git diff` 无法作为验证证据，已改用 `rg` 关键词和文件存在性验证。

## 错误记录

- 暂无。

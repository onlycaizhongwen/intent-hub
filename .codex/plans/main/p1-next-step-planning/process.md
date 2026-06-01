# P1 下一步执行规划过程

## 恢复胶囊

- 任务需求：用户要求规划下一步。
- 关键决策：下一步不直接进入 P2，而是完成 P1 可验证工程闭环；重点从内存版 stub 推进到可编译、可验收、可回溯、可配置发布的 P1。
- 当前阶段：已完成。
- 已完成产物：任务记录、P1 下一步执行计划、status、HTML、总计划同步。
- 剩余工作：无。
- 重要发现：P1 已有内存版 REST 入口、规则识别、双阶段路由、trace、bad case、幂等和 LLM stub；当前本机 Java 8 且未安装 Maven，真实编译验证需 JDK 17+ 与 Maven。

## 步骤列表

- [v] 建立任务记录。
- [v] 编写并同步 P1 下一步规划。
  - 当前产物：`.codex/plans/main/p1-next-step-planning/process.md`
  - 下一步：已完成。
  - 涉及文件：`docs/codex/v1/plans/`、`docs/codex/v1/status.md`、`docs/codex/v1/intent-hub-lifecycle.html`
- [v] 静态验证文档引用。
- [v] 收口任务记录。

## 研究发现

- 当前 P1 设计已完整覆盖目标，但总计划仍偏阶段级，缺少“下一步怎么干”的逐项任务序列。
- 下一步应优先建立可编译与可验证基础，再推进 PostgreSQL/Flyway、配置治理 API、验收测试和观测。
- 不建议此刻投入真实 BERT/Triton 或大规模 LLM 调用；这些属于 P2/P3 增强。

## 错误记录

- 暂无。

## 验证记录

- `rg -n "intent-hub-p1-next-step-plan|P1 下一步|P1-1|工程可编译|PostgreSQL/Flyway|退出评审|P1 Next Step Planned" docs/codex/v1 .codex/plans/main -S`：确认新计划、status、总计划、HTML 和任务记录均已出现目标引用。
- `Test-Path docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`：返回 `True`。

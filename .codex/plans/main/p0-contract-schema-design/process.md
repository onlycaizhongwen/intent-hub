# v1-p0-contract-schema-design 过程记录

## 恢复胶囊

- 任务需求：开始 P0 契约与 DB Schema 设计。
- 关键决策：P0 只做设计与评审材料，不进入代码实现；必须突出双阶段路由、LLM 受控、防腐层。
- 当前阶段：已完成
- 已完成产物：已新增 P0 设计文档，并回写 status 与 HTML 入口。
- 剩余工作：无。
- 重要发现：现有主线已经确认 DB Schema、decision、幂等、槽位生命周期、同步/异步分离等约束。

## 步骤列表

- [v] 读取现有主线文档。
- [v] 编写 P0 契约与 DB Schema 设计。
- [v] 回写 `docs/codex/v1/status.md`。
- [v] 验证 P0 文档章节和关键字段。

## 验证记录

- 已确认 `docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md` 存在。
- 已确认 P0 文档包含 Envelope、IntentResult、Decision、双阶段路由、LLM 受控、防腐层、DB Schema、API 清单和评审清单。
- 已确认 HTML 已加入 P0 入口和 P0 摘要区块。
- 已确认 `docs/codex/v1/status.md` 已回写 P0 当前阶段与交付物。

## 研究发现

- 三大核心变更点已提升为规划主线：双阶段路由、LLM 受控、防腐层。
- P0 应给出可评审的契约样例和表结构，而不是泛泛描述。

## 错误记录

- 暂无。

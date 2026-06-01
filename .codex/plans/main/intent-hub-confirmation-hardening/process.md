# v1-intent-hub-confirmation-hardening 过程记录

## 恢复胶囊

- 任务需求：把用户确认回执中的风险修正事项固化到 HTML 和正式规划文档。
- 关键决策：所有回执项均按“已确认”处理，包括 DB Schema、双阶段路由、LLM 受控兜底、禁止 v1 直写 DB、租户版本、槽位生命周期、拒识 decision、同步/异步区分、幂等重试、多模态延期。
- 当前阶段：已完成
- 已完成产物：已新增确认清单，更新需求/设计/计划/审查/HTML/status。
- 剩余工作：无。

## 步骤列表

- [v] 读取现有规划文档。
- [v] 固化确认事项到正式文档和 HTML。
- [v] 回写 `docs/codex/v1/status.md`。
- [v] 验证关键章节和文件存在性。

## 验证记录

- 已确认 `docs/codex/v1/trace/intent-hub-confirmed-decisions.md` 存在。
- 已确认 HTML 含“确认回执”区块。
- 已确认 requirements/design/plan/trace/html 均包含 DB Schema、双阶段路由、LLM 受控兜底、禁止通用直写 DB、租户版本、槽位生命周期、幂等重试、多模态延期等关键约束。
- 已确认 HTML 中三张原始图片路径仍为 `designs/...`，相对路径不变。

## 研究发现

- 当前文档已经提出风险，但部分语气仍是建议项，需要改为确认后的实施约束。
- HTML 已包含风险建议区，但缺少确认回执表和 DB Schema 规划。

## 错误记录

- 暂无。

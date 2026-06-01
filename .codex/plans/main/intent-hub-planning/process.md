# v1-intent-hub-planning 过程记录

## 恢复胶囊

- 任务需求：根据 `docs/codex/v1/designs/` 现有资料规划意图中枢系统，输出需求文档、总体技术方案、技术选型、实施计划，并指出不合理点。
- 关键决策：以 `intent-hub` 作为主题；保留原始资料不改动；新增结构化交付文档。
- 当前阶段：已完成
- 已完成产物：已新增 requirements、design、plan、trace 文档，并回写 status。
- 剩余工作：无。
- 重要发现：根目录没有 `designs` 文件夹，资料位于 `docs/codex/v1/designs/`；“数据库流向图”实际是请求/数据流图；原始方案将场景路由前后顺序表达不一致。

## 步骤列表

- [v] 读取项目规则、技能约束与现有状态文件。
- [v] 读取 designs 文字说明和三张设计图。
- [v] 编写需求、技术方案、执行计划、资料审查文档。
- [v] 回写 `docs/codex/v1/status.md`。
- [v] 验证文档文件存在且内容可读。

## 验证记录

- 已检查 `docs/codex/v1/requirements/intent-hub-requirements.md` 存在且包含需求结构。
- 已检查 `docs/codex/v1/designs/intent-hub-design.md` 存在且包含总体架构、技术选型、数据模型、接口、安全、可观测、风险取舍。
- 已检查 `docs/codex/v1/plans/intent-hub-plan.md` 存在且包含阶段计划、验证方式、检查点和回滚策略。
- 已检查 `docs/codex/v1/trace/intent-hub-material-review.md` 存在且列出资料不合理点。
- 已检查 `docs/codex/v1/status.md` 已回写当前主题与交付物路径。

## 研究发现

- 架构资料包含六层：接入与治理层、输入适配层、核心意图漏斗、场景路由层、输出适配层、配置与数据闭环层。
- 目标场景包含多轮对话、单轮对话、第三方 API/Webhook、自定义场景、多模态预留。
- 核心算法路径为规则引擎 -> BERT/DeBERTa/ERNIE 精排 -> LLM 兜底 -> 拒识。

## 错误记录

- 首次检查根目录 `designs` 不存在；已改为使用 `docs/codex/v1/designs/`。

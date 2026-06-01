# 数据流向图 v2 影响评估

## 审查范围

- `docs/codex/v1/designs/数据流向图v1.png`
- `docs/codex/v1/designs/数据流向图v2.png`
- `docs/codex/v1/requirements/intent-hub-requirements.md`
- `docs/codex/v1/designs/intent-hub-design.md`
- `docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md`
- `docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`
- `docs/codex/v1/plans/intent-hub-plan.md`
- `docs/codex/v1/intent-hub-lifecycle.html`

## 结论

`数据流向图v2.png` 可以采纳为当前数据流向基线。该图不推翻已确认的 P0 契约、DB Schema、技术选型和 P1 最小识别闭环，反而把此前规划中的三大关键原则表达得更准确：

- 双阶段路由：前置路由先选识别策略，后置路由再选下游动作。
- LLM 受控：LLM 位于规则与语义模型之后，作为兜底理解能力，不是主力路径。
- 防腐层：输出适配层位于识别之后，只向下游发出动作指令，不直接处理业务数据。

## v1 到 v2 的关键变化

| 变化点 | v1 表达 | v2 表达 | 影响判断 |
| --- | --- | --- | --- |
| 阶段边界 | 以纵向处理流为主 | 明确拆成接入与治理、核心意图漏斗、输出与执行三阶段 | 有利于文档统一，无需改 P0/P1 契约 |
| 路由位置 | 单一“路由决策”容易和场景路由混淆 | 显式拆为前置路由决策与后置路由决策 | 修正旧资料歧义，强化双阶段路由 |
| 配置中心 | 概念化配置支撑 | 明确 Nacos 加载路由规则、模型参数和场景映射 | 与已确认 Nacos 3.x 选型一致 |
| 识别漏斗 | 规则、BERT、LLM、拒识并列 | 保留规则、BERT 精排、LLM 兜底、拒识模块 | 与“LLM 受控兜底”一致 |
| 数据回流 | Bad Case 回流较抽象 | 标出 Bad Case 回流、样本库、训练/优化回流 | 支撑 P1 trace/bad case 与后续训练闭环 |
| 输出执行 | 输出适配到下游 | 后置路由后再进入输出适配与下游系统 | 与防腐层和下游动作模型一致 |

## 对现有规划的影响

### P0 契约与 Schema

无结构性返工。P0 中的 Envelope、IntentResult、`recognition_path`、`scene_routing_rule.route_stage`、`recognition_trace`、`bad_case`、`idempotency_record` 均与 v2 匹配。

建议保持的补充口径：

- `scene_routing_rule` 继续承载 `PRE` 与 `POST` 两类规则。
- `recognition_trace` 继续记录 pre-route、规则/模型/LLM/拒识、post-route 的分层结果。
- `bad_case` 继续作为样本回流入口，训练样本库不进入 P1 主存储实现。

### P1 最小识别闭环

无主线调整。P1 已经按“输入适配 -> 前置路由 -> 识别漏斗 -> 后置路由 -> 输出适配 -> trace/bad case”设计。v2 只要求在文档中明确：

- P1 的 Nacos 配置加载可先由内存配置模拟，后续替换为 Nacos 监听和 Admin Portal 发布。
- P1 的 BERT/模型精排可先用端口和模拟实现，真实 FastAPI/Triton 服务放到后续增强。
- P1 的 LLM 只保留受控兜底端口与 Spring AI Alibaba 适配 stub，不默认进入主识别路径。

### HTML 阅读版

已把“原始资料”中的旧图引用替换为 `designs/数据流向图v2.png`，并把旧的“数据库流向图”描述更新为“数据流向图 v2”。页面当前展示 v2 数据流向基线。

## 已对齐项

- 需求主线：双阶段路由、LLM 受控、防腐层仍成立。
- 总体设计：七个逻辑模块仍成立，Scene Router 可在模块内拆为 PreRoute 与 PostRoute。
- 技术选型：Nacos、Kafka、PostgreSQL、Redis、Spring AI Alibaba、OpenTelemetry 均未受负面影响。
- P1 设计：最小闭环仍以规则识别为主，模型和 LLM 先走端口预留。

## 风险与建议

- v2 图中出现 BERT 精排，不代表 P1 必须上线真实 BERT 服务；P1 仍以规则识别闭环为验收主线，模型精排通过接口预留。
- v2 图中出现 Nacos，不代表所有意图配置都只存 Nacos；配置事实源仍建议放 Admin Portal + PostgreSQL，Nacos 承担运行时配置分发和监听。
- v2 图中 Bad Case 回流指向训练/优化，不代表 P1 必须完成自动训练闭环；P1 只需要完成样本记录与可导出。
- 后置路由之后的输出适配仍必须保持防腐层边界，不能因为“输出与执行”阶段而扩展为直写业务库。

## 后续动作

- 正式文档已采用 `数据流向图v2.png` 作为当前数据流向图。
- 旧的“数据库流向图 vs 设计”确认项已改为“数据流向图 v2 已采纳”。
- HTML 阅读版已替换图片引用并补充 v2 三阶段说明。
- 保留 `数据流向图v1.png` 作为历史材料，不作为当前规划基线。

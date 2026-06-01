# IntentHub P1 退出评审与 P2 准入报告

## 评审结论

结论：有条件通过（Conditionally Approved）。

IntentHub P1 已完成“最小识别闭环”的工程化落地，可以进入 P2 规划与试点扩展。P1 的核心目标已经达成：服务可编译、可启动、可测试；识别链路可返回标准 IntentResult；配置可通过 Admin API 发布和回滚；PostgreSQL/Flyway 持久化可用；trace、bad case、idempotency 可落库和查询；双阶段路由、LLM 受控、防腐层三条主线没有被破坏。

P1 尚有若干增强项未完成，但不阻塞退出 P1：指标采集、bad case 标注流转、动态 scene 路由、配置对象删除/批量导入、更细字段校验、真实模型服务和真实 LLM 流量接入。这些应作为 P2 或 P1.x 增量推进。

## 评审范围

- 需求文档：`docs/codex/v1/requirements/intent-hub-requirements.md`
- 总体设计：`docs/codex/v1/designs/intent-hub-design.md`
- P0 契约与 Schema：`docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md`
- P1 最小闭环设计：`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`
- P1 执行计划：`docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`
- 关键评审与确认：`docs/codex/v1/trace/intent-hub-confirmed-decisions.md`、`docs/codex/v1/trace/intent-hub-p0-review-report.md`、`docs/codex/v1/trace/intent-hub-data-flow-v2-review.md`
- 当前实现：Maven 多模块工程、REST API、Admin API、Flyway migration、内存/JDBC 适配器和自动化测试。

## P1 完成标准对照

| P1 完成标准 | 当前结论 | 证据 |
| --- | --- | --- |
| 工程可编译、可启动 | 通过 | `mvn test` 通过；jar 已在 memory 和 `local-jdbc` 模式完成 HTTP 冒烟。 |
| 8 个 P1 验收用例通过 | 基本通过 | 已覆盖 `SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED`、幂等、配置治理、已发布配置读取、trace 查询和 bad case 查询；当前自动化测试共 17 个。 |
| 数据库 migration 可从空库初始化 | 通过 | Flyway `V1__p1_minimal_persistence.sql` 已在 Docker PostgreSQL 16 空库联调通过。 |
| 配置发布、回滚、审计可用 | 通过 | Admin Config API 已支持草稿、查询、校验、发布、回滚、导入导出和审计；`audit_log_count=6` 已验证。 |
| trace、bad case、idempotency 已持久化 | 通过 | `recognition_trace`、`bad_case`、`idempotency_record` 已落表；P1-5 已支持 trace/bad case 查询 API。 |
| Output Adapter 没有直写业务库能力 | 通过 | `DownstreamAction` 仅表达动作类型、目标、超时和幂等要求；未提供 SQL 或业务库连接能力。 |
| LLM 仍是受控兜底 | 通过 | `TongyiLlmAdapter` 为基础设施 stub；P1 默认规则识别，LLM 不作为主路径。 |
| 模块依赖方向符合 DDD 骨架 | 通过 | `domain` 不依赖 Web/JDBC/LLM SDK；`application` 只定义用例和端口；`infrastructure` 实现端口；`interfaces` 负责 HTTP。 |

## 已对齐的核心需求

- 双阶段路由：当前识别路径包含 `PRE_ROUTE` 与 `POST_ROUTE`，已体现“先选怎么认，再选谁来干”。
- LLM 受控：LLM 被放在策略端口和基础设施适配层，默认不参与主识别链路。
- 防腐层：输出动作只表达指令，不触碰业务数据和业务库。
- 配置版本化：配置按 `tenant_id + scene_id + version` 管理，支持 `DRAFT`、`PUBLISHED`、`ARCHIVED`。
- 可回溯：可通过 `trace_id` 查询识别路径、决策、槽位和下游动作。
- 数据回流：拒识和低置信样本进入 bad case，并可通过 Admin API 查询。
- 幂等：异步动作生成幂等键，重复请求不重复生成下游动作记录。

## 非阻塞遗留项

| 遗留项 | 风险 | 建议归属 |
| --- | --- | --- |
| Prometheus/OpenTelemetry 指标尚未接入 | 缺少持续运行指标和 P95 观测 | P2 入口前或 P2-1 |
| bad case 标注、关闭、导出和训练回流状态流转未实现 | 样本可查询但还不能形成完整运营闭环 | P2-1 |
| 前置路由动态 scene 选择未完成 | 当前 `order-scene` 是最小试点固定值，多场景扩展受限 | P2-1 |
| 配置对象删除、批量导入、更细字段校验未完成 | 管理后台 API 够用但运营效率和质量闸门不足 | P1.x 或 P2-1 |
| 真实 FastAPI/Triton 模型服务未接入 | 当前模型链路仍是端口预留 | P2 模型试点 |
| 真实 Spring AI Alibaba 调用未放量 | LLM 策略边界已留好，但未验证真实成本、延迟和降级 | P2 LLM 兜底试点 |
| 压测数据不足 | 还不能判断 1000 意图规模下的性能瓶颈 | P2 准入后补压测 |

## P2 准入建议

建议 P2 不直接扩大到完整平台，而是按“试点场景 + 可观测运营 + 模型增强”推进：

1. P2-1：动态 scene 路由与多租户/多场景配置读取。
2. P2-2：bad case 标注流转、导出和训练样本格式。
3. P2-3：Prometheus/OpenTelemetry 指标、Grafana 看板和基础告警。
4. P2-4：接入一个真实模型服务（FastAPI 优先，Triton 后置）。
5. P2-5：小流量启用 Spring AI Alibaba 兜底，验证超时、预算、熔断和 fallback。
6. P2-6：压测与容量评估，验证规则命中、拒识率、P95 延迟和数据库查询成本。

## 不建议立刻做

- 不建议直接做完整 Admin 前端 UI，先把配置 API、校验和审计补扎实。
- 不建议直接引入 Drools，当前轻量规则足够支撑 P1/P2 试点。
- 不建议让 LLM 成为默认主识别路径。
- 不建议让输出适配层直写业务库或接收 SQL。
- 不建议在没有 bad case 标注闭环前启动自动训练。

## 最终判断

P1 可以退出，进入 P2 规划与试点扩展。

退出条件的判断口径是：P1 已证明 IntentHub 的核心架构成立，并具备可运行、可配置、可回溯、可持久化的最小闭环；剩余项属于运营成熟度、观测深度、模型能力和多场景规模化问题，不再阻塞 P1 结束。

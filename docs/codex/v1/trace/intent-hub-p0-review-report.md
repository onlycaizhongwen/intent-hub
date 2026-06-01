# IntentHub P0 契约与 Schema 评审报告

## 评审结论

通过（Approved）。

P0 设计方案可以作为进入 P1 开发的 Specification。方案已经解决前期讨论的主要架构隐患，包括双阶段路由、LLM 受控、防腐层、DB Schema 多租户版本化、全链路追踪和幂等重试。

## 总体评价

P0 设计逻辑严密，已具备直接指导 P1 最小识别闭环开发的条件。未发现阻塞性逻辑漏洞。

## 分项评审

### 1. Envelope 输入契约

结论：通过。

优点：

- `tenant_id`、`source`、`channel` 能支撑前置路由。
- `request_id` 与 `trace_id` 分离合理。
- `metadata.user_tags` 支持后续精细化路由。

微调：

- `attachments` 只允许 URL 或 Object Key，不允许 Base64 原文，避免网关和识别链路承压。

### 2. IntentResult 输出契约

结论：通过。

优点：

- `decision` 覆盖缺槽、异步接收、安全阻断等业务异常流。
- `recognition_path` 有助于调试识别链路。
- `downstream_action` 分离了识别与执行。

微调：

- `confidence` 明确取值范围为 `[0.0, 1.0]`，并补充默认行为区间。

### 3. DB Schema

结论：通过。

优点：

- `tenant_id + scene_id + version` 支撑多租户隔离和配置回滚。
- `recognition_trace.latency_breakdown` 支撑性能优化。
- `idempotency_record` 可防止下游重复执行。

微调：

- `recognition_trace.input_snapshot` 和 `bad_case.input_snapshot` 写入前必须进行敏感信息脱敏。

### 4. 双阶段路由

结论：通过。

优点：

- 前置路由选策略，后置路由选动作，边界清晰。
- `scene_routing_rule.route_stage` 可以用一张表管理两套逻辑。

微调：

- 增加 `match_condition` 示例，特别是 slot 条件，例如 `slots.amount > 1000` 走高级审批。

### 5. LLM 受控策略

结论：通过。

优点：

- LLM 被限定为兜底角色。
- `trigger_conditions` 和 `fallback_decision` 支持优雅降级。
- `prompt_guard_enabled` 体现 Prompt Injection 防护。

微调：

- `llm_policy` 增加 `max_retries` 字段，限制 LLM 自身重试次数。

### 6. 防腐层动作模型

结论：通过。

优点：

- 明确禁止 DB 连接串和 SQL。
- `downstream_action.action_type + target` 可以覆盖 API、MQ、Webhook、MQTT。

微调：

- `downstream_action` 增加 `timeout_ms` 字段，控制第三方 API 或外部系统响应不可控的问题。

## 高风险点

### 配置管理一致性

风险：

P0 设计包含大量配置表，例如 `intent_definition`、`slot_definition`、`synonym_mapping`、`nlu_strategy`、`scene_routing_rule`。如果开发、测试、生产环境依赖手工改库同步配置，将导致不可审计、不可回滚、环境不一致和线上问题难以追踪。

约束：

P1 阶段必须配套至少一种配置治理方式：

- Admin Portal：通过管理后台维护、发布、回滚配置。
- GitOps：配置文件版本化，经过评审后发布到环境。

不允许把手工改数据库作为正式配置发布方式。

## P1 准入条件

- P0 微调项已回写到设计文档。
- 配置一致性方案在 P1 计划中作为高优先级任务。
- P1 开发不得绕过配置版本、发布审计和回滚机制。


# 意图中枢 P0 契约与 DB Schema 设计

## 目标

P0 阶段目标是把开发前最容易反复的边界固定下来：输入契约、输出契约、核心枚举、双阶段路由、LLM 受控策略、防腐层动作模型、DB Schema 和评审清单。

P0 不进入业务代码实现。只有本文评审通过后，才进入 P1 最小识别闭环开发。

## 设计主线

### 双阶段路由

先选怎么认，再选谁来干。

- 前置路由：识别前根据租户、来源、渠道、用户标签、输入类型选择识别策略。
- 后置路由：识别后根据意图、槽位、置信度、场景选择下游动作。

### LLM 受控

LLM 是最后一道防线，不是主力。

- 默认不进入主识别路径。
- 只在规则和模型低置信度、长尾表达、复杂指代等情况下触发。
- 必须具备开关、超时、预算、熔断、降级、Prompt 注入防护和审计。

### 防腐层

输出适配层只发指令，不碰业务数据。

- 允许 API、MQ、Webhook、MQTT。
- 禁止通用直写业务数据库。
- 下游动作必须有幂等键和动作契约。

## 关键枚举

### InputType

| 值 | 含义 |
| --- | --- |
| `TEXT` | 文本输入 |
| `EVENT` | 系统事件或按钮事件 |
| `WEBHOOK` | 第三方 Webhook |
| `VOICE_TEXT` | ASR 后文本 |
| `MULTIMODAL_REF` | 多模态引用预留，不做解析 |

### Decision

| 值 | 含义 | 是否触发下游 |
| --- | --- | --- |
| `SUCCESS` | 识别成功，可同步返回结果 | 可选 |
| `CLARIFY` | 槽位缺失或歧义，需要追问 | 否 |
| `REJECTED` | 未知意图或低置信度拒识 | 否 |
| `HANDOFF` | 转人工或转专门系统 | 可选 |
| `BLOCKED` | 安全策略阻断 | 否 |
| `ASYNC_ACCEPTED` | 已接收并进入异步执行 | 是 |

### RouteStage

| 值 | 含义 |
| --- | --- |
| `PRE` | 前置路由，选择识别策略 |
| `POST` | 后置路由，选择业务动作 |

### SlotState

| 值 | 含义 |
| --- | --- |
| `EMPTY` | 未填 |
| `EXTRACTED` | 已抽取但未确认 |
| `PENDING_CONFIRM` | 待用户确认 |
| `CONFIRMED` | 已确认 |
| `EXPIRED` | 已过期 |

### DownstreamActionType

| 值 | 含义 |
| --- | --- |
| `API` | 调用业务 API |
| `MQ` | 投递消息 |
| `WEBHOOK` | 回调 Webhook |
| `MQTT` | 下发 MQTT 指令 |

### IdempotencyStatus

| 值 | 含义 |
| --- | --- |
| `PENDING` | 待执行 |
| `SUCCESS` | 执行成功 |
| `FAILED` | 执行失败 |
| `RETRYING` | 重试中 |
| `EXPIRED` | 幂等记录过期 |

## 输入契约：Envelope

### 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `request_id` | string | 是 | 调用方请求 ID，调用方不传时由网关生成 |
| `trace_id` | string | 是 | 全链路追踪 ID |
| `tenant_id` | string | 是 | 租户 ID |
| `source` | string | 是 | 来源系统，例如 `chat_web`、`ivr`、`payment_webhook` |
| `channel` | string | 是 | 渠道，例如 `WEB`、`APP`、`IVR`、`API` |
| `session_id` | string | 否 | 多轮会话 ID |
| `user_id` | string | 否 | 用户 ID 或外部主体 ID |
| `input_type` | enum | 是 | 见 `InputType` |
| `text` | string | 否 | 文本内容 |
| `event` | object | 否 | 事件输入 |
| `attachments` | array | 否 | 多模态预留引用，只允许 URL 或 Object Key，不允许 Base64 原文 |
| `metadata` | object | 否 | 扩展元数据 |
| `timestamp` | string | 是 | ISO-8601 时间 |

### 示例

```json
{
  "request_id": "req_20260601_0001",
  "trace_id": "tr_8f4b1a",
  "tenant_id": "tenant_demo",
  "source": "chat_web",
  "channel": "WEB",
  "session_id": "sess_10001",
  "user_id": "user_7788",
  "input_type": "TEXT",
  "text": "帮我查一下上周的订单",
  "event": null,
  "attachments": [],
  "metadata": {
    "user_tags": ["vip"],
    "locale": "zh-CN"
  },
  "timestamp": "2026-06-01T11:30:00+08:00"
}
```

## 输出契约：IntentResult

### 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `request_id` | string | 是 | 请求 ID |
| `trace_id` | string | 是 | 追踪 ID |
| `tenant_id` | string | 是 | 租户 ID |
| `scene_id` | string | 是 | 场景 ID |
| `intent_code` | string | 否 | 意图编码 |
| `intent_name` | string | 否 | 意图名称 |
| `slots` | object | 是 | 槽位结果 |
| `confidence` | number | 是 | 最终置信度 |
| `recognition_path` | array | 是 | 命中的识别链路 |
| `decision` | enum | 是 | 见 `Decision` |
| `idempotency_key` | string | 否 | 触发下游动作时必填 |
| `downstream_action` | object | 否 | 防腐层动作描述 |
| `debug` | object | 否 | 调试信息，生产可按权限脱敏 |
| `errors` | array | 是 | 错误或拒识原因 |

`confidence` 取值范围为 `[0.0, 1.0]`。默认行为建议：

| 区间 | 默认行为 |
| --- | --- |
| `[0.85, 1.0]` | 高置信度，可进入 `SUCCESS` 或后置路由 |
| `[0.60, 0.85)` | 中置信度，可进入模型复核、LLM 兜底或 `CLARIFY` |
| `[0.0, 0.60)` | 低置信度，默认 `REJECTED` 或 `HANDOFF` |

### 示例：同步识别成功

```json
{
  "request_id": "req_20260601_0001",
  "trace_id": "tr_8f4b1a",
  "tenant_id": "tenant_demo",
  "scene_id": "ecommerce_order",
  "intent_code": "ORDER_QUERY",
  "intent_name": "订单查询",
  "slots": {
    "time_range": {
      "value": "last_week",
      "state": "CONFIRMED"
    }
  },
  "confidence": 0.93,
  "recognition_path": ["PRE_ROUTE", "RULE", "POST_ROUTE"],
  "decision": "SUCCESS",
  "idempotency_key": null,
  "downstream_action": null,
  "debug": {
    "config_version": "v1.0.0",
    "strategy_id": "strategy_order_basic",
    "model_version": null
  },
  "errors": []
}
```

### 示例：异步执行已接收

```json
{
  "request_id": "req_20260601_0002",
  "trace_id": "tr_b91f44",
  "tenant_id": "tenant_demo",
  "scene_id": "ecommerce_order",
  "intent_code": "ORDER_CANCEL",
  "intent_name": "取消订单",
  "slots": {
    "order_id": {
      "value": "O20260601001",
      "state": "CONFIRMED"
    }
  },
  "confidence": 0.91,
  "recognition_path": ["PRE_ROUTE", "MODEL", "POST_ROUTE"],
  "decision": "ASYNC_ACCEPTED",
  "idempotency_key": "idem_tenant_demo_ORDER_CANCEL_9b80",
  "downstream_action": {
    "type": "MQ",
    "target": "order.command.cancel",
    "payload_schema": "order_cancel_v1"
  },
  "debug": {
    "config_version": "v1.0.0",
    "strategy_id": "strategy_order_basic",
    "model_version": "order-cls-202606"
  },
  "errors": []
}
```

## 双阶段路由模型

### 前置路由 PreRoute

用途：选择怎么识别。

匹配条件：

- `tenant_id`
- `source`
- `channel`
- `input_type`
- `user_tags`
- `scene_hint`

输出：

- `scene_id`
- `strategy_id`
- `config_version`
- `rule_set_id`
- `model_version`
- `confidence_threshold`
- `llm_policy_id`

### 后置路由 PostRoute

用途：选择谁来执行。

匹配条件：

- `tenant_id`
- `scene_id`
- `intent_code`
- `slot_conditions`
- `confidence`
- `decision`

输出：

- `downstream_action_id`
- `action_type`
- `target`
- `idempotency_required`
- `async_required`
- `retry_policy_id`

### match_condition 示例

前置路由示例：

```json
{
  "tenant_id": "tenant_demo",
  "source": "chat_web",
  "channel": "WEB",
  "input_type": "TEXT",
  "user_tags": {
    "contains": ["vip"]
  }
}
```

后置路由示例：

```json
{
  "intent_code": "TRANSFER_MONEY",
  "confidence": {
    "gte": 0.9
  },
  "slots": {
    "amount": {
      "gt": 1000
    }
  }
}
```

该示例表示：当转账金额大于 1000 且置信度不低于 0.9 时，后置路由可以选择高级审批或高风险动作通道。

## LLM 受控策略模型

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `llm_policy_id` | string | 策略 ID |
| `tenant_id` | string | 租户 ID |
| `scene_id` | string | 场景 ID |
| `enabled` | boolean | 是否启用 |
| `trigger_conditions` | json | 低置信度、长尾、指代消解等触发条件 |
| `provider` | string | LLM 供应方 |
| `model_name` | string | 模型名称 |
| `timeout_ms` | int | 超时时间 |
| `max_tokens` | int | token 上限 |
| `max_retries` | int | LLM 自身最大重试次数，防止重试放大或死循环 |
| `daily_budget` | decimal | 日预算 |
| `circuit_breaker` | json | 熔断规则 |
| `fallback_decision` | enum | 降级后的 decision |
| `prompt_guard_enabled` | boolean | Prompt 注入防护 |
| `audit_enabled` | boolean | 审计开关 |

## 防腐层动作模型

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `downstream_action_id` | string | 动作 ID |
| `tenant_id` | string | 租户 ID |
| `scene_id` | string | 场景 ID |
| `intent_code` | string | 意图编码 |
| `action_type` | enum | API/MQ/WEBHOOK/MQTT |
| `target` | string | API 地址、Topic、Webhook URL 或 MQTT topic |
| `payload_schema` | string | 出参 schema 版本 |
| `idempotency_required` | boolean | 是否强制幂等 |
| `async_required` | boolean | 是否异步执行 |
| `timeout_ms` | int | 下游调用超时时间 |
| `retry_policy_id` | string | 重试策略 |
| `enabled` | boolean | 是否启用 |

禁止字段：

- 业务库连接串
- 业务表名
- 原始 SQL
- 通用数据库写入模板

## DB Schema 初版

### 配置域

#### tenant

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码，唯一 |
| `tenant_name` | varchar(128) | 租户名称 |
| `status` | varchar(32) | 状态 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

索引：

- `uk_tenant_id(tenant_id)`

#### scene

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `scene_name` | varchar(128) | 场景名称 |
| `status` | varchar(32) | 状态 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

索引：

- `uk_scene(tenant_id, scene_id)`

#### config_version

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `status` | varchar(32) | DRAFT/PUBLISHED/ROLLED_BACK |
| `published_by` | varchar(64) | 发布人 |
| `published_at` | timestamp | 发布时间 |
| `rollback_from` | varchar(64) | 回滚来源版本 |
| `change_summary` | text | 变更说明 |

索引：

- `uk_config_version(tenant_id, scene_id, version)`
- `idx_config_status(tenant_id, scene_id, status)`

#### intent_definition

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `intent_code` | varchar(128) | 意图编码 |
| `intent_name` | varchar(128) | 意图名称 |
| `description` | text | 描述 |
| `examples` | jsonb | 样例 |
| `required_slots` | jsonb | 必填槽位 |
| `optional_slots` | jsonb | 可选槽位 |
| `status` | varchar(32) | 状态 |

索引：

- `uk_intent(tenant_id, scene_id, version, intent_code)`

#### slot_definition

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `slot_code` | varchar(128) | 槽位编码 |
| `slot_name` | varchar(128) | 槽位名称 |
| `data_type` | varchar(32) | 数据类型 |
| `extractor` | varchar(128) | 抽取器 |
| `normalizer` | varchar(128) | 归一化器 |
| `validation_rule` | jsonb | 校验规则 |
| `required` | boolean | 是否必填 |

索引：

- `uk_slot(tenant_id, scene_id, version, slot_code)`

#### synonym_mapping

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `source_text` | varchar(256) | 原词 |
| `normalized_text` | varchar(256) | 归一化结果 |
| `scope` | varchar(64) | 作用范围 |

索引：

- `idx_synonym_lookup(tenant_id, scene_id, version, source_text)`

#### nlu_strategy

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `strategy_id` | varchar(128) | 策略 ID |
| `rule_set_id` | varchar(128) | 规则集 ID |
| `model_version` | varchar(128) | 模型版本 |
| `confidence_threshold` | decimal(5,4) | 置信度阈值 |
| `llm_policy_id` | varchar(128) | LLM 策略 ID |
| `enabled` | boolean | 是否启用 |

索引：

- `uk_strategy(tenant_id, scene_id, version, strategy_id)`

#### scene_routing_rule

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `route_stage` | varchar(16) | PRE/POST |
| `priority` | int | 优先级 |
| `match_condition` | jsonb | 匹配条件 |
| `strategy_id` | varchar(128) | 前置路由输出 |
| `downstream_action_id` | varchar(128) | 后置路由输出 |
| `enabled` | boolean | 是否启用 |

索引：

- `idx_route_match(tenant_id, scene_id, version, route_stage, priority)`

#### llm_policy

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `llm_policy_id` | varchar(128) | LLM 策略 ID |
| `enabled` | boolean | 是否启用 |
| `trigger_conditions` | jsonb | 触发条件 |
| `provider` | varchar(64) | 供应方 |
| `model_name` | varchar(128) | 模型名 |
| `timeout_ms` | int | 超时 |
| `max_tokens` | int | token 上限 |
| `max_retries` | int | 最大重试次数 |
| `daily_budget` | decimal(12,2) | 日预算 |
| `circuit_breaker` | jsonb | 熔断规则 |
| `fallback_decision` | varchar(32) | 降级 decision |
| `prompt_guard_enabled` | boolean | 注入防护 |
| `audit_enabled` | boolean | 审计 |

索引：

- `uk_llm_policy(tenant_id, scene_id, version, llm_policy_id)`

#### downstream_action

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `version` | varchar(64) | 配置版本 |
| `downstream_action_id` | varchar(128) | 动作 ID |
| `intent_code` | varchar(128) | 意图编码 |
| `action_type` | varchar(32) | API/MQ/WEBHOOK/MQTT |
| `target` | varchar(512) | 目标地址或 topic |
| `payload_schema` | varchar(128) | payload schema |
| `idempotency_required` | boolean | 是否幂等 |
| `async_required` | boolean | 是否异步 |
| `timeout_ms` | int | 下游调用超时 |
| `retry_policy_id` | varchar(128) | 重试策略 |
| `enabled` | boolean | 是否启用 |

索引：

- `uk_downstream_action(tenant_id, scene_id, version, downstream_action_id)`

### 运行域

#### conversation_session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `session_id` | varchar(128) | 会话 ID |
| `user_id` | varchar(128) | 用户 ID |
| `context_snapshot` | jsonb | 上下文快照 |
| `expires_at` | timestamp | 过期时间 |
| `updated_at` | timestamp | 更新时间 |

索引：

- `uk_session(tenant_id, session_id)`

#### slot_state

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `session_id` | varchar(128) | 会话 ID |
| `slot_code` | varchar(128) | 槽位编码 |
| `raw_value` | text | 原始值 |
| `normalized_value` | text | 归一化值 |
| `state` | varchar(32) | 槽位状态 |
| `source_turn_id` | varchar(128) | 来源轮次 |
| `expires_at` | timestamp | 过期时间 |
| `updated_at` | timestamp | 更新时间 |

索引：

- `uk_slot_state(tenant_id, scene_id, session_id, slot_code)`

#### recognition_trace

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `trace_id` | varchar(128) | 追踪 ID，唯一 |
| `request_id` | varchar(128) | 请求 ID |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `input_snapshot` | jsonb | 输入快照，写入前必须脱敏 |
| `pre_route_result` | jsonb | 前置路由结果 |
| `preprocess_result` | jsonb | 预处理结果 |
| `rule_candidates` | jsonb | 规则候选 |
| `model_candidates` | jsonb | 模型候选 |
| `llm_result` | jsonb | LLM 结果 |
| `post_route_result` | jsonb | 后置路由结果 |
| `decision` | varchar(32) | 最终 decision |
| `selected_intent` | varchar(128) | 最终意图 |
| `config_version` | varchar(64) | 配置版本 |
| `model_version` | varchar(128) | 模型版本 |
| `latency_breakdown` | jsonb | 分层耗时 |
| `created_at` | timestamp | 创建时间 |

索引：

- `uk_trace_id(trace_id)`
- `idx_trace_query(tenant_id, scene_id, created_at)`

#### bad_case

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `trace_id` | varchar(128) | 追踪 ID |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `input_snapshot` | jsonb | 输入快照，写入前必须脱敏 |
| `expected_intent` | varchar(128) | 期望意图 |
| `actual_intent` | varchar(128) | 实际意图 |
| `error_type` | varchar(64) | 错误类型 |
| `review_status` | varchar(32) | 标注状态 |
| `annotator` | varchar(64) | 标注人 |
| `created_at` | timestamp | 创建时间 |

索引：

- `idx_bad_case_queue(tenant_id, scene_id, review_status, created_at)`

#### idempotency_record

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `idempotency_key` | varchar(256) | 幂等键，唯一 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `request_hash` | varchar(128) | 请求摘要 |
| `downstream_action_id` | varchar(128) | 下游动作 |
| `status` | varchar(32) | 状态 |
| `retry_count` | int | 重试次数 |
| `last_error` | text | 最近错误 |
| `expires_at` | timestamp | 过期时间 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

索引：

- `uk_idempotency_key(idempotency_key)`
- `idx_idempotency_status(tenant_id, scene_id, status, expires_at)`

#### audit_log

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `tenant_id` | varchar(64) | 租户编码 |
| `scene_id` | varchar(64) | 场景编码 |
| `operator` | varchar(64) | 操作人 |
| `operation` | varchar(64) | 操作类型 |
| `object_type` | varchar(64) | 对象类型 |
| `object_id` | varchar(128) | 对象 ID |
| `before_snapshot` | jsonb | 变更前 |
| `after_snapshot` | jsonb | 变更后 |
| `created_at` | timestamp | 创建时间 |

索引：

- `idx_audit_query(tenant_id, scene_id, object_type, created_at)`

## P0 API 清单

| 接口 | 方法 | 用途 |
| --- | --- | --- |
| `/api/v1/intent/recognize` | POST | 同步意图识别 |
| `/api/v1/events/webhook/{source}` | POST | 第三方事件接入 |
| `/api/v1/admin/config/versions` | POST | 创建配置版本 |
| `/api/v1/admin/config/versions/{version}/publish` | POST | 发布配置版本 |
| `/api/v1/admin/config/versions/{version}/rollback` | POST | 回滚配置版本 |
| `/api/v1/admin/intents` | POST/PUT | 管理意图 |
| `/api/v1/admin/slots` | POST/PUT | 管理槽位 |
| `/api/v1/admin/routes` | POST/PUT | 管理前置/后置路由 |
| `/api/v1/admin/strategies` | POST/PUT | 管理 NLU 策略 |
| `/api/v1/admin/downstream-actions` | POST/PUT | 管理防腐层动作 |

## P0 评审清单

### 契约

- Envelope 必填字段是否足够支撑多源输入。
- IntentResult 是否能表达成功、澄清、拒识、转人工、阻断、异步接收。
- `decision` 枚举是否覆盖业务真实异常流。
- `attachments` 是否满足多模态延期但兼容预留。

### 双阶段路由

- 前置路由是否能根据租户、来源、渠道选择识别策略。
- 后置路由是否能根据意图、槽位、置信度选择下游动作。
- 两类路由是否都支持优先级、版本、灰度和禁用。

### LLM 受控

- 是否默认不走 LLM。
- 是否明确触发条件。
- 是否具备超时、预算、熔断、降级、注入防护和审计。
- LLM 失败时是否有明确 `fallback_decision`。

### 防腐层

- downstream_action 是否只包含 API/MQ/Webhook/MQTT。
- 是否没有业务库连接串、业务表名、SQL 模板。
- 是否强制幂等和重试策略。
- 是否明确同步识别和异步执行边界。

### DB Schema

- 配置域是否全部带 `tenant_id + scene_id + version`。
- 运行域是否支持 trace 回溯、bad case、幂等记录和审计。
- 关键唯一索引是否能防止配置冲突和重复执行。
- JSONB 字段是否只承载扩展信息，不替代核心查询字段。

### 配置一致性

- 是否禁止手工改数据库作为正式配置发布方式。
- 是否明确 P1 配置治理方案：Admin Portal 或 GitOps。
- 是否所有配置变更都可审计、可回滚、可同步到目标环境。

## P0 完成标准

- 本文评审通过。
- Envelope 和 IntentResult 字段冻结到 P1。
- DB Schema 初版可转为迁移脚本。
- 双阶段路由、LLM 受控、防腐层动作模型无阻塞争议。
- P1 可以基于本文启动服务骨架和最小识别闭环开发。
- P1 必须配套配置治理方案，不能依赖手工改库同步配置。

# Intent Hub P2-5 LLM 受控兜底审查

## 审查结论

通过。

P2-5 已完成受控 LLM 兜底的最小闭环：LLM 仍位于 Rule 和 Model 之后，只作为最后一道防线；默认关闭且预算为 0，不会在未配置时触发外部调用。开启时同时受全局治理配置与 scene 级 `llm_policy` 约束，失败后按 `fallbackDecision` 失败关闭。

## 范围

本次审查覆盖：

- 领域层 `LlmPolicy` 与 `LlmRecognizePolicy` 门禁。
- `LlmClientPort` 调用契约。
- 基础设施 `TongyiLlmAdapter` 与治理配置。
- JDBC 已发布 `nlu_strategy.llm_policy` 读取。
- 自动化测试。
- README、status、HTML 和任务记录同步。

## 实现结果

新增基础设施能力：

- `LlmGovernanceProperties`
- `LlmRecognitionRequest`
- `LlmRecognitionResponse`
- `TongyiLlmAdapter` HTTP 小流量兜底 adapter

配置：

```yaml
intent-hub:
  llm:
    enabled: false
    base-url: ""
    timeout-ms: 3000
    max-retries: 0
    daily-budget: 0.0
    min-confidence: 0.70
```

scene 级策略读取：

- `JdbcSceneConfigRepository` 从已发布 `nlu_strategy.llm_policy` 读取：
  - `enabled`
  - `provider`
  - `model`
  - `timeoutMs` / `timeout_ms`
  - `maxRetries` / `max_retries`
  - `dailyBudget` / `daily_budget`
  - `fallbackDecision` / `fallback_decision`

触发门禁：

```text
global enabled + baseUrl + global dailyBudget
+ scene llmPolicy.enabled
+ scene dailyBudget > 0
+ scene timeoutMs > 0
```

只有同时满足上述条件才尝试 LLM 调用。

## 架构一致性

### 双阶段路由

LLM 仍属于“怎么认”的识别策略，不参与后置动作选择。识别结果仍交给后置路由选择 `DownstreamAction`。

### LLM 受控

LLM 不是主力路径。规则和模型命中时不会进入 LLM；未命中且策略允许时才触发。治理关闭、预算为 0、超时为 0 或策略关闭都会阻断 LLM。

### 防腐层

LLM adapter 只返回识别候选 `intentCode/confidence/slots/explanation`，不返回 SQL、不返回业务执行动作，不接触业务数据源。

### DDD 分层

领域层只依赖 `LlmClientPort` 和 `LlmPolicy`，不依赖 Spring AI、DashScope、RestClient 或供应商 SDK。HTTP 适配位于 infrastructure。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 36 个。
- 失败 0，错误 0，跳过 0。

新增覆盖点：

- `RecognizeAppServiceTest` 覆盖 LLM provider 异常时失败关闭并记录 `LLM_FALLBACK:REJECTED`。
- `RecognizeAppServiceTest` 覆盖 scene 策略预算为 0 时不进入 LLM。
- `TongyiLlmAdapterTest` 覆盖治理关闭、策略预算为 0、成功返回候选和有限重试后失败。
- `JdbcSceneConfigRepositoryTest` 覆盖从已发布 `nlu_strategy.llm_policy` 读取 LLM 策略。

## 当前限制

- 当前 `TongyiLlmAdapter` 是 HTTP 契约 adapter，尚未升级为真实 Spring AI Alibaba `ChatClient` 调用。
- 尚未做 DashScope 沙箱密钥冒烟。
- `timeoutMs` 已进入策略和治理配置，但底层 RestClient request timeout 还未绑定到专用 `ClientHttpRequestFactory`。
- 当前预算只做触发门禁，还没有按日消耗计数和配额扣减。

## 后续建议

- 将 `TongyiLlmAdapter` 从 HTTP mock 契约升级为 Spring AI Alibaba `ChatClient`，保持 `LlmClientPort` 不变。
- 接入真实 DashScope 沙箱密钥，做小流量冒烟并记录 trace、指标和 bad case。
- 补 LLM request timeout 的底层绑定。
- 将 LLM 调用次数、失败次数、fallback decision 和预算消耗接入指标与审计。

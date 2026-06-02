# Intent Hub P2-4 模型服务适配审查

## 审查结论

通过。

P2-4 已完成真实模型服务 adapter 的最小闭环：模型服务作为规则之后、LLM 之前的候选来源接入识别链路，默认关闭或无 baseUrl 时 no-op，不影响规则主链路和既有 P1/P2 行为。

## 范围

本次审查覆盖：

- 领域层模型服务端口与策略。
- 应用层识别策略顺序。
- 基础设施 HTTP/no-op adapter。
- 模型服务配置。
- 自动化测试。
- README、status、P1 设计和 HTML 阅读版同步。

## 实现结果

新增领域层：

- `ModelClientPort`
- `ModelRecognitionPolicy`

新增基础设施：

- `ModelServiceProperties`
- `ModelRecognitionRequest`
- `ModelRecognitionResponse`
- `HttpModelClientAdapter`
- `NoopModelClientAdapter`

策略顺序：

```text
RuleRecognitionPolicy -> ModelRecognitionPolicy -> LlmRecognizePolicy
```

配置：

```yaml
intent-hub:
  model-service:
    enabled: false
    base-url: ""
    timeout-ms: 2000
```

FastAPI 风格契约：

- 请求：`POST {baseUrl}/recognize`
- Request body：`text`、`sceneId`
- Response body：`intentCode`、`confidence`、`slots`、`explanation`

## 架构一致性

### 双阶段路由

模型服务仍属于“怎么认”的前置识别策略，不参与后置动作选择。后置路由仍由 `IntentResult` 的意图、槽位和 decision 驱动。

### LLM 受控

模型服务被放在 LLM 之前，避免普通低置信样本直接进入 LLM。LLM 仍由 `llmPolicy.enabled()` 控制，是最后一道防线。

### 防腐层

模型服务只返回识别候选，不返回 SQL、不返回业务执行动作、不接触下游业务库。

### DDD 分层

领域层只定义 `ModelClientPort` 和策略抽象；HTTP 调用位于 infrastructure；interfaces 仅提供配置文件。领域层未依赖 Spring Web 或 RestClient。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 29 个。
- 失败 0，错误 0，跳过 0。

覆盖点：

- `RecognizeAppServiceTest` 覆盖模型候选参与识别，并验证路径为 Rule -> Model -> PostRoute。
- `ModelClientAdapterTest` 覆盖 no-op adapter 与 inactive HTTP adapter。
- 既有 P1、P2-1、P2-2、P2-3 测试仍全部通过。

## 当前限制

- 未提供 FastAPI 模型服务样例工程。
- 未执行真实 HTTP 冒烟。
- `timeoutMs` 目前仅作为配置保留，尚未绑定到底层 HTTP request timeout。
- 模型服务开关是全局配置，尚未纳入 `tenant + scene + version` 的配置治理。

## 后续建议

- 增加 `examples/model-service-fastapi` 或独立 `intent-model-service` 示例。
- 使用本地 FastAPI 服务做 HTTP 冒烟。
- 将模型开关、阈值、endpoint 和 timeout 纳入 `nlu_strategy` 或 scene 级策略配置。
- 后续 GPU/高并发部署再切 Triton，不影响当前 adapter 端口。

# Intent Hub P2-4 模型服务适配审查

## 审查结论

通过。

P2-4 已完成真实模型服务 adapter 的最小闭环：模型服务作为规则之后、LLM 之前的候选来源接入识别链路，默认关闭或无 baseUrl 时 no-op，不影响规则主链路和既有 P1/P2 行为；同时已通过 `GET {baseUrl}/health` 接入 Admin 健康检查。

## 范围

本次审查覆盖：

- 领域层模型服务端口与策略。
- 应用层识别策略顺序。
- 基础设施 HTTP/no-op adapter。
- 模型服务配置。
- FastAPI 示例容器化配置。
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

FastAPI 示例部署化入口：

- `examples/model-service-fastapi/Dockerfile`
- `examples/model-service-fastapi/docker-compose.yml`
- `scripts/validate-model-service-container.ps1`

## 架构一致性

### 双阶段路由

模型服务仍属于“怎么认”的前置识别策略，不参与后置动作选择。后置路由仍由 `IntentResult` 的意图、槽位和 decision 驱动。

### LLM 受控

模型服务被放在 LLM 之前，避免普通低置信样本直接进入 LLM。LLM 仍由 `llmPolicy.enabled()` 控制，是最后一道防线。

### 防腐层

模型服务只返回识别候选，不返回 SQL、不返回业务执行动作、不接触下游业务库。

模型服务异常时，领域策略记录 `MODEL_FALLBACK:CLOSED` 并返回空候选，让链路继续进入 LLM 兜底或拒识，避免外部模型不可用直接打断识别入口。

`MODEL_FALLBACK` 会进入最小指标口径，支持通过 `GET /api/v1/admin/metrics` 和 Prometheus 文本观察模型服务失败关闭次数。

### DDD 分层

领域层只定义 `ModelClientPort` 和策略抽象；HTTP 调用位于 infrastructure；interfaces 仅提供配置文件。领域层未依赖 Spring Web 或 RestClient。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 45 个。
- 失败 0，错误 0，跳过 0。

覆盖点：

- `RecognizeAppServiceTest` 覆盖模型候选参与识别，并验证路径为 Rule -> Model -> PostRoute。
- `RecognizeAppServiceTest` 覆盖模型服务异常时失败关闭，并验证路径包含 `MODEL_FALLBACK:CLOSED`。
- `ModelClientAdapterTest` 覆盖 no-op adapter、inactive HTTP adapter、MockRestServiceServer 成功返回、模型服务健康检查，以及 JDK 本地 HTTP server POST/JSON 解析冒烟。
- `AdminHealthControllerTest` 覆盖 `GET /api/v1/admin/health` 返回模型服务健康状态。
- `IntentHubBeanConfigurationTest` 覆盖 `RestClient.Builder` Bean，防止真实 jar 启动时 HTTP adapter 依赖缺失。
- 既有 P1、P2-1、P2-2、P2-3 测试仍全部通过。
- `scripts/validate-model-service-container.ps1` 校验 Dockerfile、Docker Compose、端口映射、健康检查和 `docker compose config`，不启动容器。

## 当前限制

- 已新增 `examples/model-service-fastapi`，提供 `/health` 和 `/recognize` 最小样例工程。
- 已补齐 `examples/model-service-fastapi` 的 Dockerfile、Docker Compose 和容器化配置校验脚本。
- 已执行 JDK 本地 HTTP server 冒烟；FastAPI 示例工程已本地冒烟通过；模型服务健康检查已接入 Admin health；已启动 jar + FastAPI 示例完成本地真实联调。
- 本地真实联调证据：`GET /api/v1/admin/health` 返回 `model_service.healthy=true`；`POST /api/v1/intent/recognize` 使用 `cancel A100` 返回 `ORDER_CANCEL/ASYNC_ACCEPTED`，路径包含 `ModelRecognitionPolicy`。
- `timeoutMs` 已通过 `SimpleClientHttpRequestFactory` 绑定到底层 HTTP connect/read timeout。
- 模型服务开关是全局配置，尚未纳入 `tenant + scene + version` 的配置治理。
- 尚未真实启动模型服务容器并让 Intent Hub 指向容器地址完成端到端冒烟。

## 后续建议

- 将 `examples/model-service-fastapi` 继续扩展为更贴近真实模型服务的样本/阈值/版本示例。
- 启动 Docker Compose 模型服务容器，确认网络、健康检查、识别请求和超时策略在实际环境中一致。
- 将模型开关、阈值、endpoint 和 timeout 纳入 `nlu_strategy` 或 scene 级策略配置。
- 后续 GPU/高并发部署再切 Triton，不影响当前 adapter 端口。

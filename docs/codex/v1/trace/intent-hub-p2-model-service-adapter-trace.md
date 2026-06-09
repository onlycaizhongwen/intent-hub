# Intent Hub P2-4 模型服务适配审查

## 审查结论

通过。

P2-4 已完成真实模型服务 adapter 的最小闭环：模型服务作为规则之后、LLM 之前的候选来源接入识别链路，默认关闭或无 baseUrl 时 no-op，不影响规则主链路和既有 P1/P2 行为；同时已通过 `GET {baseUrl}/health` 接入 Admin 健康检查，并补齐 scene 级 `model_policy` 的模型参与门禁、最低置信度过滤、endpoint/timeout 动态路由、场景客户端缓存复用、token 引用鉴权和缺 token 失败关闭。

## 范围

本次审查覆盖：

- 领域层模型服务端口与策略。
- 应用层识别策略顺序。
- 基础设施 HTTP/no-op adapter。
- 模型服务配置。
- scene 级 `model_policy` 读取与门禁。
- FastAPI 示例容器化配置。
- 自动化测试。
- README、status、P1 设计和 HTML 阅读版同步。

## 实现结果

新增领域层：

- `ModelClientPort`
- `ModelRecognitionPolicy`
- `ModelPolicy`

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

已发布 `nlu_strategy.model_policy` 示例：

```json
{
  "enabled": true,
  "endpoint": "https://model.example.test",
  "timeoutMs": 1800,
  "minConfidence": 0.72,
  "authTokenRef": "INTENT_HUB_MODEL_TOKEN"
}
```

当前运行时语义：

- `enabled=false`：该 scene 跳过模型服务识别，识别路径记录 `MODEL_POLICY:DISABLED`。
- `minConfidence`：模型候选低于该阈值时丢弃，识别路径记录 `MODEL_POLICY:LOW_CONFIDENCE`。
- `endpoint`、`timeoutMs`：已进入配置治理和 Flyway Schema，并已参与运行时动态模型服务路由。
- `authTokenRef`：只保存环境变量或系统属性引用名，运行时解析 token 后注入 `Authorization: Bearer ...`，不把明文 token 写入 DB、文档或代码仓库；引用存在但 token 无法解析时记录 `MODEL_FALLBACK:AUTH_MISSING_TOKEN` 并失败关闭，不发无鉴权请求。

FastAPI 风格契约：

- 请求：`POST {baseUrl}/recognize`
- Request body：`text`、`sceneId`
- Response body：`intentCode`、`confidence`、`slots`、`explanation`

FastAPI 示例部署化入口：

- `examples/model-service-fastapi/Dockerfile`
- `examples/model-service-fastapi/docker-compose.yml`
- `scripts/validate-model-service-container.ps1`
- `scripts/smoke-model-service-e2e.ps1`
- `scripts/smoke-model-policy-jdbc.ps1`

FastAPI 示例扩展字段：

- `modelVersion`：当前样例返回 `fastapi-example-2026-06-08`，用于验证模型版本透出。
- `threshold`：当前样例返回 `0.70`，用于验证阈值配置透出。
- Java adapter 识别链路当前仍只消费 `intentCode`、`confidence`、`slots`、`explanation`；`modelVersion` 与 `threshold` 只通过健康详情进入 Admin health 和 smoke 断言，不参与下游动作。

## 架构一致性

### 双阶段路由

模型服务仍属于“怎么认”的前置识别策略，不参与后置动作选择。后置路由仍由 `IntentResult` 的意图、槽位和 decision 驱动。

### LLM 受控

模型服务被放在 LLM 之前，避免普通低置信样本直接进入 LLM。LLM 仍由 `llmPolicy.enabled()` 控制，是最后一道防线。

### 防腐层

模型服务只返回识别候选，不返回 SQL、不返回业务执行动作、不接触下游业务库。

模型服务异常时，领域策略记录 `MODEL_FALLBACK:CLOSED` 并返回空候选，让链路继续进入 LLM 兜底或拒识，避免外部模型不可用直接打断识别入口。

`MODEL_FALLBACK` 会进入最小指标口径，支持通过 `GET /api/v1/admin/metrics` 和 Prometheus 文本观察模型服务失败关闭次数。

模型服务鉴权引用缺失时，基础设施 adapter 抛出受控鉴权异常，领域策略记录 `MODEL_FALLBACK:AUTH_MISSING_TOKEN`。该路径只暴露缺失原因，不暴露 token 值或引用解析结果，并同样进入模型 fallback 指标口径。

### DDD 分层

领域层只定义 `ModelClientPort` 和策略抽象；HTTP 调用位于 infrastructure；interfaces 仅提供配置文件。领域层未依赖 Spring Web 或 RestClient。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 63 个。
- 失败 0，错误 0，跳过 0。

覆盖点：

- `RecognizeAppServiceTest` 覆盖模型候选参与识别，并验证路径为 Rule -> Model -> PostRoute。
- `RecognizeAppServiceTest` 覆盖 scene 级 `model_policy.enabled=false` 跳过模型，以及 `minConfidence` 过滤低置信模型候选。
- `RecognizeAppServiceTest` 覆盖模型服务异常时失败关闭，并验证路径包含 `MODEL_FALLBACK:CLOSED`。
- `RecognizeAppServiceTest` 覆盖模型服务 token 引用缺失时失败关闭，并验证路径包含 `MODEL_FALLBACK:AUTH_MISSING_TOKEN` 且写入 bad case。
- `JdbcSceneConfigRepositoryTest` 覆盖从已发布 `nlu_strategy.model_policy` 读取模型开关、endpoint、timeout、最低置信度和 `authTokenRef`。
- `ConfigVersionAppServiceTest` 覆盖 Admin 策略对象 upsert 时保留 `modelPolicy` 和 `authTokenRef`，防止应用层规范化吞掉模型策略。
- `ModelClientAdapterTest` 覆盖 no-op adapter、inactive HTTP adapter、MockRestServiceServer 成功返回、模型服务健康详情、JDK 本地 HTTP server POST/JSON 解析冒烟、scene endpoint/timeout 动态路由、客户端缓存复用、Bearer 鉴权头注入，以及 token 引用缺失时不调用远端模型服务。
- `AdminHealthControllerTest` 覆盖 `GET /api/v1/admin/health` 返回模型服务健康状态、模型版本和阈值。
- `IntentHubBeanConfigurationTest` 覆盖 `RestClient.Builder` Bean，防止真实 jar 启动时 HTTP adapter 依赖缺失。
- 既有 P1、P2-1、P2-2、P2-3 测试仍全部通过。
- `scripts/validate-model-service-container.ps1` 校验 Dockerfile、Docker Compose、端口映射、健康检查和 `docker compose config`，不启动容器。
- `scripts/smoke-model-service-e2e.ps1` 自动打包 Intent Hub jar、启动模型服务容器、验证直连模型识别、启动 Intent Hub、验证 `model_service.healthy=true`、`model_service.modelVersion` 和 `ModelRecognitionPolicy` 识别路径，并在结束后清理进程与容器。
- `scripts/smoke-model-policy-jdbc.ps1` 自动启动临时 PostgreSQL 16 空库，使用 `local-jdbc` profile 验证 Flyway V1/V2/V3、`nlu_strategy.model_policy` 字段、Admin `modelPolicy` 写入/查询、发布配置读取和 `MODEL_POLICY:DISABLED` 识别路径。
- FastAPI 示例覆盖 `ORDER_CANCEL`、`ORDER_QUERY`、`REFUND_APPLY`、`LOGISTICS_QUERY`、`INVOICE_APPLY` 多意图样本，且 smoke 脚本会断言 `modelVersion`，防止误用旧镜像或旧服务。

## 当前限制

- 已新增 `examples/model-service-fastapi`，提供 `/health` 和 `/recognize` 最小样例工程。
- 已补齐 `examples/model-service-fastapi` 的 Dockerfile、Docker Compose 和容器化配置校验脚本。
- 已执行 JDK 本地 HTTP server 冒烟；FastAPI 示例工程已本地冒烟通过；模型服务健康检查已接入 Admin health；已启动 jar + FastAPI 示例完成本地真实联调。
- 本地真实联调证据：`GET /api/v1/admin/health` 返回 `model_service.healthy=true`，当前健康详情可透出模型服务 `modelVersion` 与 `threshold`；`POST /api/v1/intent/recognize` 使用 `cancel A100` 返回 `ORDER_CANCEL/ASYNC_ACCEPTED`，路径包含 `ModelRecognitionPolicy`。
- 容器端到端联调证据：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-model-service-e2e.ps1` 已完整通过，覆盖 Docker 模型服务容器与 Intent Hub jar 的本地端到端链路。
- 全局 `timeoutMs` 已通过 `SimpleClientHttpRequestFactory` 绑定到底层 HTTP connect/read timeout。
- scene 级 `model_policy.enabled/minConfidence/endpoint/timeoutMs/authTokenRef` 已纳入 `tenant + scene + version` 配置治理；运行时 HTTP adapter 已按 scene 动态切换 endpoint/timeout，并按 endpoint + timeout 复用客户端。
- 已修复 Admin 应用层策略规范化遗漏 `modelPolicy` 的问题；此前 repository 已支持 `model_policy`，但 HTTP Admin 真链路会把该字段落成 `{}`，现已通过单测和真实 JDBC smoke 锁定。
- `authTokenRef` 已完成 token 引用解析、出站 Bearer header 注入和引用缺失失败关闭；真实生产仍需 Secret/Vault、K8s Secret、TLS、轮换和审计策略。

## 后续建议

- 将模型健康详情继续纳入发布前 smoke 和观测面板，用于识别旧镜像、旧模型或错误阈值配置。
- 将模型服务 `authTokenRef` 接入 Secret/Vault 或 K8s Secret 管理，补齐租户级密钥轮换、TLS、鉴权审计和失败关闭告警。
- 将 `scripts/smoke-model-service-e2e.ps1` 纳入后续 CI 或发布前检查，作为模型 adapter 与容器配置变更后的回归入口。
- 将 `scripts/smoke-model-policy-jdbc.ps1` 纳入发布前检查，作为 Flyway 增量迁移和 Admin 配置治理链路的回归入口。
- 后续 GPU/高并发部署再切 Triton，不影响当前 adapter 端口。

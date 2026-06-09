# P2-4 真实模型服务适配

## 恢复胶囊

- 任务需求：继续 P2，接入一个真实模型服务的最小适配，FastAPI 优先，Triton 后置。
- 关键决策：模型服务作为规则之后、LLM 之前的候选来源；默认关闭时 no-op，不影响规则主链路；全局 base-url 可由 scene 级 `modelPolicy.endpoint` 覆盖或补齐。
- 当前阶段：P2.x 模型服务动态路由已补齐；已支持 scene 级 `modelPolicy.endpoint/timeoutMs` 覆盖模型服务请求目标和超时，按 endpoint + timeout 复用场景客户端，并支持 `authTokenRef` 引用环境变量/系统属性注入 Bearer 鉴权头；引用存在但 token 解析失败时会失败关闭，不发无鉴权模型请求。
- 已完成产物：模型端口/策略、HTTP/no-op adapter、scene 级 endpoint/timeout 动态路由、场景客户端缓存复用、模型服务 token 引用鉴权与缺失失败关闭、配置、测试、README/status/design/HTML/trace 文档同步、FastAPI 示例、HTTP 冒烟、健康检查接入、本地真实联调、FastAPI 示例 Dockerfile、Docker Compose 和容器化配置校验脚本。
- 剩余工作：生产级连接池治理与隔离、租户级鉴权体系、K8s 服务发现、真实多实例压测与模型版本策略细化。
- 重要发现：当前 `IntentRecognizer` 只组合 `RuleRecognitionPolicy` 和 `LlmRecognizePolicy`；`LlmRecognizePolicy` 受 `sceneConfig.llmPolicy.enabled()` 控制。

## 步骤列表

- [v] 建立 P2-4 任务记录。
- [v] 实现模型服务最小适配。
  - 当前产物：`ModelClientPort`、`ModelRecognitionPolicy`、`HttpModelClientAdapter`、`NoopModelClientAdapter`。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-domain`、`intent-hub-application`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补测试。
- [v] 同步 README/status/HTML/design/trace。
- [v] 运行测试并提交。
  - 当前产物：`mvn test` 已通过，共 29 个测试。
  - 下一步：已随 `main` 推送到 GitHub。
  - 涉及文件：全仓变更。
- [v] 补模型服务容器化配置样例。
  - 当前产物：`examples/model-service-fastapi/Dockerfile`、`examples/model-service-fastapi/docker-compose.yml`、`scripts/validate-model-service-container.ps1`。
  - 下一步：已完成；后续可启动容器后做 Intent Hub 端到端联调。
  - 涉及文件：`examples/model-service-fastapi`、`scripts`、status、HTML、trace。

## 研究发现

- P2-4 不应让模型服务取代规则主链路；规则仍是主力，模型服务是受控候选来源。
- 模型服务应位于 LLM 之前，避免普通低置信样本直接进入 LLM。
- FastAPI 最小请求/响应建议：
  - request：`text`、`sceneId`
  - response：`intentCode`、`confidence`、`slots`、`explanation`
- 默认 no-op 能保证未配置模型服务时，现有 26 个测试和本地启动不受影响。
- P2-4 首版完成后，`mvn test` 通过，共 29 个测试；后续 P2 批次已推进到 43 个测试。
- P2-4 后续已补 FastAPI 示例、JDK 本地 HTTP server 冒烟、模型服务健康检查、本地真实联调、容器化配置样例、scene 级 endpoint/timeout 动态路由、endpoint + timeout 维度的客户端缓存复用、`authTokenRef` Bearer 鉴权头注入和 token 引用缺失失败关闭；生产级连接池治理、租户级鉴权体系与多实例压测仍待补。

## 2026-06-04 补充记录：模型服务容器化配置样例

- 本轮目标：为 `examples/model-service-fastapi` 补齐可重复的容器化启动入口，降低后续部署化联调成本。
- 已完成：新增 Dockerfile、Docker Compose、`.dockerignore` 和 `scripts/validate-model-service-container.ps1`；同步示例 README、status、HTML 生命周期页和 P2-4 trace。
- 检查口径：Dockerfile 基础镜像、依赖安装、端口暴露、uvicorn 启动命令，compose 服务名、端口映射、健康检查和 `docker compose config`。
- 边界：当前只校验配置并提供样例容器；未启动容器，未执行 Intent Hub 指向容器模型服务的端到端请求；生产部署仍需模型版本、资源限制、认证、TLS、服务发现和高可用。

## 错误记录

- P2-4 提交 `4e3b58e Insert model recognition before LLM fallback` 已推送到 GitHub。

## 2026-06-08 补充记录：scene 级模型 endpoint/timeout 动态路由

- 本轮目标：关闭 `nlu_strategy.model_policy.endpoint/timeoutMs` 只入库不参与运行时请求的缺口，让不同 scene 可以选择不同模型服务地址和超时策略。
- 已完成：`ModelRecognitionPolicy` 将 `ModelPolicy` 传给 `ModelClientPort`；`HttpModelClientAdapter` 支持按 scene 策略动态选择请求客户端，并按 endpoint + timeout 缓存复用；全局 `enabled=false` 仍完全关闭模型链路，未配置 scene endpoint 时继续走全局 base-url。
- 验证证据：`RecognizeAppServiceTest#passesSceneModelPolicyToModelClient` 覆盖策略传递；`ModelClientAdapterTest` 覆盖 scene endpoint 覆盖全局 base-url、无全局 base-url 时由 scene endpoint 单独生效、相同 endpoint + timeout 复用缓存客户端，以及旧全局 base-url 路径。
- 边界：当前是最小动态路由与客户端缓存复用，不做生产级连接池治理、租户鉴权、K8s 服务发现或真实多实例压测。

## 2026-06-08 补充记录：模型服务 token 引用鉴权

- 本轮目标：推进模型服务生产化待办中的鉴权能力，但不把明文密钥写入 DB、配置文档或代码仓库。
- 已完成：`ModelPolicy` 增加 `authTokenRef`；Admin 策略对象会保留并校验该引用；JDBC 已发布配置读取会把 `authTokenRef` 带入运行时；`HttpModelClientAdapter` 运行时从系统属性或环境变量解析 token，并对场景模型请求注入 `Authorization: Bearer ...`；如果配置了引用但解析不到 token，则抛出受控鉴权异常并由识别策略记录 `MODEL_FALLBACK:AUTH_MISSING_TOKEN`，不向模型服务发无鉴权请求。
- 验证证据：`ConfigVersionAppServiceTest#preservesModelPolicyWhenUpsertingStrategy` 覆盖 Admin 保留 token 引用；`JdbcSceneConfigRepositoryTest#loadsLlmPolicyFromPublishedStrategy` 覆盖 JDBC 读取 token 引用；`ModelClientAdapterTest#httpAdapterSendsBearerTokenFromSceneModelPolicyTokenRef` 覆盖 HTTP Bearer header 注入；`ModelClientAdapterTest#httpAdapterFailsClosedWithoutCallingRemoteWhenTokenRefCannotBeResolved` 覆盖 token 引用缺失时不外呼；`RecognizeAppServiceTest#failsClosedWithAuthMarkerWhenModelServiceTokenReferenceIsMissing` 覆盖识别路径和 bad case 记录。
- 边界：当前支持 token 引用解析、出站 Bearer 头注入和缺 token 失败关闭；真实生产仍需租户级密钥分发、轮换、TLS、K8s Secret/Vault 接入和审计策略。

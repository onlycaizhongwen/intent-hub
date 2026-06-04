# P2-4 真实模型服务适配

## 恢复胶囊

- 任务需求：继续 P2，接入一个真实模型服务的最小适配，FastAPI 优先，Triton 后置。
- 关键决策：模型服务作为规则之后、LLM 之前的候选来源；默认关闭或无 `base-url` 时 no-op，不影响规则主链路；不修改 `SceneConfig` 契约。
- 当前阶段：P2.x 模型服务部署化联调准备进行中；已补 FastAPI 示例容器化配置与不启动容器的配置校验脚本。
- 已完成产物：模型端口/策略、HTTP/no-op adapter、配置、测试、README/status/design/HTML/trace 文档同步、FastAPI 示例、HTTP 冒烟、健康检查接入、本地真实联调、FastAPI 示例 Dockerfile、Docker Compose 和容器化配置校验脚本。
- 剩余工作：真实 `docker compose up --build -d` 联调、Intent Hub 指向容器模型服务的端到端冒烟、样本/阈值/模型版本策略细化。
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
- P2-4 后续已补 FastAPI 示例、JDK 本地 HTTP server 冒烟、模型服务健康检查、本地真实联调和容器化配置样例；真实启动容器后的部署化联调仍待补。

## 2026-06-04 补充记录：模型服务容器化配置样例

- 本轮目标：为 `examples/model-service-fastapi` 补齐可重复的容器化启动入口，降低后续部署化联调成本。
- 已完成：新增 Dockerfile、Docker Compose、`.dockerignore` 和 `scripts/validate-model-service-container.ps1`；同步示例 README、status、HTML 生命周期页和 P2-4 trace。
- 检查口径：Dockerfile 基础镜像、依赖安装、端口暴露、uvicorn 启动命令，compose 服务名、端口映射、健康检查和 `docker compose config`。
- 边界：当前只校验配置并提供样例容器；未启动容器，未执行 Intent Hub 指向容器模型服务的端到端请求；生产部署仍需模型版本、资源限制、认证、TLS、服务发现和高可用。

## 错误记录

- P2-4 提交 `4e3b58e Insert model recognition before LLM fallback` 已推送到 GitHub。

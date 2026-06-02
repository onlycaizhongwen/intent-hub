# P2-4 真实模型服务适配

## 恢复胶囊

- 任务需求：继续 P2，接入一个真实模型服务的最小适配，FastAPI 优先，Triton 后置。
- 关键决策：模型服务作为规则之后、LLM 之前的候选来源；默认关闭或无 `base-url` 时 no-op，不影响规则主链路；不修改 `SceneConfig` 契约。
- 当前阶段：已完成，待提交；最后一笔 task-control 状态提交仍本地领先，GitHub 443 超时。
- 已完成产物：模型端口/策略、HTTP/no-op adapter、配置、测试、README/status/design/HTML/trace 文档同步。
- 剩余工作：提交并推送。
- 重要发现：当前 `IntentRecognizer` 只组合 `RuleRecognitionPolicy` 和 `LlmRecognizePolicy`；`LlmRecognizePolicy` 受 `sceneConfig.llmPolicy.enabled()` 控制。

## 步骤列表

- [v] 建立 P2-4 任务记录。
- [v] 实现模型服务最小适配。
  - 当前产物：`ModelClientPort`、`ModelRecognitionPolicy`、`HttpModelClientAdapter`、`NoopModelClientAdapter`。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-domain`、`intent-hub-application`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补测试。
- [v] 同步 README/status/HTML/design/trace。
- [~] 运行测试并提交。
  - 当前产物：`mvn test` 已通过，共 29 个测试。
  - 下一步：提交并推送。
  - 涉及文件：全仓变更。

## 研究发现

- P2-4 不应让模型服务取代规则主链路；规则仍是主力，模型服务是受控候选来源。
- 模型服务应位于 LLM 之前，避免普通低置信样本直接进入 LLM。
- FastAPI 最小请求/响应建议：
  - request：`text`、`sceneId`
  - response：`intentCode`、`confidence`、`slots`、`explanation`
- 默认 no-op 能保证未配置模型服务时，现有 26 个测试和本地启动不受影响。
- P2-4 已完成后，`mvn test` 通过，共 29 个测试。
- P2-4 当前只完成 adapter；真实 FastAPI 服务示例和 HTTP 冒烟后续补。

## 错误记录

- `f9d7fc8 Record completed P2 handoff state` 本地领先 1 个，push 因 GitHub 443 超时暂未成功。

# P2-5 LLM 受控兜底

## 恢复胶囊

- 任务需求：继续 P2，完成小流量 LLM 受控兜底的最小闭环。
- 关键决策：LLM 仍是最后一道防线；默认关闭且预算为 0；只有全局治理配置和 scene 级 `llm_policy` 同时允许时才触发；失败按 fallback decision 关闭。
- 当前阶段：已完成，待提交。
- 已完成产物：LLM 领域策略门禁、基础设施治理配置、HTTP adapter、JDBC 策略读取、测试、README/status/HTML/trace 同步。
- 剩余工作：提交、推送和远端确认。
- 重要发现：当前 Spring AI Alibaba 依赖已作为 optional 存在，但 P2-5 先用 HTTP 契约 adapter 固化治理边界，后续再升级真实 `ChatClient`。

## 步骤列表

- [v] 建立 P2-5 任务记录。
- [v] 实现 LLM 治理门禁和 adapter。
  - 当前产物：`LlmGovernanceProperties`、`TongyiLlmAdapter`、`LlmRecognitionRequest`、`LlmRecognitionResponse`。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-domain`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补已发布 `llm_policy` 读取。
  - 当前产物：`JdbcSceneConfigRepository` 从 `nlu_strategy.llm_policy` 读取策略。
  - 下一步：已完成。
- [v] 补测试。
  - 当前产物：`mvn test` 已通过，共 36 个测试。
  - 下一步：已完成。
- [v] 同步 README/status/HTML/trace。
- [~] 提交并推送。
  - 当前产物：本地变更已验证。
  - 下一步：提交并推送。

## 研究发现

- LLM 门禁应在领域策略层先拦截预算为 0 或 timeout 为 0 的场景，避免指标误算 LLM fallback。
- 基础设施 adapter 仍需二次检查全局治理开关、baseUrl 和全局预算，防止误配置导致外部调用。
- 当前 P2-5 还不是完整生产 LLM：没有真实 DashScope 沙箱冒烟、没有按日预算扣减、没有底层 HTTP timeout 绑定。

## 错误记录

- 首次 `mvn test` 因新增断言漏掉既有 `POST_ROUTE:NONE` 路径失败，已按现有后置路由行为修正断言后全量通过。

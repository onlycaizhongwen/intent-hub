# 恢复胶囊

- 任务需求：将 P1 阶段默认采用 Spring AI Alibaba、保持 Spring AI 接口抽象，以及 Maven Module 代码骨架划分固化到正式文档和 HTML。
- 关键决策：Spring AI Alibaba 是 P1 默认 Provider 实现，不是核心领域依赖；核心链路通过 Spring AI/自定义端口保持供应商可替换。
- 当前阶段：已完成。
- 已完成产物：`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`、`docs/codex/v1/trace/intent-hub-tech-selection-confirmation.md`、`docs/codex/v1/designs/intent-hub-design.md`、`docs/codex/v1/intent-hub-lifecycle.html`、`docs/codex/v1/status.md`。
- 剩余工作：无。
- 重要发现：现有文档只写 Spring AI Provider Adapter，尚未明确 Spring AI Alibaba 默认实现，也未给出 Maven Module 代码骨架。

## 步骤列表

- [v] 定位现有 P1 技术基线、LLM 策略、HTML 技术选型和状态文件。
- [v] 固化 Spring AI Alibaba 默认实现与 Maven Module 划分。
- [v] 验证关键词和旧口径。

## 研究发现

- Spring AI Alibaba 官方说明其基于 Spring AI 构建，是通义系列模型及服务在 Java AI 应用开发中的实践方案。
- P1 仍应坚持 LLM 受控：可以默认使用 Spring AI Alibaba 作为实现组件，但不能让真实 LLM 默认进入主识别链路。
- 已验证关键词：Spring AI Alibaba、TongyiLlmAdapter、intent-hub-application、intent-hub-domain、intent-hub-infrastructure、intent-hub-interfaces、Maven Module。
- 已验证旧口径：正式设计/trace/HTML 中不再保留单独的 `Spring AI 抽象 + Provider Adapter` 作为 P1 默认实现表述。

## 错误记录

- 暂无。

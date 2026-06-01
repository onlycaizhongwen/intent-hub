# 恢复胶囊

- 任务需求：开始 P1 最小识别闭环，实现 Maven 多模块 DDD 工程骨架和可运行的最小识别主链路。
- 关键决策：先做内存版最小闭环；PostgreSQL/Redis/Kafka/Nacos/Spring AI Alibaba 保留模块与适配边界，不阻塞第一步闭环。
- 当前阶段：第一步已完成，等待 JDK 17+ 和 Maven 环境做真实编译/运行验证。
- 已完成产物：Maven 多模块工程、领域契约、应用服务、基础设施 stub、REST API、status/HTML/P1 设计实现进展同步。
- 剩余工作：在 JDK 17+ 与 Maven 环境执行 `mvn clean package` 和接口试跑；之后接入 DB migration、Admin Portal 配置 API 和真实持久化。
- 重要发现：当前机器只有 Java 8 且未安装 Maven，无法本机编译 Spring Boot 4 / Java 17 工程；本轮以结构和静态校验为主，真实编译需 JDK 17+ 和 Maven。

## 步骤列表

- [v] 检查仓库结构、Java/Maven 环境、P1 设计文档。
- [v] 创建 P1 Maven 多模块工程骨架和最小闭环代码。
- [v] 同步 status/HTML/P1 设计实现状态。
- [v] 运行可用验证并记录限制。

## 研究发现

- Spring AI Alibaba DashScope starter 坐标为 `com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope`，Maven Central 可见版本包括 `1.1.2.3` 和 `2.0.0-M1.1`。
- P1 仍应保持 LLM 不进入主链路，`TongyiLlmAdapter` 先做基础设施层 stub。
- 静态验证通过：domain/application 未引入 Spring Web、Spring AI Alibaba、DB、Redis、Kafka；Spring 和 Spring AI Alibaba 只在 infrastructure/interfaces。
- 关键闭环文件已落地：`RecognizeController`、`RecognizeAppService`、`RuleRecognitionPolicy`、`InMemorySceneConfigRepository`、`InMemoryRecognitionTraceRepository`、`InMemoryBadCaseRepository`、`InMemoryIdempotencyRepository`、`TongyiLlmAdapter`。

## 错误记录

- `mvn -version` 失败：当前环境未安装 Maven。
- `java -version` 显示 Java 8，不满足 P1 目标 Java 17/21。

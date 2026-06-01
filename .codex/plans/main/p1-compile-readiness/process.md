# P1-1 工程可编译与本地可启动准备过程

## 恢复胶囊

- 任务需求：用户说“继续”，按已固化的 P1 下一步计划推进。
- 关键决策：进入 P1-1，优先验证工程可编译与本地可启动；本机环境不满足 JDK 17+ 与 Maven 时，先做静态工程检查和可修复项。
- 当前阶段：已完成。
- 已完成产物：任务记录、环境检查脚本、缺槽澄清代码修正、P1 设计/计划/status 文档同步。
- 剩余工作：无；真实编译需切换到 JDK 17+ 与 Maven 环境后执行。
- 重要发现：`java -version` 为 1.8.0_251；`mvn` 不可用。

## 步骤列表

- [v] 检查 Java/Maven 环境。
- [v] 检查 POM 与源码结构。
  - 当前产物：`.codex/plans/main/p1-compile-readiness/process.md`
  - 下一步：已完成。
  - 涉及文件：`pom.xml`、`intent-hub-*/pom.xml`、`intent-hub-*/src/main/java/**`
- [v] 修复静态可发现问题。
- [v] 更新 P1 执行记录与验证结果。
- [v] 收口任务记录。

## 研究发现

- 当前 parent POM 使用 Spring Boot `4.0.0`、`java.version=17`。
- 当前机器无法执行 Maven 构建，真实编译需在 JDK 17+ 与 Maven 环境复验。
- 代码使用 Java 17+ 相关能力：record、switch expression，以及 Java 17 的 `HexFormat`。
- domain/application 未发现 Spring、Web、Spring AI Alibaba、DB、Redis、Kafka 依赖。
- `ORDER_CANCEL` 缺槽澄清路径已补齐：缺少 `order_id` 时返回 `CLARIFY`，不提前生成异步幂等键。

## 错误记录

- `mvn -version`：PowerShell 报 `mvn` 命令不存在。

## 验证记录

- `java -version`：当前为 `1.8.0_251`，不满足 P1 JDK 17+。
- `mvn -version`：命令不可用。
- `powershell -ExecutionPolicy Bypass -File scripts/check-p1-env.ps1`：正确失败并提示需要 JDK 17+。
- `rg -n "org\.springframework|jakarta|javax|java\.sql|redis|kafka|dashscope|alibaba|web\.bind" intent-hub-domain intent-hub-application -S`：未发现 domain/application 越层依赖。
- Java 文件花括号平衡检查：未发现不平衡文件。

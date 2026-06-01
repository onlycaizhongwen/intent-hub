# P1-5 可观测与数据回流闭环过程

## 恢复胶囊

- 任务需求：继续推进 P1，补齐可观测与数据回流最小闭环，让识别 trace 和 bad case 可以通过 Admin API 查询。
- 关键决策：P1-5 先做只读查询 API，不引入完整看板；识别写入链路不变，查询通过 application 端口隔离 JDBC/Web。
- 当前阶段：P1-5 最小查询闭环已完成，待提交推送。
- 已完成产物：任务记录、应用查询模型与端口、内存/JDBC 查询适配、Admin REST API、自动化测试、memory 与 local-jdbc 冒烟。
- 剩余工作：提交推送。
- 重要发现：现有 `recognition_trace` 与 `bad_case` 表字段已足够支撑最小查询；memory 模式已有内存列表但缺少面向 Admin 的查询模型。

## 步骤列表

- [v] 注册任务记录。
- [v] 实现可观测查询契约与 Admin API。
  - 当前产物：`ObservabilityAppService`、`ObservabilityQueryPort`、`RecognitionTraceRecord`、`BadCaseRecord`、`BadCaseQuery`、`AdminObservabilityController`。
- [v] 增加自动化测试并运行构建。
  - 验证结果：`mvn test` 通过，共 17 个测试。
  - 构建结果：`mvn -pl intent-hub-interfaces -am package -DskipTests` 通过。
  - memory 冒烟：`TRACE-OBS-SMOKE-003` 可查询到 `ORDER_QUERY/SUCCESS`，bad case 可按 `tenantId=demo&status=OPEN` 查询。
  - local-jdbc 冒烟：`TRACE-JDBC-OBS-003` 可从 PostgreSQL 查询到 `INVOICE_QUERY/SUCCESS`，路径包含 `PRE_ROUTE:order-scene:v-published-read-1` 和 `POST_ROUTE:INVOICE_QUERY_API`。
- [v] 同步 P1 文档、HTML 和 README。
- [~] 提交并推送。

## 研究发现

- P1-4 已完成已发布配置读取，识别结果会写入 `recognition_trace`。
- 低置信或拒识结果会写入 `bad_case`，默认状态来自数据库默认值 `OPEN`。
- 查询 API 应保持只读，避免把 bad case 标注流和管理流在 P1 最小阶段混入。
- PowerShell 直接内联中文 JSON 时可能出现控制台编码问题；冒烟使用 Unicode 转义请求体可避免请求文本乱码。
- `local-jdbc` 当前数据库最新发布配置为 `v-published-read-1` 发票示例，因此订单文本拒识、发票文本命中 `INVOICE_QUERY` 是符合当前库状态的结果。

## 错误记录

- 第一次测试编译失败：`List#getFirst()` 不属于 Java 17 API。已改为 `records.get(0)` 并复验通过。

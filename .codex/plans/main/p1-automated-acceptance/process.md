# P1-2 自动化验收固化过程

## 恢复胶囊

- 任务需求：继续推进 P1，把手工冒烟固化为自动化测试。
- 关键决策：应用层测试锁定 decision、缺槽、bad case、幂等；接口层测试锁定 HTTP 契约与 JSON 字段。
- 当前阶段：P1-2 自动化验收已完成，正在收口文档。
- 已完成产物：任务记录、应用层测试、接口层契约测试、Maven 验证结果、P1 文档同步。
- 剩余工作：无；下一步进入 P1-3 PostgreSQL/Flyway 持久化最小落地。
- 重要发现：当前 P1 可靠启动方式是 `mvn clean package` 后 `java -jar ...`；自动化测试应避免依赖外部服务。

## 步骤列表

- [v] 注册任务记录。
- [v] 新增应用层和接口层自动化测试。
  - 当前产物：`intent-hub-application/src/test/java/com/intenthub/application/RecognizeAppServiceTest.java`、`intent-hub-interfaces/src/test/java/com/intenthub/interfaces/web/RecognizeControllerTest.java`。
  - 已新增依赖：`intent-hub-application/pom.xml`、`intent-hub-interfaces/pom.xml` 中的 `spring-boot-starter-test`。
- [v] 运行 `mvn test` / `mvn clean package`。
  - 验证结果：`mvn test` 通过，共 9 个测试。
  - 验证结果：`mvn clean package` 通过，共 9 个测试。
- [v] 同步 P1 文档与 HTML。
  - 已同步：`docs/codex/v1/status.md`、`docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`、`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`、`docs/codex/v1/intent-hub-lifecycle.html`。

## 研究发现

- 当前最小验收用例：`SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED`、重复异步请求幂等键稳定。
- `InMemoryBadCaseRepository` 只记录 `REJECTED` 或置信度 `<0.60` 的结果。
- `InMemoryIdempotencyRepository` 按 `tenantId|requestId|actionCode` 生成稳定 SHA-256 key。
- 应用层测试覆盖：订单查询成功、订单取消异步接收并生成稳定幂等键、取消订单缺少 `order_id` 返回 `CLARIFY` 且不生成幂等键、未知意图拒识并记录 bad case。
- 接口层测试采用直接 Controller 契约测试，覆盖请求映射、trace_id 生成、缺槽澄清、未知意图拒识、重复异步取消请求幂等键稳定。

## 错误记录

- 曾尝试按 MockMvc 方式固化接口测试，但 Spring Boot 4 测试依赖拆分后暴露的导入与 Jackson 组合不符合当前最小工程形态；为避免为测试引入额外复杂度，改为直接 Controller 契约测试。

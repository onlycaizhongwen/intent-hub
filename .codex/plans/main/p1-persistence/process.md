# P1-3 PostgreSQL/Flyway 持久化最小落地过程

## 恢复胶囊

- 任务需求：继续推进 P1-3，把 trace、bad case、idempotency 从内存记录推进为 PostgreSQL/Flyway 最小持久化闭环。
- 关键决策：默认保持内存 fallback；通过配置开关启用 JDBC 持久化；domain/application 不引入 DB 依赖。
- 当前阶段：P1-3 真实 PostgreSQL/Flyway 联调已通过。
- 已完成产物：任务记录、Flyway migration、JDBC repository、配置开关、默认 memory fallback、`local-jdbc` PostgreSQL 联调验证。
- 剩余工作：进入 P1-4 Admin Portal 最小配置治理 API。
- 重要发现：Spring Boot 4 需要显式引入 `spring-boot-flyway` 才会加载 Flyway 自动配置；仅有 `flyway-core` 与 `flyway-database-postgresql` 不会自动执行 migration。

## 步骤列表

- [v] 注册任务记录。
- [v] 新增 Flyway migration 与 JDBC 持久化适配器。
  - 当前产物：`V1__p1_minimal_persistence.sql`、`JdbcRecognitionTraceRepository`、`JdbcBadCaseRepository`、`JdbcIdempotencyRepository`。
  - 已新增依赖：`spring-boot-starter-jdbc`、`flyway-core`、`flyway-database-postgresql`、PostgreSQL runtime driver、H2 runtime fallback。
- [v] 增加持久化配置开关。
  - 默认：`intent-hub.persistence.mode=memory` 且 `spring.flyway.enabled=false`。
  - JDBC：`local-jdbc` profile 使用 PostgreSQL，并启用 Flyway。
- [v] 运行 `mvn test` / `mvn clean package`。
  - 验证结果：`mvn test` 通过，共 9 个测试。
  - 验证结果：`mvn clean package` 通过，共 9 个测试。
  - 启动验证：默认 memory 模式 jar 可启动，`/api/v1/admin/health` 返回 `UP`。
- [v] 真实 PostgreSQL 联调。
  - 环境：Docker `postgres:16-alpine`，数据库 `intent_hub`，用户 `intent_hub`。
  - 修复：新增 `spring-boot-flyway`，使 Spring Boot 4 加载 `FlywayAutoConfiguration`。
  - Flyway：空库启动后成功应用 `V1 - p1 minimal persistence`，创建 12 张 public 表。
  - 接口：`local-jdbc` profile 下 `ORDER_QUERY` 返回 `SUCCESS`，`ORDER_CANCEL` 返回 `ASYNC_ACCEPTED`，未知意图返回 `REJECTED`。
  - 查询：`recognition_trace=7`，`bad_case=4`，`idempotency_record=1`。
  - 幂等：重复 `REQ-JDBC-102` 返回相同 idempotency key，`idempotency_record` 未新增重复记录。
- [v] 同步 P1 文档与 HTML。

## 研究发现

- 当前内存实现：`InMemoryRecognitionTraceRepository`、`InMemoryBadCaseRepository`、`InMemoryIdempotencyRepository`。
- 应用层端口保持稳定：`RecognitionTracePort`、`BadCasePort`、`IdempotencyPort`。
- 最小持久化应优先覆盖 `recognition_trace`、`bad_case`、`idempotency_record`，并落地 P1 必需配置表骨架。
- JDBC starter 加入 classpath 后，默认 memory 模式也会触发 DataSource 自动配置；已通过 H2 runtime fallback 保持本地默认启动可用，但业务 repository 仍由 `intent-hub.persistence.mode` 控制。
- Spring Boot 4 的 Flyway 自动配置在 `spring-boot-flyway` 模块中，后续新增数据库 migration 时必须保留该依赖。
- PowerShell 直接发送中文 JSON 曾出现编码偏差；真实联调用 Unicode escape JSON 验证了规则命中和数据库写入链路。

## 错误记录

- 首次 jar 启动失败：默认 memory 模式缺少 datasource driver，Spring Boot JDBC 自动配置无法确定 driver。已增加 H2 runtime fallback，并保持 Flyway 默认关闭、业务持久化默认仍为内存。
- 首次 `local-jdbc` 启动后接口 500：`recognition_trace` 不存在。根因是缺少 Spring Boot 4 的 `spring-boot-flyway` 自动配置模块，Flyway 未执行。已新增依赖并复验通过。

# TASKS

## P1-3 PostgreSQL/Flyway 持久化真实联调
- 状态：已完成
- 摘要：P1-3 已完成真实 PostgreSQL/Flyway 联调；Spring Boot 4 Flyway 自动配置依赖已补齐，空库 migration、trace、bad case、idempotency 和重复请求幂等均验证通过。
- 过程文件：`.codex/plans/main/p1-persistence/process.md`
- 恢复提示：读取 process.md，下一步从 P1-4 Admin Portal 最小配置治理 API 开始。

## P1-4 Admin Portal 最小配置治理 API
- 状态：已发布配置读取已完成
- 摘要：已实现配置版本草稿、查询、校验、发布、回滚、导入导出与审计端口/API；默认 memory 模式冒烟通过，local-jdbc 下已验证 config_version 与 audit_log 真实落库；已补配置对象最小 Upsert/List API，并完成识别链路读取 PostgreSQL 最新 PUBLISHED 配置的 JDBC 冒烟。
- 过程文件：`.codex/plans/main/p1-admin-config/process.md`
- 恢复提示：读取 process.md，从配置对象删除/批量导入、更多已发布配置读取场景或 P1-5 可观测与回流继续。

## P1-5 可观测与数据回流闭环
- 状态：待提交推送
- 摘要：已补齐识别 trace 与 bad case 的只读查询能力，提供 Admin API 支撑按 trace_id 定位识别路径，以及按租户/场景/意图/状态筛选 bad case；memory 与 local-jdbc 冒烟均通过。
- 过程文件：`.codex/plans/main/p1-observability/process.md`
- 恢复提示：读取 process.md，从提交和推送继续。

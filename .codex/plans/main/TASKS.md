# TASKS

## P1-3 PostgreSQL/Flyway 持久化真实联调
- 状态：已完成
- 摘要：P1-3 已完成真实 PostgreSQL/Flyway 联调；Spring Boot 4 Flyway 自动配置依赖已补齐，空库 migration、trace、bad case、idempotency 和重复请求幂等均验证通过。
- 过程文件：`.codex/plans/main/p1-persistence/process.md`
- 恢复提示：读取 process.md，下一步从 P1-4 Admin Portal 最小配置治理 API 开始。

## P1-4 Admin Portal 最小配置治理 API
- 状态：配置对象最小 CRUD 已完成
- 摘要：已实现配置版本草稿、查询、校验、发布、回滚、导入导出与审计端口/API；默认 memory 模式冒烟通过，local-jdbc 下已验证 config_version 与 audit_log 真实落库；已补意图、槽位、同义词、策略、路由、下游动作的最小 Upsert/List API，下一步切换识别配置读取到已发布版本。
- 过程文件：`.codex/plans/main/p1-admin-config/process.md`
- 恢复提示：读取 process.md，从识别配置读取已发布版本、配置对象删除/批量导入或 JDBC 对象 API 联调继续。

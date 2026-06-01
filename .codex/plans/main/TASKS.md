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
- 状态：已完成
- 摘要：已补齐识别 trace 与 bad case 的只读查询能力，提供 Admin API 支撑按 trace_id 定位识别路径，以及按租户/场景/意图/状态筛选 bad case；memory 与 local-jdbc 冒烟均通过。
- 过程文件：`.codex/plans/main/p1-observability/process.md`
- 恢复提示：P1-5 已提交并推送，提交 `e92ffa6`。

## P1-6 P1 退出评审与 P2 准入
- 状态：本地已提交，远端推送待网络恢复
- 摘要：已形成 P1 退出评审报告，结论为有条件通过；P1 可进入 P2 规划与试点扩展，遗留项进入 P2/P1.x。本地提交 `1a04b19` 已完成，GitHub 443 超时导致远端同步暂未确认。
- 过程文件：`.codex/plans/main/p1-exit-review/process.md`
- 恢复提示：网络恢复后执行 `git push origin main` 并确认远端 main 包含 `1a04b19`。

## P2-1 动态 scene 路由与多场景配置读取
- 状态：本地已提交，远端推送待确认
- 摘要：替换 P1 已发布配置读取中的固定 `order-scene`，实现 Envelope metadata 显式 scene 优先、已发布版本兜底、内置配置最终回退的最小动态 scene 读取闭环。本地提交 `8b9e187` 已完成。
- 过程文件：`.codex/plans/main/p2-dynamic-scene-routing/process.md`
- 恢复提示：网络恢复后执行 `git push origin main` 并确认远端 main 包含 `8b9e187`。

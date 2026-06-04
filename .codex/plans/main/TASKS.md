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
- 状态：已提交并推送
- 摘要：已形成 P1 退出评审报告，结论为有条件通过；P1 可进入 P2 规划与试点扩展，遗留项进入 P2/P1.x。已随 `main` 推送到 GitHub。
- 过程文件：`.codex/plans/main/p1-exit-review/process.md`
- 恢复提示：P1-6 已完成，可从 P2 规划与试点扩展继续。

## P2-1 动态 scene 路由与多场景配置读取
- 状态：已提交并推送
- 摘要：替换 P1 已发布配置读取中的固定 `order-scene`，实现 Envelope metadata 显式 scene 优先、已发布版本兜底、内置配置最终回退的最小动态 scene 读取闭环。已随 `main` 推送到 GitHub。
- 过程文件：`.codex/plans/main/p2-dynamic-scene-routing/process.md`
- 恢复提示：P2-1 已完成，可从后续 P2.x 继续。

## P2-2 Bad Case 标注流转与样本导出
- 状态：已提交并推送
- 摘要：在 P1 bad case 查询基础上补最小运营闭环：标注、关闭、导出训练样本格式，并保持 memory/JDBC 双实现；`mvn test` 已通过，共 24 个测试；已随 `main` 推送到 GitHub。
- 过程文件：`.codex/plans/main/p2-bad-case-workflow/process.md`
- 恢复提示：P2-2 已完成，可从 P2-3 或后续 P2.x 继续。

## P2-3 指标采集与观测接口
- 状态：已提交并推送
- 摘要：补最小指标采集、JSON 查询和 Prometheus 文本导出；暂不引入 Actuator/Micrometer，保持当前健康检查契约；`mvn test` 已通过，共 26 个测试；已推送到 GitHub。
- 过程文件：`.codex/plans/main/p2-metrics-observability/process.md`
- 恢复提示：P2-3 已完成，可从真实模型服务、小流量 LLM 兜底或 Micrometer/OpenTelemetry 桥接继续。

## P2-4 真实模型服务适配
- 状态：已提交并推送
- 摘要：接入 FastAPI 风格模型服务最小 adapter，默认关闭/no-op；模型候选位于规则之后、LLM 之前；`mvn test` 已通过，共 29 个测试。
- 过程文件：`.codex/plans/main/p2-model-service-adapter/process.md`
- 恢复提示：P2-4 已完成并推送，可从 P2-5 或后续 P2.x 继续。

## P2-5 LLM 受控兜底
- 状态：已提交并推送
- 摘要：补全 LLM 受控兜底最小闭环：全局治理配置、scene 级 `llm_policy` 读取、预算/超时门禁、有限重试和 fallback 失败关闭；并补齐 HTTP timeout 绑定、模型服务失败关闭、fallback 指标口径、LLM 预算消费最小计数与持久化审计、外呼前日预算原子预占、同步失败释放、默认关闭的 stale pending 后台补偿、补偿指标、管理端 confirmed/reserved/pending 预算查询、模型 adapter 本地 HTTP 冒烟、模型服务健康检查、本地真实联调、Spring AI Alibaba `ChatClient` 预接入、DashScope 沙箱冒烟 profile/script、运维告警样例和本地观测栈校验脚本；默认关闭且预算为 0，不影响规则和模型主链路；已随 `main` 推送到 GitHub。
- 过程文件：`.codex/plans/main/p2-llm-governance/process.md`
- 恢复提示：读取 process.md，继续 P2.x 真实 DashScope 冒烟、告警/真实多实例压测或模型服务部署化联调；后续新提交完成后，推送 GitHub 仍需用户单独指令。

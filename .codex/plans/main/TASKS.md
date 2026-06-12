# TASKS

## P1-3 PostgreSQL/Flyway 持久化真实联调
- 状态：已完成
- 摘要：P1-3 已完成真实 PostgreSQL/Flyway 联调；Spring Boot 4 Flyway 自动配置依赖已补齐，空库 migration、trace、bad case、idempotency 和重复请求幂等均验证通过。
- 过程文件：`.codex/plans/main/p1-persistence/process.md`
- 恢复提示：读取 process.md，下一步从 P1-4 Admin Portal 最小配置治理 API 开始。

## P1-4 Admin Portal 最小配置治理 API
- 状态：已发布配置读取已完成
- 摘要：已实现配置版本草稿、查询、校验、发布、回滚、导入导出与审计端口/API；默认 memory 模式冒烟通过，local-jdbc 下已验证 config_version 与 audit_log 真实落库；已补配置对象最小 Upsert/List API、显式 `actionSchema.intentCode` 动作归属读取，并完成识别链路读取 PostgreSQL 最新 PUBLISHED 配置的 JDBC 冒烟。
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
- 摘要：接入 FastAPI 风格模型服务最小 adapter，默认关闭/no-op；模型候选位于规则之后、LLM 之前；已补 scene 级 endpoint/timeout 动态路由；`mvn test` 已通过。
- 过程文件：`.codex/plans/main/p2-model-service-adapter/process.md`
- 恢复提示：P2-4 已完成并推送，可从 P2-5 或后续 P2.x 继续。

## P2-5 LLM 受控兜底
- 状态：已提交并推送
- 摘要：补全 LLM 受控兜底最小闭环：全局治理配置、scene 级 `llm_policy` 读取、预算/超时门禁、有限重试和 fallback 失败关闭；并补齐 HTTP timeout 绑定、模型服务失败关闭、fallback 指标口径、LLM 预算消费最小计数与持久化审计、外呼前日预算原子预占、同步失败释放、默认关闭的 stale pending 后台补偿、补偿指标、管理端 confirmed/reserved/pending 预算查询、模型 adapter 本地 HTTP 冒烟、模型服务健康检查、本地真实联调、Spring AI Alibaba `ChatClient` 预接入、DashScope 沙箱冒烟 profile/script、运维告警样例和本地观测栈校验脚本；默认关闭且预算为 0，不影响规则和模型主链路；已随 `main` 推送到 GitHub。
- 过程文件：`.codex/plans/main/p2-llm-governance/process.md`
- 恢复提示：读取 process.md，继续 P2.x 真实 DashScope 冒烟、告警/真实多实例压测或模型服务部署化联调；后续新提交完成后，推送 GitHub 仍需用户单独指令。

## P2-6 密钥治理与外部联调准入
- 状态：进行中
- 摘要：P2 下一步规划已完成，并已补 Secret 解析端口与默认 env/system property 实现；模型服务 adapter 已迁移到统一 `SecretRefResolver`，LLM/DashScope 侧已预留同一解析入口；外部联调前预检脚本已补齐；文件挂载 Secret resolver 与 managed-config resolver 已预留；本地带鉴权模型服务 smoke 已完成，覆盖 PostgreSQL 已发布配置读取 `authTokenRef` 后注入 Bearer token；外部联调冒烟记录模板已新增，用于真实远端模型服务和 DashScope/LLM 留存准入证据。后续继续补 Vault SDK/动态刷新型 Nacos adapter、真实远端模型服务 smoke 或 DashScope 沙箱 smoke。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/plans/intent-hub-p2-next-step-plan.md` 和 process.md，从 P2-6 的 Vault SDK/Nacos 加密配置后端、真实远端模型服务 smoke 或 DashScope 沙箱 smoke 继续。

## P2-7 多实例一致性与压测
- 状态：本地四段闭环完成
- 摘要：已新增本地双实例一致性 smoke，启动同一个 PostgreSQL 16 与两个 Intent Hub `local-jdbc` 实例，由实例 A 发布配置后，实例 A/B 对同一异步请求返回同一个非空幂等键；数据库验证 `idempotency_record` 仅 1 条共享记录，`recognition_trace` 有 2 条请求记录。已新增 LLM 日预算多实例 smoke，两个实例共享 PostgreSQL 与本地 mock LLM，`dailyBudget=2` 下 6 个并发请求只有 2 个成功命中 LLM，剩余进入 `LLM_FALLBACK:REJECTED`，管理端预算 reserved 不超额且派生 pending 为 0。已新增 LLM stale pending 后台补偿 smoke，两个实例同时开启补偿 scheduler 后只发生 1 次补偿，预算行校正到 provider confirmed 用量。已新增基础双实例并发压测，40 请求输出 32 成功、8 拒识、0 fallback、平均 246.48ms、P95 319ms、P99 471ms，DB trace/bad case 数量一致。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-multi-instance-consistency-trace.md`，下一步可进入 P2-8 观测告警真实试点，或扩展 P2-7 到模型服务异常/缺 token fallback 组合压测。

## P2-8 观测告警本地试点
- 状态：本地可重复试点完成
- 摘要：已新增 `scripts/smoke-observability-pilot-local.ps1`，启动单实例 Intent Hub memory 模式，生成 10 条本地识别请求，形成 6 条 `SUCCESS` 与 4 条 `REJECTED`/bad case；已验证 `/api/v1/admin/metrics` 中 `totalRequests=10`、`totalBadCases=4`，Prometheus 文本包含 `intent_hub_requests_total` 与 `intent_hub_bad_cases_total`，`/api/v1/admin/metrics/alerts` 返回 `WARN` 且包含 `BAD_CASE_RATE_HIGH`；已生成 `ops/pilot-execution-record-local.md` 并新增 `docs/codex/v1/trace/intent-hub-p2-observability-pilot-trace.md`。本阶段仍不是 dev/staging/production 的真实 Prometheus、Alertmanager、Grafana 接入。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-observability-pilot-trace.md` 与 `ops/pilot-execution-record-local.md`；下一步可进入真实 dev/staging 观测告警接入，或转入 P2-9 配置发布治理增强。

## P2-9 配置发布治理增强
- 状态：最小治理闭环完成
- 摘要：已新增配置版本 diff API 与发布前 dry-run 报告，支持按业务标识对比 intent、slot、synonym、strategy、route、downstream action，返回新增/修改/删除计数与对象快照；dry-run 复用发布前校验，可携带 baseVersion 输出差异，并返回 GitOps 文件结构建议。Admin API 已暴露 `/diff` 与 `/dry-run` 契约，相关应用层与接口层测试通过。审批状态流转和真实 GitOps PR 同步仍为后续预留。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-governance-trace.md`；下一步可进入 P2-10 Admin Portal 配置评审界面、配置审批状态机/GitOps 导出，或回到真实 dev/staging 观测告警接入。

## P2-10 配置审批与 GitOps 导出
- 状态：最小闭环完成
- 摘要：已补齐 `DRAFT -> REVIEWING -> APPROVED -> PUBLISHED` 的最小状态流转，新增提交评审、批准和 GitOps 审查包导出能力；`REVIEWING` 版本不能直接发布，必须先批准，历史 DRAFT 直接发布保持兼容。GitOps 导出按建议路径返回配置对象内容，并附带 `dry-run.json`。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-approval-gitops-trace.md`；下一步可补 Admin Portal 评审页面、驳回/撤回流转、审批快照哈希，或进入真实 dev/staging 观测告警接入。

## P2-11 Admin 配置评审工作台聚合契约
- 状态：最小聚合契约完成
- 摘要：当前仓库没有独立前端工程或模板页面，本阶段先补页面可消费的数据面：新增 `ConfigReviewWorkspace` 与 `ConfigReviewWorkspaceAppService`，通过 `GET /api/v1/admin/config/versions/{version}/review-workspace` 聚合版本状态、校验、dry-run/diff、审计记录、可用动作和阻断原因。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-review-workspace-trace.md`；下一步可建立 Admin Portal 前端页面、补权限模型，或继续配置驳回/撤回与审批快照哈希。

## P2-12 配置评审驳回与撤回
- 状态：最小回退闭环完成
- 摘要：已新增 `rejectReview` 与 `cancelReview`，支持 `REVIEWING -> DRAFT` 驳回和 `REVIEWING/APPROVED -> DRAFT` 撤回，写入审计 reason 与 previousStatus；Admin API 暴露 `/reject-review` 与 `/cancel-review`，工作台动作新增 `REJECT_REVIEW`、`CANCEL_REVIEW`、`CANCEL_APPROVAL`。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-review-return-trace.md`；下一步可补审批快照哈希、权限模型、结构化 review comment，或建立 Admin Portal 前端页面。

## P2-13 审批快照哈希
- 状态：最小漂移防护完成
- 摘要：已在 `approve` 时计算配置包 SHA-256 快照哈希并写入 `CONFIG_APPROVED` 审计 detail；`APPROVED` 版本发布前会重新计算当前配置包哈希并比对最近批准快照，不一致则阻断发布。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-trace.md`；下一步可将 hash 固化到 `config_version` 字段、在 review-workspace 显式返回 hash，或补审批权限模型。

## P2-14 审批快照哈希强字段化
- 状态：强字段闭环完成
- 摘要：已新增 `config_version.approved_snapshot_hash` Flyway 迁移，并将批准快照哈希从审计 detail 升级为版本强字段；版本详情与 review-workspace 显式返回 `approvedSnapshotHash/currentSnapshotHash`；`APPROVED` 发布优先使用强字段校验，旧数据兼容审计 detail 回退。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-field-trace.md`；下一步可补 publish `expectedSnapshotHash` 条件发布、`approved_by/approved_at` 强字段、审批权限模型或结构化 review history。

## P2-15 发布 expectedSnapshotHash 条件校验
- 状态：条件发布闭环完成
- 摘要：已为发布接口补 `expectedSnapshotHash` 可选字段，调用方可携带工作台读取到的 `currentSnapshotHash` 做二次确认；服务端发布前比对当前配置包 hash，不一致则阻断发布。旧 publish 调用保持兼容，APPROVED 发布仍保留批准快照漂移校验。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-publish-expected-snapshot-hash-trace.md`；下一步可补 `approved_by/approved_at` 强字段、审批权限模型、结构化 review history 或 Admin Portal 发布按钮强制携带 hash。

## P2-16 审批元数据强字段化
- 状态：审批元数据闭环完成
- 摘要：已新增 `config_version.approved_by` 与 `approved_at` Flyway 迁移，并在 approve 时与 `approved_snapshot_hash` 一起写入版本强字段；版本详情与 review-workspace 显式返回审批人和审批时间。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-approval-metadata-field-trace.md`；下一步可补审批权限模型、结构化 review history、撤回时审批元数据清理语义或 Admin Portal 审批信息展示。

## P2-17 配置评审权限模型
- 状态：最小权限闭环完成
- 摘要：已为 Admin 配置评审动作补最小角色门禁：approve/reject/cancel 要求 `CONFIG_APPROVER`，publish 要求 `CONFIG_PUBLISHER`；HTTP 请求体支持 `roles`，应用层保留内部兼容调用。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-review-permission-trace.md` 与 `docs/codex/v1/trace/intent-hub-p2-review-workspace-role-filter-trace.md`；下一步可补真实登录态/IAM 接入、统一 403 响应或 tenant/scene 级权限。

## P2-18 评审工作台按角色过滤动作
- 状态：工作台权限可见性闭环完成
- 摘要：已为 `review-workspace` 增加 roles 查询参数与应用层过滤重载，Admin 工作台缺省角色时不再展示审批/发布等受控动作；`CONFIG_APPROVER` 控制审批/撤回动作，`CONFIG_PUBLISHER` 控制发布动作，并通过 `blockedReasons` 返回权限阻断原因。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-review-workspace-role-filter-trace.md`；下一步可补真实登录态/IAM、统一 403 响应、tenant/scene 级权限、`SUBMIT_REVIEW/ROLLBACK_TARGET` 更细授权或结构化 review history。

## P2-19 统一 403 响应
- 状态：权限错误响应闭环完成
- 摘要：已新增接口层统一错误响应 `ApiErrorResponse` 与 `GlobalExceptionHandler`，将配置评审权限失败的 `SecurityException` 映射为 HTTP 403 JSON，响应包含 `code=FORBIDDEN`、`message` 和 `status=403`；接口层测试覆盖错误角色调用 approve 时返回结构化 403。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-forbidden-error-response-trace.md`；下一步可补真实登录态/IAM 角色来源、tenant/scene 级权限、更多领域异常统一错误响应或结构化 review history。

## P2-20 Admin 请求上下文角色来源
- 状态：请求上下文角色来源闭环完成
- 摘要：已新增 `AdminRequestContext`，Admin 配置评审动作和 `review-workspace` 优先读取 `X-IntentHub-Actor` / `X-IntentHub-Roles`，再回退请求体/query roles；为后续网关/IAM 注入身份上下文预留稳定适配点。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-request-context-trace.md`；下一步可补 tenant/scene 级权限、Spring Security/JWT Filter、更多领域异常统一错误响应或结构化 review history。

## P2-21 tenant/scene 级配置权限
- 状态：范围角色最小闭环完成
- 摘要：已新增 `ConfigRoleMatcher`，配置评审审批/发布动作支持 `ROLE:tenant:scene`、`ROLE:tenant:*`、`ROLE:*:scene` 与全局旧角色兼容；工作台动作过滤与动作接口共用同一 scoped role 语义。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-scoped-config-role-trace.md`；下一步可补 Spring Security/JWT Filter、配置对象编辑权限、只读权限分层或结构化 review history。

## P2-22 配置对象编辑权限
- 状态：对象编辑权限闭环完成
- 摘要：已将配置对象 upsert、bulk upsert 和 delete 纳入 `CONFIG_EDITOR` scoped role 校验，复用 `ConfigRoleMatcher` 支持 `ROLE:tenant:scene` 与 `*` 通配；Admin 对象编辑入口支持请求体 roles 与 `X-IntentHub-Roles`，错误角色返回结构化 403。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-object-edit-permission-trace.md`；下一步可补 Spring Security/JWT Filter、只读权限分层、权限失败审计或对象类型级权限。

## P2-23 配置只读权限分层
- 状态：只读权限闭环完成
- 摘要：已新增 `CONFIG_VIEWER` 只读角色，并让 `CONFIG_EDITOR/CONFIG_APPROVER/CONFIG_PUBLISHER` 继承读权限；配置详情、校验、diff、dry-run、导出、GitOps 审查包、审计查询、配置对象列表和评审工作台均纳入 scoped viewer role 校验。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-config-viewer-permission-trace.md`；下一步可补 Spring Security/JWT Filter、权限失败安全审计、对象类型级权限或结构化 review history。

## P2-24 权限失败安全审计
- 状态：拒绝审计闭环完成
- 摘要：已在 `ConfigPermission` 中统一记录 `CONFIG_PERMISSION_DENIED` 审计事件，覆盖只读、编辑、审批、发布和工作台读取权限失败；HTTP 403 响应契约保持不变。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-permission-denied-audit-trace.md`；下一步可补 Spring Security/JWT Filter、权限拒绝指标告警、对象类型级权限或结构化 review history。

## P2-25 权限拒绝指标告警
- 状态：指标告警闭环完成
- 摘要：已将 `CONFIG_PERMISSION_DENIED` 审计事件接入指标体系，新增 `totalPermissionDenied`、Prometheus `intent_hub_permission_denied_total` 和 `CONFIG_PERMISSION_DENIED` WARN 告警；memory/JDBC 审计实现均会同步递增指标。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-permission-denied-metrics-alert-trace.md`；下一步可补 Spring Security/JWT Filter、真实 Prometheus/Alertmanager 规则、对象类型级权限或结构化 review history。

## P2-26 Admin JWT Filter
- 状态：最小 JWT 认证闭环完成
- 摘要：已新增默认关闭的 Admin Bearer JWT Filter，不引入 Spring Security 依赖；开启后保护 `/api/v1/admin/config/**`，使用 JDK HMAC-SHA256 校验 HS256 token，支持 `secret/secretRef`、`exp/iss/aud` 和 roles claim，并把可信 actor/roles 写入 Admin 请求上下文。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwt-filter-trace.md`；下一步可补认证失败安全审计、真实 Prometheus/Alertmanager 规则、对象类型级权限、结构化 review history 或企业 IAM/OIDC/JWKS 接入。

## P2-27 Admin JWT 认证失败审计与指标
- 状态：认证失败审计指标闭环完成
- 摘要：已将 Admin JWT 认证失败接入审计、metrics、Prometheus 文本和告警快照；失败事件记录为 `ADMIN_JWT_AUTH_FAILED`，只保存 method/path/reason，不记录 Authorization header 或 token 原文。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwt-auth-failure-audit-trace.md`；下一步可补真实 Prometheus/Alertmanager 规则、对象类型级权限、结构化 review history、完整 IAM/OIDC/JWKS 接入或真实外部联调 smoke。

## P2-28 安全指标 Prometheus/Alertmanager 规则
- 状态：运维规则样例闭环完成
- 摘要：已为 `intent_hub_permission_denied_total` 与 `intent_hub_admin_jwt_auth_failures_total` 补真实 Prometheus rule 样例，采用 5 分钟增量窗口，标记 `category=security`，并在 Alertmanager 样例中新增安全 receiver；Runbook、Prometheus/Alertmanager README、运维总入口和本地观测配置校验脚本已同步。未修改 Java 运行时代码。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-security-alert-rules-trace.md`；下一步可进入对象类型级权限、结构化 review history、完整 IAM/OIDC/JWKS 接入，或执行真实 dev/staging Prometheus/Alertmanager 试点。

## P2-29 配置对象类型级编辑权限
- 状态：对象类型编辑权限闭环完成
- 摘要：在兼容 `CONFIG_EDITOR[:tenant:scene]` 总编辑权限的基础上，新增 `CONFIG_INTENT_EDITOR`、`CONFIG_SLOT_EDITOR`、`CONFIG_SYNONYM_EDITOR`、`CONFIG_STRATEGY_EDITOR`、`CONFIG_ROUTE_EDITOR`、`CONFIG_ACTION_EDITOR` 对象类型级编辑角色；配置对象 upsert/bulk/delete 可使用对应类型角色，错误类型角色会返回结构化 403 并写入拒绝审计，审计 detail 包含 `objectType` 与 `alternativeRole`。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-object-type-permission-trace.md`；下一步可继续结构化 review history、完整 IAM/OIDC/JWKS 接入、真实 dev/staging Prometheus/Alertmanager 试点，或回到 P2-6 外部联调 smoke。

## P2-30 结构化 review history
- 状态：评审历史结构化闭环完成
- 摘要：已在 Admin 配置评审工作台中新增 `reviewHistory`，从版本审计记录派生 `REVIEW_SUBMITTED`、`APPROVED`、`REJECTED`、`CANCELLED`、`PUBLISHED` 等结构化阶段，并返回 actor、status、reason、snapshotHash、requiredRole、alternativeRole、objectType 和 occurredAt；原始 `audits` 保留兼容。相关应用层、基础设施层与接口层测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-review-history-trace.md`；下一步可继续完整 IAM/OIDC/JWKS 接入、真实 dev/staging Prometheus/Alertmanager 试点、真实外部联调 smoke，或补 Admin Portal 前端时间线。

## P2-31 Admin JWT RS256/JWKS 入口
- 状态：最小 RS256/JWKS 入口完成
- 摘要：已在既有默认关闭的 Admin JWT Filter 基础上，扩展 `AdminJwtVerifier` 支持 RS256 + JWKS 公钥验签；新增 `jwksJson` 与 `jwksUrl` 配置，保留 HS256 `secret/secretRef` 兼容，支持 `kid` 匹配，并在多 RSA key 但 token 缺少 `kid` 时拒绝。JWT verifier 定向测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-rs256-trace.md`；下一步可补 JWKS URL 真实 smoke、TTL/刷新/轮换策略、完整 IAM/OIDC discovery，或推进真实外部联调。

## P2-32 Admin JWKS URL 本地 smoke
- 状态：JWKS URL 本地 smoke 完成
- 摘要：已在 `AdminJwtVerifierTest` 中使用 JDK `HttpServer` 启动本地 JWKS endpoint，验证 `jwksUrl` 可以通过真实 HTTP 拉取 JWKS 并完成 RS256 token 验签；同一 verifier 连续验签两次只请求一次 JWKS endpoint，证明实例内缓存生效。JWT verifier clean 定向测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-url-smoke-trace.md`；下一步可补真实 IAM/OIDC sandbox smoke、JWKS TTL/刷新/轮换策略，或推进外部联调配置模板。

## P2-33 Admin JWKS 缓存 TTL
- 状态：JWKS 缓存 TTL 最小闭环完成
- 摘要：已为 `jwksUrl` 拉取结果新增 `jwksCacheTtlSeconds` 配置，默认 300 秒；TTL 内复用缓存，TTL 到期后重新请求 JWKS endpoint。测试覆盖默认 TTL 缓存命中，以及 `jwksCacheTtlSeconds=0` 时连续验签触发重新拉取。JWT verifier clean 定向测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-cache-ttl-trace.md`；下一步可补刷新失败回退旧 JWKS、JWKS timeout/指标、真实 IAM/OIDC sandbox smoke，或外部联调配置模板。

## P2-34 Admin JWKS 刷新失败旧缓存宽限
- 状态：JWKS stale grace 最小闭环完成
- 摘要：已为 `jwksUrl` 缓存新增 `jwksStaleGraceSeconds` 配置，默认 300 秒；TTL 到期后仍会优先刷新 JWKS，若刷新失败且仍在宽限期内，则继续使用上一份已成功拉取并解析过的 JWKS；宽限期为 0 或已过期时刷新失败会拒绝验签。JWT verifier 与 Admin Controller 组合回归通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-stale-grace-trace.md`；下一步可补 JWKS timeout/指标、真实 IAM/OIDC sandbox smoke、OIDC discovery，或外部联调配置模板。

## P2-35 Admin JWKS timeout 与指标
- 状态：JWKS timeout 与指标最小闭环完成
- 摘要：已为 `jwksUrl` 拉取新增 `jwksFetchTimeoutMs` 配置，默认 2000ms；JWKS fetch、fetch failure、cache hit、stale hit 已接入 `IntentMetricsPort`、`MetricsSnapshot`、内存指标仓储和 Prometheus 文本。JWT verifier、Admin metrics 和 Admin config controller 组合回归通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-timeout-metrics-trace.md`；下一步可补 JWKS Prometheus/Alertmanager 规则、真实 IAM/OIDC sandbox smoke、OIDC discovery，或外部联调配置模板。

## P2-36 Admin JWKS Prometheus/Alertmanager 告警规则
- 状态：JWKS 告警规则闭环完成
- 摘要：已为 `intent_hub_admin_jwks_fetch_failures_total` 与 `intent_hub_admin_jwks_stale_hits_total` 补 Prometheus 告警规则、Runbook、Prometheus/Alertmanager README、运维总入口和本地观测配置校验脚本；两条规则均使用 5 分钟增量窗口并标记 `category=security`。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-jwks-alert-rules-trace.md`；下一步可进入真实 IAM/OIDC sandbox smoke、OIDC discovery、外部联调配置模板，或真实 dev/staging Prometheus/Alertmanager 试点。

## P2-37 Admin OIDC Discovery
- 状态：OIDC discovery 最小闭环完成
- 摘要：已为 Admin JWT RS256/JWKS 验签新增 `oidcDiscoveryUrl` 配置，支持从 `/.well-known/openid-configuration` 解析 `jwks_uri`，并复用现有 JWKS URL TTL、stale grace、timeout、指标与验签链路。定向 verifier 测试通过。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-oidc-discovery-trace.md`；下一步可进入真实 IAM/OIDC sandbox smoke、discovery metadata issuer 校验、discovery fetch 指标告警，或真实 dev/staging Prometheus/Alertmanager 试点。

## P2-38 Admin OIDC Discovery Issuer 校验
- 状态：discovery issuer 校验最小闭环完成
- 摘要：已为 `oidcDiscoveryUrl` 增加 discovery metadata issuer 一致性校验，默认启用；显式配置 `issuer` 时，discovery 文档中的 `issuer` 必须一致，否则拒绝继续读取 `jwks_uri`。同时保留兼容开关 `oidcDiscoveryIssuerValidationEnabled=false`，且不影响 JWT payload `iss` 校验。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-oidc-discovery-issuer-validation-trace.md`；下一步可进入真实 IAM/OIDC sandbox smoke、discovery fetch 指标告警、外部联调配置模板，或真实 dev/staging Prometheus/Alertmanager 试点。

## P2-39 Admin OIDC Discovery 指标
- 状态：discovery fetch 指标最小闭环完成
- 摘要：已为 `oidcDiscoveryUrl` 拉取 discovery metadata 增加独立 fetch/failure 指标，Prometheus 文本导出 `intent_hub_admin_oidc_discovery_fetches_total` 与 `intent_hub_admin_oidc_discovery_fetch_failures_total`；HTTP 非 2xx、JSON 解析失败、缺 `jwks_uri` 和 issuer 不一致均进入 discovery failure 计数。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-oidc-discovery-metrics-trace.md`；下一步可补 discovery Prometheus/Alertmanager 告警规则与 Runbook，或进入真实 IAM/OIDC sandbox smoke。

## P2-40 Admin OIDC Discovery 告警规则
- 状态：discovery failure 告警规则最小闭环完成
- 摘要：已为 `intent_hub_admin_oidc_discovery_fetch_failures_total` 补 Prometheus 告警 `IntentHubAdminOidcDiscoveryFetchFailed`、Runbook 处理章节、Prometheus/ops README 和本地观测配置校验脚本；规则使用 5 分钟增量窗口并标记 `category=security`。
- 过程文件：`.codex/plans/main/p2-next-step-planning/process.md`
- 恢复提示：读取 `docs/codex/v1/trace/intent-hub-p2-admin-oidc-discovery-alert-rules-trace.md`；下一步可进入真实 IAM/OIDC sandbox smoke、真实 dev/staging Prometheus/Alertmanager 试点，或外部联调配置模板。

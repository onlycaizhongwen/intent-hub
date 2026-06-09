# P2 下一步规划

## 恢复胶囊

- 任务需求：规划 P2-5 之后的下一步，形成可执行顺序并固化到正式计划文档。
- 关键决策：优先进入 P2-6 密钥治理与外部联调准入，再推进 P2-7 多实例一致性与压测、P2-8 观测告警真实试点、P2-9 配置发布治理增强。
- 当前阶段：P2-6 已启动，Secret 解析端口与默认 env/system property resolver 已完成，模型服务 adapter 已迁移到统一 resolver，LLM adapter 已预留同一 resolver，外部联调前预检脚本已补齐，文件挂载 Secret resolver 已预留，managed-config Secret resolver 已补齐，模型服务客户端缓存已支持 Secret 轮换感知，本地带鉴权模型服务 smoke 与文件挂载轮换 smoke 已完成，外部联调冒烟记录模板已固化。
- 已完成产物：`docs/codex/v1/plans/intent-hub-p2-next-step-plan.md`、`SecretRefResolver`、`EnvironmentSecretRefResolver`、`CompositeSecretRefResolver`、`FileSecretRefResolver`、`ManagedConfigSecretRefResolver`、`ManagedConfigSecretProperties`、模型服务/LLM resolver 接入、模型服务 token fingerprint 客户端缓存、`scripts/preflight-external-integration.ps1`（含 env 与文件挂载 Secret 预检）、`scripts/smoke-model-service-e2e.ps1 -WithAuth`、`scripts/smoke-secret-rotation.ps1`、`ops/external-integration-smoke-record-template.md`、FastAPI 可选鉴权与文件 token 示例、测试、status/trace/production checklist/TASKS 回写。
- 剩余工作：P2-6 继续实现 Vault SDK/动态刷新型 Nacos adapter、真实远端模型服务 smoke 和 DashScope 沙箱 smoke。
- 重要发现：模型服务和 LLM 外部调用能力已具备，当前最大生产化缺口是 Secret 安全解析、真实外部联调证据、多实例一致性和真实观测试点。

## 步骤列表

- [v] 读取当前状态、任务记录和既有 P2 trace。
- [v] 形成 P2 后半段执行顺序。
- [v] 新增 P2 下一步执行计划文档。
- [v] 回写 status 与 TASKS。
- [v] 启动 P2-6，补 Secret 解析端口和默认 env/system property resolver。
- [v] 模型服务 adapter 迁移到统一 resolver。
- [v] LLM adapter 预留同一 resolver。
- [v] 同步 P2-4/P2-5 trace、生产化清单、status 与 TASKS。
- [v] 新增外部联调前预检脚本，覆盖 Intent Hub health、模型服务 health、模型服务 Secret 引用和 DashScope Secret 引用存在性检查，且不打印密钥值。
- [v] 新增文件挂载 Secret resolver 与组合 resolver，支持 `intent-hub.secret.file-root` 读取外部挂载文件，并拒绝路径穿越。
- [v] 完成本地带鉴权模型服务 smoke，覆盖无 token 直连拒绝、preflight 引用检查、PostgreSQL 已发布 `modelPolicy.authTokenRef` 读取、Bearer token 注入和 `ModelRecognitionPolicy` 识别路径。
- [v] 新增外部联调冒烟记录模板，覆盖 Secret 引用、preflight、模型服务鉴权、DashScope/LLM、trace、指标、预算和安全复核证据。
- [v] 新增 managed-config Secret resolver，支持从外部托管配置注入的引用映射读取 Secret。
- [v] 补齐模型服务 scene 客户端 Secret 轮换感知，避免缓存 key 保存明文 token，并在 token 变化时重建客户端。
- [v] 新增并执行本地文件挂载 Secret 轮换 smoke。
- [~] 继续 P2-6 外部联调准入。
  - 当前产物：统一 Secret resolver 最小闭环、文件挂载 Secret resolver、managed-config Secret resolver、模型服务 token fingerprint 客户端缓存、外部联调 preflight 脚本、本地带鉴权模型服务 smoke 证据、本地文件挂载轮换 smoke 证据、外部联调冒烟记录模板。
  - 下一步：新增 Vault SDK/动态刷新型 Nacos adapter，或在凭证可用时执行真实远端模型服务 smoke / DashScope 沙箱 smoke。
  - 涉及文件：`scripts/`、`intent-hub-infrastructure/src/main/java/com/intenthub/infrastructure/security/`、`ops/production-readiness-checklist.md`、`ops/external-integration-smoke-record-template.md`。

## 研究发现

- P2-6 是最自然的下一步，因为它承接 `authTokenRef`、DashScope smoke、模型服务鉴权和生产化安全边界。
- P2-7 依赖 P2-6 的外部依赖准入稳定性，否则压测会混入密钥和外部服务不稳定因素。
- P2-8 可以在 P2-6/P2-7 后执行，用真实指标和告警证据校准当前样例。
- P2-9 是平台化增强，不是当前外部联调和试点准入的阻塞项。
- 2026-06-09：P2-6 第一段完成。统一 `SecretRefResolver` 让模型服务和 LLM/DashScope 后续共享 Secret 引用解析边界；默认实现仍只读系统属性和环境变量，不代表 Vault/K8s Secret 已生产落地。
- 2026-06-09：P2-6 第二段完成。新增 `scripts/preflight-external-integration.ps1`，用于真实外部 smoke 前检查 Intent Hub、模型服务健康和 Secret 引用存在性；脚本不发识别请求、不调用 LLM、不打印密钥值。预检通过只代表准入条件满足，不代表真实外部联调已完成。
- 2026-06-09：P2-6 第三段完成。新增 `CompositeSecretRefResolver` 与 `FileSecretRefResolver`，并通过 `intent-hub.secret.file-root` 接入 Spring Bean；文件 resolver 支持 K8s Secret/Vault Agent 文件挂载形态，并拒绝路径穿越。该实现不是 Vault SDK/Nacos 加密配置的生产集成，轮换、权限和审计仍需后续补齐。
- 2026-06-09：P2-6 第四段完成。FastAPI 模型服务示例新增可选 `MODEL_SERVICE_AUTH_TOKEN`，`scripts/smoke-model-service-e2e.ps1 -WithAuth` 已通过：启动临时 PostgreSQL 16、模型服务容器和 Intent Hub `local-jdbc`；直连无 token 被 401 拒绝；preflight 只输出引用状态；Admin 发布带 `modelPolicy.authTokenRef` 的 scene 后，识别链路通过 Bearer token 调用模型服务并返回 `ORDER_CANCEL/ASYNC_ACCEPTED`，路径包含 `ModelRecognitionPolicy`。该证据仍属于本地外部依赖 smoke，不代表真实远端模型服务或生产 Secret 系统已完成。
- 2026-06-09：P2-6 第五段完成。新增 `ops/external-integration-smoke-record-template.md`，用于真实模型服务和 DashScope/LLM 联调时统一记录 Secret 引用、preflight、鉴权、trace、预算、指标和安全复核证据；并同步 `ops/README.md`、`ops/production-readiness-checklist.md` 与 P2 计划。该模板明确 preflight 不等于真实链路通过，本地带鉴权 smoke 也不等于真实远端服务或生产级 Secret 系统落地。
- 2026-06-09：P2-6 第六段完成。`scripts/preflight-external-integration.ps1` 新增 `-SecretFileRoot`，可在真实 smoke 前检查文件挂载 Secret 引用，并拒绝路径穿越；输出只展示 `file` 等来源类型，不输出文件内容或密钥值。该能力与运行时 `intent-hub.secret.file-root` 保持一致，但仍不等同于 Vault SDK/Nacos 加密配置生产集成。
- 2026-06-09：P2-6 第七段完成。新增 `ManagedConfigSecretProperties` 与 `ManagedConfigSecretRefResolver`，通过 `intent-hub.secret.managed-config.enabled` 和 `intent-hub.secret.managed-config.refs.*` 支持外部托管配置注入 Secret 映射；解析顺序为 env/system property -> file-root -> managed-config。该能力适合 Nacos/Apollo/Spring Config 等平台完成解密后的运行时注入，不代表 Intent Hub 已实现供应商 SDK、权限、轮换或审计。
- 2026-06-09：P2-6 第八段完成。模型服务 scene 客户端缓存 key 改为 `endpoint + timeout + authTokenRef + token fingerprint`，不再保存明文 token；同一路由 token 指纹变化时会驱逐旧客户端并重建，避免 Secret 轮换后长期复用旧 Bearer 鉴权头。`ModelClientAdapterTest` 已覆盖轮换后第二次请求使用新 token 且缓存数量保持 1。
- 2026-06-09：P2-6 第九段完成。新增 `scripts/smoke-secret-rotation.ps1` 并已执行通过：脚本启动临时 PostgreSQL 16、文件挂载 Secret、FastAPI 模型服务容器和 Intent Hub `local-jdbc`；先用初始 token 完成直连和 Intent Hub 识别，再改写 Secret 文件，验证旧 token 直连被 401 拒绝、新 token 直连通过，且 Intent Hub 第二次识别仍通过 `ModelRecognitionPolicy`。脚本结束后已清理容器、进程和临时 Secret 文件。

## 错误记录

- 2026-06-09：首次带鉴权 smoke 在 Admin 写入 downstream action 时失败，原因是脚本使用了不被配置校验允许的 `ASYNC_COMMAND`，已改为 `MQ`。
- 2026-06-09：第二次带鉴权 smoke 识别结果未命中专用 scene，原因是 memory 模式下 Admin 配置仓储与识别读取仓储未打通，已将 `-WithAuth` smoke 切到 `local-jdbc` + 临时 PostgreSQL。

## 2026-06-09 P2-7 断点记录

- 当前阶段：P2-7 多实例一致性与压测本地四段闭环已完成。
- 已完成产物：`scripts/smoke-multi-instance-consistency.ps1`、`scripts/smoke-llm-budget-multi-instance.ps1`、`scripts/smoke-llm-budget-reconciliation-multi-instance.ps1`、`scripts/stress-multi-instance-basic.ps1`、`docs/codex/v1/trace/intent-hub-p2-multi-instance-consistency-trace.md`。
- 验证证据 1：已执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-multi-instance-consistency.ps1 -SkipPackage`，结果通过。
- 验证结论 1：同一个 PostgreSQL 16 下启动两个 Intent Hub `local-jdbc` 实例，实例 A 发布配置后，实例 A/B 对同一个异步请求均返回 `ORDER_CANCEL/ASYNC_ACCEPTED`，并返回同一个非空 `idempotencyKey`；数据库 `idempotency_record` 对该键只有 1 条记录，`recognition_trace` 有 2 条记录。
- 验证证据 2：已执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-multi-instance.ps1 -SkipPackage`，结果通过。
- 验证结论 2：两个 Intent Hub `local-jdbc` 实例共享 PostgreSQL 与本地 mock LLM，`dailyBudget=2` 下 6 个交替并发请求只有 2 个成功命中 LLM，其余进入 `LLM_FALLBACK:REJECTED`；管理端预算查询 reserved 不超过日预算且派生 pending 为 0；数据库预算明细为 `__budget__/__daily__:2:2.0000` 与 `http-contract/mock-llm:4:4.0000`，`recognition_trace` 有 6 条记录。
- 验证证据 3：已执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-reconciliation-multi-instance.ps1 -SkipPackage`，结果通过。
- 验证结论 3：两个 Intent Hub `local-jdbc` 实例共享 PostgreSQL 且同时开启 LLM budget reconciliation scheduler；脚本插入 stale pending 预算数据后，后台补偿只生效 1 次，预算明细最终为 `__budget__/__daily__:1:1.0000` 与 `http-contract/mock-llm:1:1.0000`，跨实例补偿指标合计为 1。
- 验证证据 4：已执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stress-multi-instance-basic.ps1 -SkipPackage`，结果通过。
- 验证结论 4：两个 Intent Hub `local-jdbc` 实例共享 PostgreSQL，发布纯规则识别 scene 后并发发送 40 个请求，结果为 32 个 `SUCCESS`、8 个 `REJECTED`、0 fallback、0 transport error；平均耗时 246.48ms，P95 319ms，P99 471ms；数据库 `recognition_trace=40`、`bad_case=8`。
- 重要边界：本次覆盖规则识别 + 异步动作幂等 + trace 写入 + LLM 日预算多实例并发预占 + 同步失败释放 + stale pending 后台补偿 + 本地小规模并发压测，不覆盖真实模型服务、真实 LLM provider、Kubernetes 多副本或生产压测。
- 下一步：可进入 P2-8 观测告警真实试点，或扩展 P2-7 到模型服务异常/缺 token fallback 组合压测。

## 2026-06-09 P2-7 第二段错误记录

- 首次 LLM budget smoke 全部 fallback，识别路径显示 `LLM_ERROR:RestClientException`；已将本地 Python mock LLM 服务改为 HTTP/1.0 并显式 `Connection: close`，避免 RestClient 读响应受到 keep-alive 细节影响。
- 后续 smoke 出现 `consumedUnits=3` 大于 `dailyBudget=2` 的断言失败；复查后确认当前设计中 `reservedUnits` 是日预算门禁口径，provider/model `consumedUnits` 是真实外呼尝试审计口径，异常释放会减少 reserved 但保留尝试审计。已调整 smoke 断言：门禁检查 `reservedUnits <= dailyBudget` 与 `pendingUnits=0`，并保留 provider 明细输出用于排查。
- 首次 reconciliation smoke 读取接口 JSON 时未拿到 `pendingUnits` 字段；原因是当前 `LlmBudgetUsage.pendingUnits()` 是 Java 派生方法，接口 JSON 未序列化该值。脚本已按同一语义用 `max(reservedUnits - consumedUnits, 0)` 计算派生 pending。
- 首次 basic stress 统计成功数时误按 `ASYNC_ACCEPTED` 统计；由于脚本下游动作配置为 `idempotencyRequired=false`，成功 decision 应为 `SUCCESS`。已修正统计口径。

## 2026-06-09 P2-8 断点记录

- 当前阶段：P2-8 观测告警本地可重复试点已完成。
- 已完成产物：`scripts/smoke-observability-pilot-local.ps1`、`ops/pilot-execution-record-local.md`、`docs/codex/v1/trace/intent-hub-p2-observability-pilot-trace.md`。
- 验证证据 1：已执行 PowerShell Parser 检查，`scripts/smoke-observability-pilot-local.ps1` 语法通过。
- 验证证据 2：已执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-observability-pilot-local.ps1 -SkipPackage`，结果通过。
- 验证结论：脚本启动 Intent Hub 单实例 memory 模式，健康检查返回 `UP`；生成 10 条本地识别请求，其中 6 条成功、4 条拒识并形成 bad case；`/api/v1/admin/metrics` 返回 `totalRequests=10` 与 `totalBadCases=4`；`/api/v1/admin/metrics/prometheus` 包含 `intent_hub_requests_total` 与 `intent_hub_bad_cases_total`；`/api/v1/admin/metrics/alerts` 返回 `WARN` 且包含 `BAD_CASE_RATE_HIGH`；执行记录已写入 `ops/pilot-execution-record-local.md`。
- 重要边界：本次只证明本地 endpoint 与内置告警快照可用，不代表真实 Prometheus target、Alertmanager route、Grafana dashboard、receiver 通知链路或 dev/staging/production 观测栈接入完成。
- 下一步：可进入真实 dev/staging 观测告警接入试点，按 `ops/pilot-execution-record-template.md` 记录 Prometheus target UP、rules load、Alertmanager receiver、Grafana dashboard、告警演练和恢复证据；如果暂时没有真实观测栈环境，可转入 P2-9 配置发布治理增强。

## 2026-06-09 P2-8 错误记录

- 首次脚本语法检查失败，原因是 PowerShell here-string 结束标记未独占行；已将记录生成模板改为稳定写法。
- 首次本地 smoke 中 10 条请求全部进入 bad case，原因不是旧 jar，而是 PowerShell 默认发送 JSON 字符串时会破坏中文规则关键词；已改为 UTF-8 bytes 发送 JSON body，并用 Unicode 码点构造“查一下订单”样本文本。

## 2026-06-09 P2-9 断点记录

- 当前阶段：P2-9 配置发布治理增强最小闭环已完成。
- 已完成产物：`ConfigDiffEntry`、`ConfigDiffResult`、`ConfigDryRunReport`、`ConfigVersionAppService.diff(...)`、`ConfigVersionAppService.dryRunPublish(...)`、`GET /api/v1/admin/config/versions/{version}/diff`、`POST /api/v1/admin/config/versions/{version}/dry-run`、`docs/codex/v1/trace/intent-hub-p2-config-governance-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层测试覆盖按业务标识识别 `ADDED/MODIFIED/REMOVED`，dry-run 复用发布前跨对象引用校验并返回 GitOps 文件建议；接口层测试覆盖 Admin diff 与 dry-run 契约。
- 关键设计：diff 按对象稳定业务标识对齐，而不是按数组下标对齐；slot 使用 `intentCode.slotCode`，route 使用 `routeStage.routeTarget`，缺失业务标识时使用 `__index_N` 兼容异常数据。
- 重要边界：本阶段没有改变 `publish`/`rollback` 语义，没有新增审批状态表，也没有真正写 Git 仓库或创建 PR；`DRAFT -> REVIEWING -> APPROVED -> PUBLISHED` 仍为后续 Admin Portal/GitOps 阶段预留。
- 下一步：可进入 P2-10 Admin Portal 配置评审界面、配置审批状态机/GitOps 导出，或回到真实 dev/staging 观测告警接入试点。

## 2026-06-09 P2-10 断点记录

- 当前阶段：P2-10 配置审批与 GitOps 导出最小闭环已完成。
- 已完成产物：`ConfigVersionPort.updateStatus(...)`、`ConfigGitOpsExport`、`ConfigVersionAppService.submitReview(...)`、`ConfigVersionAppService.approve(...)`、`ConfigVersionAppService.exportGitOps(...)`、Admin `submit-review/approve/gitops` API、`docs/codex/v1/trace/intent-hub-p2-config-approval-gitops-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 27 个测试、基础设施层 59 个测试、接口层 21 个测试，合计 107 个测试；覆盖 `DRAFT -> REVIEWING -> APPROVED -> PUBLISHED`、`REVIEWING` 禁止直接发布、GitOps 导出包含 `dry-run.json`、Controller 契约暴露。
- 关键设计：为兼容既有 P1/P2 smoke，旧的 DRAFT 直接发布仍允许；新审批链路一旦进入 `REVIEWING`，必须先 approve 才能 publish。
- 重要边界：当前 GitOps 是 API 导出结构，不会真实写 Git、创建分支、提交 commit 或发 PR；当前审批不含驳回、撤回、审批意见、多人审批或快照哈希。
- 下一步：可补 Admin Portal 评审页面、驳回/撤回流转、审批快照哈希，或回到真实 dev/staging 观测告警接入试点。

## 2026-06-09 P2-11 断点记录

- 当前阶段：P2-11 Admin 配置评审工作台聚合契约已完成。
- 已完成产物：`ConfigReviewWorkspace`、`ConfigReviewWorkspaceAppService`、`GET /api/v1/admin/config/versions/{version}/review-workspace`、`docs/codex/v1/trace/intent-hub-p2-admin-review-workspace-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 27 个测试、基础设施层 59 个测试、接口层 22 个测试，合计 108 个测试；覆盖 DRAFT 工作台可用动作、REVIEWING 下禁止直接发布并提示阻断原因。
- 关键设计：仓库当前没有前端工程，因此先提供 Admin Portal 页面可消费的数据面；工作台聚合版本状态、校验、dry-run/diff、审计、可用动作和阻断原因。
- 重要边界：本阶段不是完整 UI；可用动作是后端建议，后续仍需叠加用户权限、菜单权限和真实审批策略。
- 下一步：可建立 Admin Portal 前端页面、补权限模型，或继续配置驳回/撤回与审批快照哈希。

## 2026-06-09 P2-12 断点记录

- 当前阶段：P2-12 配置评审驳回与撤回最小闭环已完成。
- 已完成产物：`ConfigVersionAppService.rejectReview(...)`、`ConfigVersionAppService.cancelReview(...)`、`ConfigVersionActionRequest.reason`、Admin `reject-review/cancel-review` API、工作台 `REJECT_REVIEW/CANCEL_REVIEW/CANCEL_APPROVAL` 动作、`docs/codex/v1/trace/intent-hub-p2-config-review-return-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 28 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 110 个测试；覆盖 `REVIEWING -> DRAFT` 驳回、`APPROVED -> DRAFT` 撤回、审计动作、Controller 契约和工作台动作。
- 关键设计：驳回和撤回均回到 `DRAFT`，从而复用既有“仅 DRAFT 可编辑”约束；reason 先落在审计 detail 中，不新增审批意见表。
- 重要边界：当前没有审批快照哈希、多人审批、权限模型或结构化 review history。
- 下一步：可补审批快照哈希、权限模型、结构化 review comment，或建立 Admin Portal 前端页面。

## 2026-06-09 P2-13 断点记录

- 当前阶段：P2-13 审批快照哈希最小闭环已完成。
- 已完成产物：`CONFIG_APPROVED.detail.snapshotHash`、`ConfigVersionAppService.publish(...)` 的 APPROVED 快照漂移校验、`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 29 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 111 个测试；覆盖 approve 后记录 snapshotHash、批准后底层配置漂移时发布被阻断。
- 关键设计：不新增 DB migration，先把批准快照哈希放入既有 audit log detail；发布 APPROVED 版本时读取最近一次 `CONFIG_APPROVED` 快照并比对当前配置包哈希。
- 重要边界：当前哈希不在 `config_version` 强字段中；canonical 逻辑为最小稳定字符串化，不是标准 JSON canonicalization。
- 下一步：可将 hash 固化到 `config_version` 字段、在 review-workspace 显式返回 hash，或补审批权限模型。

## 2026-06-09 P2-14 断点记录

- 当前阶段：P2-14 审批快照哈希强字段化已完成。
- 已完成产物：`config_version.approved_snapshot_hash`、Flyway `V4__p2_config_approval_snapshot_hash.sql`、`ConfigVersionInfo.approvedSnapshotHash/currentSnapshotHash`、`ConfigVersionPort.updateApprovedSnapshotHash(...)`、版本详情与 review-workspace hash 显式返回、`docs/codex/v1/trace/intent-hub-p2-approval-snapshot-hash-field-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 29 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 111 个测试；覆盖批准时写入强字段、版本详情和工作台返回当前/批准 hash、批准后漂移发布阻断，以及 JDBC schema 兼容。
- 关键设计：`approve` 时同时写审计 detail 与 `config_version.approved_snapshot_hash`；`publish` 对 `APPROVED` 版本优先读取强字段，强字段缺失时兼容旧审计 detail，避免已有批准数据无法发布。
- 重要边界：当前尚未新增 `approved_by/approved_at` 强字段；publish 尚未要求调用方传入 `expectedSnapshotHash`；canonical 逻辑仍不是标准 JSON canonicalization。
- 下一步：可补 publish `expectedSnapshotHash` 条件发布、审批权限模型、结构化 review comment/history，或建立 Admin Portal 前端页面。

## 2026-06-09 P2-15 断点记录

- 当前阶段：P2-15 发布 `expectedSnapshotHash` 条件校验已完成。
- 已完成产物：`ConfigVersionAppService.publish(..., expectedSnapshotHash)`、`ConfigVersionActionRequest.expectedSnapshotHash`、Admin publish 请求体透传、`docs/codex/v1/trace/intent-hub-p2-publish-expected-snapshot-hash-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 30 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 112 个测试；覆盖错误 expected hash 阻断、正确 current hash 发布成功、Controller 请求体字段生效。
- 关键设计：`expectedSnapshotHash` 作为调用方二次确认字段，非空才校验；旧调用保持兼容。`APPROVED` 发布仍保留批准快照未漂移校验，expected hash 不替代审批哈希。
- 重要边界：当前没有强制所有发布请求必传 expected hash；尚未补 OpenAPI/接口样例；`approved_by/approved_at`、权限模型和结构化 review history 仍待后续。
- 下一步：可补 Admin Portal 发布按钮强制携带 `currentSnapshotHash`、`approved_by/approved_at` 强字段、审批权限模型或结构化 review comment/history。

## 2026-06-09 P2-16 断点记录

- 当前阶段：P2-16 审批元数据强字段化已完成。
- 已完成产物：`config_version.approved_by`、`config_version.approved_at`、Flyway `V5__p2_config_approval_metadata.sql`、`ConfigVersionInfo.approvedBy/approvedAt`、memory/JDBC 审批元数据读写、`docs/codex/v1/trace/intent-hub-p2-approval-metadata-field-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 30 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 112 个测试；覆盖 approve 后版本详情和 review-workspace 返回审批人、审批时间与批准 hash。
- 关键设计：复用 `updateApprovedSnapshotHash(..., actor)` 在 approve 阶段写入 hash、审批人和审批时间，避免新增多个端口方法；旧 `ConfigVersionInfo` 构造器保持兼容。
- 重要边界：当前单人审批语义不变；撤回/驳回暂不清空审批元数据；审批权限模型和结构化 review history 仍待后续。
- 下一步：可补审批权限模型、结构化 review comment/history、撤回审批元数据清理语义或 Admin Portal 审批信息展示。

## 2026-06-09 P2-17 断点记录

- 当前阶段：P2-17 配置评审权限模型最小闭环已完成。
- 已完成产物：`ConfigVersionActionRequest.roles`、`ConfigVersionAppService` 角色门禁、Admin approve/reject/cancel/publish 角色透传、`docs/codex/v1/trace/intent-hub-p2-config-review-permission-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 31 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 113 个测试；覆盖审批角色、发布角色、错误角色阻断和 Controller 契约。
- 关键设计：`CONFIG_APPROVER` 控制 approve/reject/cancel，`CONFIG_PUBLISHER` 控制 publish；应用层 roles 为 `null` 时保持内部兼容，Admin API 空 roles 会被受控动作拦截。
- 重要边界：roles 仍来自请求体，尚未接入真实身份/IAM；权限失败尚未统一映射为 HTTP 403；review-workspace 尚未按角色过滤动作。
- 下一步：可补真实登录态/IAM 接入、review-workspace 按角色过滤动作、统一 403 响应或 tenant/scene 级权限。

## 2026-06-09 P2-18 断点记录

- 当前阶段：P2-18 评审工作台按角色过滤动作最小闭环已完成。
- 已完成产物：`ConfigReviewWorkspaceAppService.getWorkspace(..., roles)`、Admin `review-workspace` roles 查询参数、工作台可用动作角色过滤、`docs/codex/v1/trace/intent-hub-p2-review-workspace-role-filter-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 32 个测试、基础设施层 59 个测试、接口层 23 个测试，合计 114 个测试；覆盖旧应用层调用兼容、Admin 缺省 roles 隐藏受控动作、`CONFIG_APPROVER` 显示审批/撤回动作、`CONFIG_PUBLISHER` 显示发布动作以及 `blockedReasons` 权限提示。
- 关键设计：应用层 roles 为 `null` 时保留内部兼容不过滤；Admin API 缺省 roles 时按空角色处理，从数据面隐藏 `APPROVE/REJECT_REVIEW/CANCEL_REVIEW/CANCEL_APPROVAL/PUBLISH/PUBLISH_COMPAT` 等受控动作。
- 重要边界：roles 仍来自查询参数，尚未接入真实登录态/IAM；权限失败尚未统一映射 HTTP 403；tenant/scene 级权限与 `SUBMIT_REVIEW/ROLLBACK_TARGET` 更细授权仍待后续。
- 下一步：可补统一 403 响应、真实登录态/IAM 接入、tenant/scene 级权限，或进入结构化 review comment/history。

## 2026-06-09 P2-19 断点记录

- 当前阶段：P2-19 统一 403 响应最小闭环已完成。
- 已完成产物：`ApiErrorResponse`、`GlobalExceptionHandler`、`AdminConfigControllerTest.mapsPermissionFailureToForbiddenHttpResponse`、`docs/codex/v1/trace/intent-hub-p2-forbidden-error-response-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 32 个测试、基础设施层 59 个测试、接口层 24 个测试，合计 115 个测试；覆盖错误角色调用 approve 时返回 HTTP 403，并返回 `code=FORBIDDEN`、`status=403` 与明确权限失败 message。
- 关键设计：角色判定仍保持在应用层，接口层只通过 `@RestControllerAdvice` 把 `SecurityException` 映射为结构化 403 响应，避免权限失败冒泡成 500 或非结构化异常。
- 重要边界：当前只覆盖 `SecurityException`；参数错误、状态冲突、配置校验失败和资源不存在仍未统一；响应体暂不含 `traceId/requestId`。
- 下一步：可补真实登录态/IAM 角色来源、tenant/scene 级权限、更多领域异常统一错误响应，或进入结构化 review comment/history。

## 2026-06-09 P2-20 断点记录

- 当前阶段：P2-20 Admin 请求上下文角色来源最小闭环已完成。
- 已完成产物：`AdminRequestContext`、Admin 评审动作 header actor/roles 优先解析、`review-workspace` header roles 优先解析、`docs/codex/v1/trace/intent-hub-p2-admin-request-context-trace.md`。
- 验证证据 1：已执行 `mvn -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test`，结果通过。
- 验证结论 1：应用层 32 个测试、基础设施层 59 个测试、接口层 26 个测试，合计 117 个测试；覆盖请求体错误 roles 但 header roles 正确时 approve 成功，且 `approvedBy` 来自 `X-IntentHub-Actor`；覆盖 review-workspace 从 `X-IntentHub-Roles` 展示发布动作。
- 关键设计：接口层优先读取 `X-IntentHub-Actor` 与 `X-IntentHub-Roles`，缺失时回退请求体/query，保持现有脚本和内部直接调用兼容。
- 重要边界：当前不是完整 IAM/JWT 集成，尚未校验 header 来源可信度；roles 仍是全局角色，未绑定 tenant/scene。
- 下一步：可补 tenant/scene 级权限、Spring Security/JWT Filter、更多领域异常统一响应，或进入结构化 review comment/history。

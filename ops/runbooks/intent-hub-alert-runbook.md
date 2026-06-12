# Intent Hub 告警 Runbook

本文档对应 `ops/prometheus/intent-hub-alert-rules.yml` 中的 P2.x 告警规则，用于值班时快速判断影响、止血和定位。

## 通用处理原则

1. 先确认影响范围：租户、scene、渠道、时间窗口、是否只影响 LLM 兜底或模型路径。
2. 先止血再定位：必要时回滚配置版本、关闭模型服务路径、关闭 LLM 兜底或收紧预算。
3. 不让 LLM 变主链路：LLM 只能作为最后防线，fallback 激增时优先修规则、模型和配置。
4. 防腐层不越界：Intent Hub 只排查识别、路由、动作指令，不直接修改业务库或业务数据。
5. 保留证据：记录 trace_id、request_id、tenant_id、scene_id、配置版本、recognition_path 和 dashboard 截图。

## 快速入口

- 指标快照：`GET /api/v1/admin/metrics`
- Prometheus 文本：`GET /api/v1/admin/metrics/prometheus`
- 告警快照：`GET /api/v1/admin/metrics/alerts`
- 健康检查：`GET /api/v1/admin/health`
- LLM 预算查询：`GET /api/v1/admin/llm/budget-usage`
- Trace 查询：`GET /api/v1/admin/observability/traces/{traceId}`
- Bad Case 查询：`GET /api/v1/admin/observability/bad-cases`

## IntentHubBadCaseRateHigh

触发条件：

```promql
intent_hub_bad_cases_total / clamp_min(intent_hub_requests_total, 1) > 0.30
```

影响判断：

- 是否集中在某个 tenant、scene、channel。
- 是否在最近一次配置发布后出现。
- 是否由 `REJECTED`、`CLARIFY`、低置信或 fallback 失败关闭驱动。

止血动作：

- 回滚最近配置版本。
- 临时提高高频意图规则优先级。
- 对高风险 scene 暂时关闭 LLM 兜底，避免成本被异常样本放大。

定位步骤：

1. 查询 bad case 列表，按 scene、intent、status 聚合。
2. 抽样查看 trace，确认 recognition_path 是 Rule、Model 还是 LLM 失败。
3. 对照最近配置发布记录，检查 synonym、slot、routing rule、nlu_strategy。
4. 将确认样本进入标注流转，形成训练或规则补充任务。

## IntentHubModelFallbackDetected

触发条件：

```promql
intent_hub_model_fallbacks_total > 0
```

影响判断：

- 是否模型服务整体不可用。
- 是否只有某个模型 baseUrl、timeout 或 provider 配置异常。
- 规则主链路是否仍可正常识别。

止血动作：

- 关闭模型服务路径，回退 Rule -> LLM 受控兜底或 Rule-only。
- 回滚模型服务 baseUrl/timeout 配置。
- 如果模型服务不稳定，优先保持规则主链路可用。

定位步骤：

1. 查看 `GET /api/v1/admin/health` 中 `model_service.healthy`。
2. 检查模型服务 `/health` 和 `/recognize`。
3. 检查网络、DNS、timeout、模型服务日志。
4. 抽样 trace，确认 `MODEL_FALLBACK:CLOSED` 是否集中出现。

## IntentHubLlmFallbackDetected

触发条件：

```promql
intent_hub_llm_fallbacks_total > 0
```

影响判断：

- LLM 是否仍只作为最后防线。
- fallback 是否由预算、timeout、provider 异常或 prompt guard 触发。
- 是否因为规则/模型召回下降导致 LLM 被动承担主流量。

止血动作：

- 将 scene 级 `llm_policy.enabled` 关闭。
- 降低 scene 级预算或全局预算。
- 回滚触发 LLM 激增的配置版本。

定位步骤：

1. 查询 LLM 预算使用，确认 confirmed、reserved、pending。
2. 抽样 trace，查看 `LLM_FALLBACK:{fallbackDecision}`。
3. 检查 `llm_policy`、全局治理开关、timeout、maxRetries、provider。
4. 将 LLM fallback 样本转为规则补充或模型训练候选。

## IntentHubLlmBudgetReconciliationDetected

触发条件：

```promql
intent_hub_llm_budget_reconciliations_total > 0
```

影响判断：

- 是否有 stale pending 预占未释放。
- 是否 provider timeout、adapter 异常或服务重启导致。
- 是否影响当天 LLM 可用预算。

止血动作：

- 暂停高风险 scene 的 LLM 兜底。
- 缩短 provider timeout 或减少 maxRetries。
- 若 pending 持续增加，临时关闭 LLM 预算后台补偿之外的真实外呼。

定位步骤：

1. 查询 `GET /api/v1/admin/llm/budget-usage`，查看 pendingUnits。
2. 检查 `llm_budget_usage` 中 `__budget__/__daily__` 预占行。
3. 对照 provider 调用日志和 adapter 异常。
4. 复核后台补偿配置是否默认关闭或周期过长。

## IntentHubConfigPermissionDenied

触发条件：

```promql
increase(intent_hub_permission_denied_total[5m]) > 0
```

影响判断：

- 是否集中在某个 tenant、scene 或配置对象操作。
- 是否发生在新角色策略、Admin Portal、网关/IAM 变更之后。
- 是否只是预期内越权拦截，还是合法用户被错误拒绝。

止血动作：

- 如果合法用户被拒绝，先回滚最近的角色映射或 scoped role 配置。
- 如果疑似越权访问，临时收紧 Admin 入口来源、暂停相关 actor 或网关凭证。
- 保持防腐层边界：只处理 Intent Hub 配置权限和动作指令，不直接修改业务库或业务数据。

定位步骤：

1. 查询审计事件 `CONFIG_PERMISSION_DENIED`，确认 actor、tenantId、sceneId、action 和 reason。
2. 对照请求来源，确认角色是否满足 `CONFIG_VIEWER/CONFIG_EDITOR/CONFIG_APPROVER/CONFIG_PUBLISHER[:tenant:scene]`。
3. 检查 `review-workspace` 返回的 `blockedReasons`，确认是否为预期权限阻断。
4. 若由网关/IAM 注入角色，检查 `X-IntentHub-Actor`、JWT claims 或上游角色映射是否被错误变更。

禁止动作：

- 不在 Prometheus 标签中增加 actor、token、sourceIp 等高基数字段。
- 不为了快速恢复而给所有用户配置全局 `CONFIG_*:*:*` 权限。
- 不把权限失败排查扩展成直接查询或修复业务数据。

## IntentHubAdminJwtAuthFailed

触发条件：

```promql
increase(intent_hub_admin_jwt_auth_failures_total[5m]) > 0
```

影响判断：

- 是否集中在 Admin 配置入口 `/api/v1/admin/config/**`。
- 是否由 token 过期、issuer/audience 不匹配、secretRef 解析失败、签名错误或缺少 Bearer token 导致。
- 是否与网关认证配置、密钥轮换、IAM 发布或时钟漂移同时发生。

止血动作：

- 如果是合法请求失败，优先修复网关/IAM token 签发、issuer/audience 或 secretRef 配置。
- 如果疑似攻击或扫描，收紧网关来源、限流 Admin 路径，并保留审计事件。
- 若密钥轮换异常，回滚到上一版可用 secretRef 或恢复双发兼容窗口。

定位步骤：

1. 查询审计事件 `ADMIN_JWT_AUTH_FAILED`，只使用 method、path、reason 定位，不查 token 原文。
2. 检查 Admin JWT Filter 开关、`secret/secretRef`、`issuer`、`audience` 与系统时间。
3. 对照网关/IAM 日志，确认是否有 token 签发格式或 claims 变化。
4. 确认失败没有继续进入应用层授权；应用层权限失败应看 `CONFIG_PERMISSION_DENIED`。

禁止动作：

- 不记录或转发 Authorization header、JWT 原文、secret 或完整 claims。
- 不临时关闭 Admin JWT Filter 作为长期恢复手段。
- 不把认证失败和应用层权限拒绝混为同一个告警处理。

## IntentHubAverageLatencyHigh

触发条件：

```promql
intent_hub_latency_millis_avg > 1000
```

影响判断：

- 是所有流量变慢，还是某个 recognition path 变慢。
- 是否模型服务、LLM provider、数据库查询或下游动作路由导致。
- 是否与配置发布、模型服务发布或网络变更相关。

止血动作：

- 关闭慢路径：优先关闭异常模型服务或 LLM 兜底。
- 回滚最近配置版本。
- 临时提高规则命中覆盖，减少模型和 LLM 调用。

定位步骤：

1. 查看平均耗时、P95、P99 和最大耗时走势。
2. 抽样 trace，比对 Rule、Model、LLM 路径。
3. 检查模型服务和 LLM provider timeout。
4. 检查 PostgreSQL、Redis、网关、网络延迟。

## IntentHubP95LatencyHigh

触发条件：

```promql
intent_hub_latency_millis_p95 > 1500
```

影响判断：

- 是否大部分用户已经感知变慢，而不是单个离群请求。
- 是否集中在某个 tenant、scene、channel 或特定识别路径。
- 是否在模型服务、LLM 兜底或数据库读取配置后出现。

止血动作：

- 对慢 scene 临时关闭模型服务或 LLM 兜底。
- 回滚最近发布的 scene 配置或模型服务版本。
- 临时提高高频规则覆盖，减少慢路径调用。

定位步骤：

1. 对比 P95 与平均耗时，如果 P95 明显高于平均值，优先找慢 scene 和慢 path。
2. 抽样查询慢窗口内的 trace，检查 `recognition_path`。
3. 检查模型服务 `/health`、LLM provider timeout 和 PostgreSQL 查询耗时。
4. 将慢路径样本归档到试点执行记录，作为后续优化证据。

## IntentHubP99LatencyCritical

触发条件：

```promql
intent_hub_latency_millis_p99 > 3000
```

影响判断：

- 是否有 1% 左右请求达到用户超时边界。
- 是否有外部依赖卡住、timeout 未生效或连接池耗尽。
- 是否会导致上游网关重试、请求堆积或预算异常消耗。

止血动作：

- 立即关闭或隔离慢外部依赖路径，包括模型服务或 LLM。
- 收紧 timeout，并确认失败关闭路径仍能返回拒识或规则结果。
- 对高风险 tenant/scene 临时限流。

定位步骤：

1. 查询 P99 告警窗口内的 trace 和 metrics snapshot。
2. 检查是否集中在 `ModelRecognitionPolicy`、`LlmRecognizePolicy` 或 JDBC 配置读取。
3. 确认 connect/read timeout、provider 限流和网络状态。
4. 复盘是否需要把 `recognition_path` 升级为结构化 span/event。

## IntentHubMaxLatencyCritical

触发条件：

```promql
intent_hub_latency_millis_max > 3000
```

影响判断：

- 是否存在单个请求长尾，还是持续长尾。
- 是否触发用户可感知超时。
- 是否有外部依赖卡死或 timeout 未生效。

止血动作：

- 立即关闭外部慢依赖路径，包括模型服务或 LLM。
- 收紧 timeout，避免请求堆积。
- 必要时限流高风险 tenant/scene。

定位步骤：

1. 查找长尾请求对应 trace_id。
2. 查看 recognition_path 是否包含 Model 或 LLM。
3. 检查 connect/read timeout 是否按配置生效。
4. 对照 P95/P99 判断是单点异常还是群体长尾，并复盘是否需要链路 span。

## 复盘清单

- 是否需要新增规则、同义词、槽位或训练样本。
- 是否需要调整 scene routing、nlu_strategy 或 llm_policy。
- 是否需要补充 Prometheus 规则、Grafana 面板或 SLO 阈值。
- 是否需要把 recognition_path 从字符串升级为结构化 span/event。
- 是否需要对高等级租户配置独立限流、预算和告警路由。

## IntentHubAdminJwksFetchFailed

触发条件：
```promql
increase(intent_hub_admin_jwks_fetch_failures_total[5m]) > 0
```

影响判断：
- 是否只影响开启 `jwksUrl` 的 Admin JWT 验签路径。
- 是否与 IAM/OIDC JWKS endpoint 发布、DNS/TLS/代理变更、网络抖动或 `jwksFetchTimeoutMs` 调整同时发生。
- 是否已经同时出现 `IntentHubAdminJwksStaleHit`，说明系统正在依赖旧 JWKS 缓存兜底。

止血动作：
- 优先恢复 IAM/OIDC JWKS endpoint、网络代理、证书链或 DNS 解析。
- 若最近发布了 JWKS URL、网关代理或证书配置，优先回滚到上一版可用配置。
- 在确认安全边界后，可临时延长 `jwksStaleGraceSeconds` 争取恢复窗口，但必须设定回滚时间，不把 stale grace 当长期方案。

定位步骤：
1. 查看应用日志中 JWKS fetch failure 的异常类型，区分 timeout、DNS、TLS、HTTP 状态码和 JSON/JWK 解析失败。
2. 从 Intent Hub 所在网络访问 JWKS URL，确认 TLS、代理、状态码和响应体格式。
3. 对照 IAM/OIDC 发布记录，确认是否发生 key rotation、issuer 变更或 JWKS endpoint 路径变更。
4. 检查 `jwksFetchTimeoutMs` 是否过短，以及失败是否集中在网络高峰或 IAM 发布窗口。

禁止动作：
- 不记录、转发或粘贴 Authorization header、JWT 原文、私钥、完整 claims 或真实 token。
- 不为了恢复而长期关闭 Admin JWT Filter。
- 不在 Prometheus 标签中加入 issuer、kid、url、actor、tenant 等高基数字段。

## IntentHubAdminJwksStaleHit

触发条件：
```promql
increase(intent_hub_admin_jwks_stale_hits_total[5m]) > 0
```

影响判断：
- 说明 JWKS TTL 到期后的刷新失败，但仍处于 `jwksStaleGraceSeconds` 宽限窗口内。
- Admin API 当前可能仍可验签成功，但正在消耗旧 JWKS 缓存宽限期。
- 若 IAM 已完成 key rotation，新 token 可能因为旧 JWKS 无法识别新 key 而失败。

止血动作：
- 立即确认 JWKS endpoint 是否恢复；目标是在 stale grace 到期前恢复正常刷新。
- 暂缓 IAM key rotation 或确认双 key 兼容窗口仍覆盖 Intent Hub 缓存刷新周期。
- 如必须延长宽限期，应同步安全值班确认风险，并在 IAM 恢复后恢复默认值。

定位步骤：
1. 同时查看 `intent_hub_admin_jwks_fetch_failures_total` 是否增长，确认 stale 命中是否由刷新失败触发。
2. 检查当前 `jwksCacheTtlSeconds`、`jwksStaleGraceSeconds` 与 IAM key rotation 窗口是否匹配。
3. 抽样验证新旧 key 的 `kid` 是否都存在于 JWKS endpoint 中。
4. 恢复后观察 stale hit 是否停止增长，并确认 fetch failure 不再增长。

禁止动作：
- 不把 stale hit 当作健康状态；它只是受控降级信号。
- 不无限延长 `jwksStaleGraceSeconds`。
- 不绕过防腐层边界去改业务库或业务数据；本告警只处理 Admin JWT/JWKS 认证链路。

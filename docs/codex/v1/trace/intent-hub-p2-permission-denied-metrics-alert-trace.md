# Intent Hub P2-25 权限拒绝指标告警审查

## 审查结论

P2-25 权限拒绝指标告警最小闭环已完成，通过。

本阶段承接 P2-24 的 `CONFIG_PERMISSION_DENIED` 安全审计事件，把权限拒绝从“可查审计日志”推进到“可观测指标和告警快照”。实现不改变 Admin API 的 HTTP 403 响应契约，不新增数据库表，也不引入新的观测依赖。

## 本次交付物

- `IntentMetricsPort`
  - 新增 `recordPermissionDenied(tenantId, sceneId, action)` 默认方法。
- `MetricsSnapshot`
  - 新增 `totalPermissionDenied` 聚合计数。
- `InMemoryIntentMetricsRepository`
  - 记录权限拒绝计数，并在 snapshot 中返回。
- `MetricsAppService`
  - Prometheus 文本新增 `intent_hub_permission_denied_total`。
- `MetricsAlertAppService`
  - 当权限拒绝计数大于 0 时返回 `CONFIG_PERMISSION_DENIED` WARN 告警。
- `InMemoryConfigGovernanceRepository` / `JdbcAuditLogRepository`
  - 记录 `CONFIG_PERMISSION_DENIED` 审计事件时同步递增权限拒绝指标。

## 指标与告警

Prometheus 指标：

```text
intent_hub_permission_denied_total
```

告警快照：

```text
code=CONFIG_PERMISSION_DENIED
severity=WARN
```

当前先采用全局聚合计数，避免在没有真实 IAM 上下文前引入 actor、sourceIp 等高基数标签。tenant/scene/action 维度仍保留在审计 detail 中，可用于人工排查。

## 一致性审查

- 与 P2-24 一致：指标以 `CONFIG_PERMISSION_DENIED` 审计事件为触发点，审计事件仍是权限拒绝的权威记录。
- 与 P2-8 一致：继续复用现有 `/api/v1/admin/metrics`、`/api/v1/admin/metrics/prometheus`、`/api/v1/admin/metrics/alerts`。
- 与 P2-19 一致：权限失败 HTTP 403 响应结构未改变。
- 与当前观测策略一致：不引入 Micrometer/Actuator 新依赖，保持最小内置指标端口。

## 验证证据

新增或扩展测试：

- `InMemoryIntentMetricsRepositoryTest.countsModelAndLlmFallbackClosuresSeparately`
  - 覆盖 `recordPermissionDenied` 后 `totalPermissionDenied=2`。
- `InMemoryConfigGovernanceRepositoryTest.recordsPermissionDeniedMetricsWhenAuditEventIsPermissionDenied`
  - memory 模式下 `CONFIG_PERMISSION_DENIED` 审计事件会递增权限拒绝指标。
- `JdbcAuditLogRepositoryTest.recordsPermissionDeniedMetricsWhenAuditEventIsPermissionDenied`
  - JDBC 模式下 `CONFIG_PERMISSION_DENIED` 审计事件会递增权限拒绝指标。
- `AdminMetricsControllerTest`
  - metrics snapshot 返回 `totalPermissionDenied`。
  - Prometheus 文本包含 `intent_hub_permission_denied_total`。
  - alert snapshot 包含 `CONFIG_PERMISSION_DENIED`。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 36 个测试、基础设施层 61 个测试、接口层 31 个测试，合计 128 个测试。

## 风险与边界

- 当前指标为全局聚合计数，不按 tenant/scene/action 细分，避免高基数指标风险。
- 当前告警策略为“有权限拒绝即 WARN”，适合最小治理阶段；真实生产可按时间窗口、租户、来源或阈值细化。
- 当前没有接入真实 Prometheus/Alertmanager/Grafana 环境；本阶段只证明内置指标和告警快照可消费。
- 当前拒绝事件 actor 仍待真实登录态/IAM 接入后补齐。

## 后续建议

- 接入 Spring Security/JWT Filter，把可信 actor 与角色来源固化到 Admin 请求上下文。
- 在真实观测栈中为 `intent_hub_permission_denied_total` 配置 Prometheus rule 与 Alertmanager receiver。
- 若生产需要分租户告警，优先通过日志/审计检索实现，谨慎增加 Prometheus 高基数标签。
- 继续补结构化 review history，把审批动作、权限拒绝和审计查询组合成 Admin Portal 可读轨迹。

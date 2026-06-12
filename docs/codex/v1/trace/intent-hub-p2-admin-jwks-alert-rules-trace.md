# Intent Hub P2-36 Admin JWKS 告警规则审查

## 结论

通过。P2-36 已把 Admin JWKS 的失败拉取与 stale 缓存兜底纳入 Prometheus/Alertmanager 样例和 Runbook，延续 P2-35 已落地的指标出口：

- `intent_hub_admin_jwks_fetch_failures_total`
- `intent_hub_admin_jwks_stale_hits_total`

这意味着 JWKS 链路不再只停留在 metrics 暴露层，而是具备了最小可接入的告警规则、校验脚本和运维处理说明。

## 本次交付

- `ops/prometheus/intent-hub-alert-rules.yml`
  - 新增 `IntentHubAdminJwksFetchFailed`
  - 新增 `IntentHubAdminJwksStaleHit`
- `scripts/validate-observability-compose.ps1`
  - 将两条 JWKS 告警加入必检清单
  - 将两条 JWKS 指标加入 security alert 5 分钟增量窗口校验
- `ops/prometheus/README.md`
  - 补充 JWKS 安全告警说明
- `ops/alertmanager/README.md`
  - 补充 `category=security` 覆盖 JWKS 安全告警
- `ops/README.md`
  - 补充运维总入口中的 JWKS 安全指标覆盖范围
- `ops/runbooks/intent-hub-alert-runbook.md`
  - 补充 `IntentHubAdminJwksFetchFailed` 处理流程
  - 补充 `IntentHubAdminJwksStaleHit` 处理流程

## PromQL 规则

```promql
increase(intent_hub_admin_jwks_fetch_failures_total[5m]) > 0
```

用于发现 IAM/OIDC JWKS endpoint、DNS、TLS、代理、网络或 `jwksFetchTimeoutMs` 异常。

```promql
increase(intent_hub_admin_jwks_stale_hits_total[5m]) > 0
```

用于发现 JWKS TTL 到期刷新失败后，系统正在使用 stale grace 旧缓存兜底。

## 关键边界

- 告警标签继续保持低基数，只使用 `severity`、`subsystem`、`category`，不把 issuer、kid、url、actor、tenant、scene 放入 Prometheus 标签。
- stale hit 是受控降级信号，不代表健康状态；处理目标是在 `jwksStaleGraceSeconds` 到期前恢复正常 JWKS 刷新。
- Runbook 明确禁止记录或传播 Authorization header、JWT 原文、私钥、完整 claims 或真实 token。
- 防腐层边界保持不变：本告警只处理 Admin JWT/JWKS 认证链路，不访问业务库、不执行 SQL、不修复业务数据。

## 验证证据

- 已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-observability-compose.ps1
```

- 期望校验点：
  - Prometheus alert rules 文件存在
  - 新增两条 JWKS 告警名称存在
  - `intent_hub_admin_jwks_fetch_failures_total[5m]` 使用 5 分钟增量窗口
  - `intent_hub_admin_jwks_stale_hits_total[5m]` 使用 5 分钟增量窗口
  - Docker Compose、Prometheus rule 引用、Grafana provisioning 引用保持可校验

## 未覆盖风险

- 当前仍是本地配置样例和脚本校验，不等同于真实 dev/staging Alertmanager 已加载并触达值班通道。
- 当前未补 JWKS fetch latency histogram，暂不能基于延迟分位数做提前预警。
- 当前未完成真实 IAM/OIDC sandbox smoke、OIDC discovery 或生产证书链验证。

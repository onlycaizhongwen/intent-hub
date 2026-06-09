# Intent Hub P2-8 观测告警本地试点审查

## 审查结论

P2-8 已完成本地可重复试点证据链，通过。

本阶段新增 `scripts/smoke-observability-pilot-local.ps1`，在本地启动单个 Intent Hub memory 模式实例，生成 10 条识别请求，其中 6 条命中 `ORDER_QUERY/SUCCESS`，4 条进入 `UNKNOWN/REJECTED` 并形成 bad case。脚本随后验证管理端 JSON 指标、Prometheus 文本指标和告警快照接口，并生成 `ops/pilot-execution-record-local.md` 作为本地试点执行记录。

需要强调：本次完成的是“本地 endpoint 与告警快照可用性试点”，不是 dev/staging/production 的真实 Prometheus target、Alertmanager route 或 Grafana dashboard 接入完成。

## 本次交付物

- `scripts/smoke-observability-pilot-local.ps1`
  - 启动 Intent Hub jar，默认端口 `18091`。
  - 关闭模型服务和 LLM 外部依赖，保持观测试点聚焦于本地识别链路。
  - 构造 10 条请求，固定形成 6 条成功、4 条拒识。
  - 使用 UTF-8 bytes 发送 JSON 请求，避免 Windows PowerShell 默认编码破坏中文规则关键词。
  - 验证 `/api/v1/admin/health` 返回 `UP`。
  - 验证 `/api/v1/admin/metrics` 中 `totalRequests=10`、`totalBadCases=4`。
  - 验证 `/api/v1/admin/metrics/prometheus` 包含 `intent_hub_requests_total` 和 `intent_hub_bad_cases_total`。
  - 验证 `/api/v1/admin/metrics/alerts` 返回 `WARN` 且包含 `BAD_CASE_RATE_HIGH`。
  - 生成 `ops/pilot-execution-record-local.md`。
- `ops/pilot-execution-record-local.md`
  - 记录本地试点环境、流量结果、endpoint 检查、告警快照和后续缺口。

## 验证证据

已执行：

```powershell
$errors=$null; [System.Management.Automation.PSParser]::Tokenize((Get-Content -Path 'scripts/smoke-observability-pilot-local.ps1' -Raw), [ref]$errors) | Out-Null; if ($errors) { $errors | Format-List; exit 1 } else { 'smoke-observability-pilot-local.ps1 parser ok' }
```

结果：PowerShell Parser 检查通过。

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-observability-pilot-local.ps1 -SkipPackage
```

结果：

- Intent Hub health 返回 `UP`。
- 本地试点流量生成成功。
- JSON metrics endpoint 校验通过。
- Prometheus metrics endpoint 校验通过。
- alerts endpoint 校验通过。
- 生成 `ops/pilot-execution-record-local.md`。
- 指标摘要：`requests=10`、`badCases=4`、`status=WARN`、`alerts=BAD_CASE_RATE_HIGH`。
- 脚本结束后已停止本地 Intent Hub 进程。

`ops/pilot-execution-record-local.md` 记录的关键结果：

- total requests：10
- successes：6
- rejected：4
- bad cases：4
- average latency：0.9ms
- P95 latency：7.0ms
- P99 latency：7.0ms
- JSON totalRequests 检查：PASS
- JSON totalBadCases 检查：PASS
- Prometheus requests 指标检查：PASS
- Prometheus bad cases 指标检查：PASS
- BAD_CASE_RATE_HIGH 触发检查：PASS

## 修复记录

- 首次脚本语法检查失败，原因是 PowerShell here-string 结束标记没有独占行；已将记录模板改为稳定形式。
- 首次 smoke 中 10 条请求全部进入 bad case，原因是 PowerShell 默认请求编码会破坏中文规则关键词；已将 JSON body 改为 UTF-8 bytes 发送，并用 Unicode 码点构造“查一下订单”样本文本。
- 重新打包后仍复现上述编码问题，确认不是旧 jar 影响；最终通过 UTF-8 bytes 修复。

## 风险与边界

- 本次没有启动真实 Prometheus、Alertmanager 或 Grafana。
- 本次没有验证 Prometheus target UP、rule load、Alertmanager receiver、Grafana datasource 或 dashboard panel。
- 本次告警只验证 Intent Hub 内置 `/api/v1/admin/metrics/alerts` 快照，不代表 Alertmanager 已完成通知链路。
- 本次流量是 memory 模式单实例本地试点，不代表多实例、Kubernetes、负载均衡或生产网络环境。
- 本次记录不包含 receiver secret、token、签名密钥、原始 trace 输入或 bad case 明文。

## 后续建议

- 在 dev/staging 环境复制 `ops/pilot-execution-record-template.md`，形成真实环境专属试点记录。
- 接入真实 Prometheus scrape target，并记录 target UP 截图或 PromQL 查询结果。
- 加载 `ops/prometheus/intent-hub-alert-rules.yml`，验证至少 2 条 warning 与 1 条 critical 演练。
- 配置 Alertmanager sandbox receiver，验证通知到达、静默、恢复和升级路径。
- 导入 Grafana dashboard，验证请求量、耗时、decision、fallback、预算和 bad case 面板均有数据。
- 将真实试点结果反向校准当前告警阈值、Runbook 和 SLO 示例。

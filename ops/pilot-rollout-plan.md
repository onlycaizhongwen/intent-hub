# Intent Hub 观测告警试点接入计划

本文档用于把 P2.x 运维样例推进到一个试点环境。目标不是一次性生产化，而是在低风险环境里拿到真实抓取、告警路由、看板和 Runbook 演练证据。

## 试点目标

- Prometheus 能抓取试点 Intent Hub 实例的 `GET /api/v1/admin/metrics/prometheus`。
- Grafana 能展示试点环境真实指标。
- 6 条 P2.x 告警能至少完成一次沙箱触发、路由、通知和恢复验证。
- Runbook 能被值班同学按步骤执行，并记录改进项。
- 试点过程中不让 Intent Hub 直连业务数据库，不把 LLM 变成主流量路径。

## 试点范围

建议首个试点选择：

| 项目 | 建议 |
| --- | --- |
| 环境 | dev 或 staging，避免直接进入生产 |
| 租户 | 1 个内部或低风险试点租户 |
| scene | 1 个规则主导、模型/LLM 默认关闭或小流量的 scene |
| 实例数 | 1 到 2 个 Intent Hub 实例 |
| 告警接收 | 测试值班群或沙箱 webhook |
| 数据保留 | 按环境默认 retention，先验证链路 |

暂不纳入首轮试点：

- 真实业务数据修复流程。
- 生产级 SLA 承诺。
- LLM 大流量外呼。
- 跨区域高可用。
- 真实 receiver secret 入库或落仓。

## 一周执行节奏

| 日程 | 目标 | 产出 |
| --- | --- | --- |
| Day 1 | 确认试点环境、实例地址、网络访问和 metrics endpoint | 试点范围确认记录 |
| Day 2 | 接入 Prometheus scrape，确认 target `UP` | Prometheus target 截图或导出记录 |
| Day 3 | 加载 Prometheus alert rules，验证规则语法和评估状态 | rule 状态记录 |
| Day 4 | 接入 Alertmanager 沙箱 receiver，验证 warning/critical 路由 | 告警通知记录 |
| Day 5 | 导入 Grafana dashboard，确认关键面板有数据 | dashboard 截图或链接 |
| Day 6 | 执行 6 条告警演练，按 Runbook 完成止血、定位和恢复 | 演练记录 |
| Day 7 | 复盘阈值、receiver、Runbook、dashboard 和缺口 | 试点复盘结论 |

## 试点前置条件

- Intent Hub 试点实例已启动。
- `GET /api/v1/admin/health` 返回 `UP`。
- `GET /api/v1/admin/metrics/prometheus` 返回 Prometheus text/plain 指标。
- Prometheus 到 Intent Hub 网络可达。
- Alertmanager 沙箱 receiver 可接收测试消息。
- Grafana 有可用 Prometheus datasource。
- 试点期间使用的 secret 不写入仓库。

## 验收步骤

### 1. metrics endpoint

执行：

```bash
curl -i http://<intent-hub-host>:8080/api/v1/admin/metrics/prometheus
```

通过标准：

- HTTP 状态为 200。
- 响应包含 `intent_hub_requests_total`。
- 响应不包含明文手机号、身份证、订单敏感详情。

### 2. Prometheus scrape

将 [Prometheus 抓取配置](prometheus/intent-hub-scrape-config.yml) 改为试点地址。

通过标准：

- Prometheus Targets 页面中 `intent-hub` 为 `UP`。
- 查询 `intent_hub_requests_total` 有结果。
- 如果是多实例，能区分 `instance`。

### 3. Prometheus rule

加载 [Prometheus 告警规则](prometheus/intent-hub-alert-rules.yml)。

通过标准：

- Prometheus rules 页面无语法错误。
- 6 条 P2.x 告警均处于可评估状态。
- 告警标签包含 `severity` 和 `service=intent-hub`。

### 4. Alertmanager route

按 [Alertmanager 路由样例](alertmanager/alertmanager-route-sample.yml) 改成沙箱 receiver。

通过标准：

- `warning` 路由到 P2 测试通道。
- `critical` 路由到 P1 测试通道。
- critical 存在时，同 alertname 的 warning 被抑制。

### 5. Grafana dashboard

导入 [Grafana 看板样例](grafana/intent-hub-dashboard.json)。

通过标准：

- 请求量、bad case 率、耗时、decision 分布、fallback、LLM 预算活动面板可显示数据。
- dashboard 使用试点 datasource。
- 面板没有持续报错。

### 6. Runbook 演练

按 [告警 Runbook](runbooks/intent-hub-alert-runbook.md) 逐条演练。

通过标准：

- 每条告警有触发方式、通知截图或日志、处理记录和恢复记录。
- 发现的阈值、文案、receiver 或 dashboard 问题被记录。
- 涉及业务数据修复的事项被转交业务系统 owner，而不是在 Intent Hub 内处理。

## 回滚策略

| 变更 | 回滚方式 |
| --- | --- |
| Prometheus scrape | 移除或注释试点 scrape job |
| Prometheus rule | 移除 rule file 或回滚到上一版规则 |
| Alertmanager route | 回滚 receiver 和 route 配置 |
| Grafana dashboard | 删除试点 dashboard 或恢复上一版 JSON |
| Runbook 文案 | 回滚文档提交 |

回滚后必须确认：

- Prometheus 配置 reload 成功。
- Alertmanager 配置 reload 成功。
- Grafana 无异常 dashboard。
- 试点 receiver 不再收到错误噪音。

## 试点复盘模板

```markdown
# Intent Hub 观测告警试点复盘

- 试点环境：
- 试点租户：
- 试点 scene：
- 时间窗口：
- 参与人：

## 通过项

- 

## 问题项

- 

## 阈值调整

- 

## Runbook 调整

- 

## 是否进入下一环境

- 结论：
- 条件：
```

## 当前结论

下一步应选择一个 dev/staging 试点环境，先完成 Day 1 到 Day 3：确认试点范围、接入 Prometheus scrape、加载规则并确认 rule 可评估。

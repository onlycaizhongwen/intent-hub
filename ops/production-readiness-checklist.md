# Intent Hub 生产化落地检查清单

本文档用于把 `ops/` 下的观测样例推进到真实环境。所有条目完成前，当前运维资产只能视为 P2.x 样例，不应宣称为生产部署方案。

## 1. 指标入口

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| metrics endpoint | `GET /api/v1/admin/metrics/prometheus` 可被 Prometheus 稳定抓取 | 已有样例 | 待定 |
| endpoint 鉴权 | Prometheus 访问 metrics endpoint 具备鉴权、网络白名单或网关策略 | 未落地 | 待定 |
| 多实例聚合 | Prometheus 按实例、租户、scene 聚合指标，不依赖单实例内存快照 | 未落地 | 待定 |
| 指标标签 | 明确 `tenant_id`、`scene_id`、`decision`、`recognition_path` 等标签策略 | 部分样例 | 待定 |
| 指标保留周期 | Prometheus retention 满足排障和审计要求 | 未落地 | 待定 |

## 2. Prometheus

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| 服务发现 | 使用 K8s service discovery、Consul、Nacos 或静态实例清单 | 样例为静态 target | 待定 |
| scrape 间隔 | 按环境确认 `scrape_interval` 与 `scrape_timeout` | 样例为 30s/5s | 待定 |
| rule file 加载 | 告警规则进入真实 Prometheus rule 配置并可热加载或发布 | 未落地 | 待定 |
| 规则演练 | 对 6 条 P2.x 告警做人工或沙箱触发演练 | 未落地 | 待定 |
| 高可用 | Prometheus HA 或远端存储策略明确 | 未落地 | 待定 |

## 3. Alertmanager

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| receiver | 替换样例 webhook 为真实值班通道 | 未落地 | 待定 |
| secret 管理 | webhook token、签名密钥和代理凭证不进入仓库 | 未落地 | 待定 |
| 告警分级 | `critical`、`warning`、默认 receiver 与组织值班等级一致 | 样例已提供 | 待定 |
| 升级策略 | 超过响应时限自动升级到 P1/P2 owner | 未落地 | 待定 |
| 静默策略 | 发布、演练和已知故障期间的 silence 流程明确 | 未落地 | 待定 |
| 抑制规则 | critical 存在时抑制同 alertname 的 warning | 样例已提供 | 待定 |

## 4. Grafana

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| datasource | 使用真实 Prometheus datasource，不使用样例默认名 | 未落地 | 待定 |
| dashboard provisioning | dashboard 通过 GitOps、Terraform 或平台发布 | 样例 JSON 已提供 | 待定 |
| 权限 | 看板查看、编辑和发布权限分离 | 未落地 | 待定 |
| 持久化 | Grafana 配置、dashboard 和 datasource 可备份恢复 | 未落地 | 待定 |
| P95/P99 | 补充 histogram 或 OpenTelemetry/Micrometer 后展示分位耗时 | 未落地 | 待定 |

## 5. SLO 与错误预算

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| 租户等级 | 核心租户、普通租户、试点租户的 SLO 分层明确 | 未落地 | 待定 |
| scene 等级 | 核心 scene 与低风险 scene 的阈值不同 | 未落地 | 待定 |
| 质量预算 | bad case 率、拒识率、澄清率进入质量预算讨论 | 部分样例 | 待定 |
| LLM 预算 | LLM fallback、预算消费和补偿作为受控指标评审 | 部分样例 | 待定 |
| 审批记录 | 正式 SLA/SLO 有业务、研发、运维共同确认记录 | 未落地 | 待定 |

## 6. 安全与合规

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| TLS | 网关、Prometheus、Alertmanager、Grafana 通信启用 TLS 或内网等效保护 | 未落地 | 待定 |
| RBAC | Admin API、metrics、dashboard 按角色授权 | 未落地 | 待定 |
| 敏感信息 | trace、bad case、告警内容不泄漏手机号、身份证、订单敏感字段 | 代码已做部分脱敏 | 待定 |
| 审计 | 配置发布、告警静默、receiver 变更有审计记录 | 部分支持 | 待定 |
| 防腐层边界 | Runbook 不要求 Intent Hub 直连业务库修复数据 | 已明确 | 待定 |
| Secret resolver | 模型服务和 LLM 凭证统一通过 Secret 引用解析，不在配置/DB/trace/仓库保存明文 | 已有 env/system property 默认实现 | 待定 |
| Secret 后端 | Vault、K8s Secret 或 Nacos 加密配置等生产级后端完成接入与权限控制 | 已预留文件挂载后端和 managed-config 映射；生产级权限、轮换、审计未落地 | 待定 |
| Secret 轮换 | token 轮换后客户端缓存可刷新或重建，旧 token 不长期驻留 | 模型服务 scene 客户端已按 token 指纹感知轮换并重建；本地文件挂载轮换 smoke 已通过；真实 Secret 后端轮换演练未落地 | 待定 |
| 外部联调预检 | `scripts/preflight-external-integration.ps1` 可在真实 smoke 前检查 Intent Hub、模型服务健康和 Secret 引用存在性，且不打印密钥值 | 已有本地预检脚本 | 待定 |
| 外部联调记录 | 真实模型服务与 DashScope/LLM smoke 按模板记录 Secret 引用、preflight、鉴权、trace、预算、指标和安全复核证据 | 已提供 `ops/external-integration-smoke-record-template.md`，真实记录未落地 | 待定 |
| 外部鉴权 smoke | 带鉴权模型服务和 DashScope 沙箱均完成最小外部调用证据 | 本地带鉴权模型服务 smoke 已完成；真实远端模型服务与 DashScope 未落地 | 待定 |

## 7. 演练与验收

| 检查项 | 目标状态 | 当前状态 | 负责人 |
| --- | --- | --- | --- |
| 本地联调 | `ops/local-observability` 可完整启动并看到 target `UP` | 未执行 | 待定 |
| 告警演练 | 6 条告警均能触发、路由、通知、恢复 | 未执行 | 待定 |
| Runbook 演练 | 值班同学能按 Runbook 完成止血和定位 | 未执行 | 待定 |
| 故障复盘 | 演练后更新阈值、receiver、Runbook 和 dashboard | 未执行 | 待定 |
| 回滚路径 | Prometheus rule、Alertmanager route、Grafana dashboard 有回滚方案 | 未落地 | 待定 |

## 通过标准

生产化准入至少满足：

- Prometheus 能稳定抓取所有 Intent Hub 实例。
- 关键告警能路由到真实值班通道，并完成一次演练。
- Grafana dashboard 能展示真实环境数据。
- SLO、阈值、receiver 和升级策略完成业务、研发、运维三方确认。
- metrics endpoint、receiver secret、dashboard 权限和 TLS/网络策略完成安全确认。
- 模型服务和 LLM 凭证通过生产级 Secret 后端解析，并完成一次轮换或等效演练。
- 真实模型服务和 DashScope/LLM 冒烟记录完整留存，且记录中不包含明文密钥。
- Runbook 完成一次值班演练，并根据演练结果更新。

## 当前结论

当前状态为“样例完整，生产化待落地”。下一步应优先选择一个试点环境，按本清单完成 Prometheus 抓取、Alertmanager 路由、Grafana 导入和告警演练。

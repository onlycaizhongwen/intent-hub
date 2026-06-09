# 意图中枢 P2 下一步执行计划

## 目标

P2-1 到 P2-5 已经把动态 scene、Bad Case 流转、指标观测、真实模型服务 adapter、LLM 受控兜底和基础运维样例补齐。下一步不再继续零散补点，而是进入 P2 后半段的生产化试点闭环。

推荐主线：

```text
P2-6 密钥治理与外部联调准入
-> P2-7 多实例一致性与压测
-> P2-8 观测告警真实试点
-> P2-9 配置发布治理增强
```

优先级判断：

- 模型服务已经支持 scene 级 endpoint/timeout 和 `authTokenRef`，且缺 token 会失败关闭；下一步应把“引用名如何安全解析、轮换、审计”固化。
- LLM 已经完成 Spring AI Alibaba 预接入、预算门禁和 DashScope smoke profile/script；下一步应补真实沙箱外呼证据，但凭证必须只来自环境或 Secret。
- 预算和幂等已经有 JDBC 实现；下一步应验证多实例下的原子预占、失败释放和后台补偿。
- Prometheus/Grafana/Alertmanager 样例已具备；下一步应做 dev/staging 试点执行记录，而不是继续只写样例。

## 当前基线

已完成：

- P1 最小闭环、PostgreSQL/Flyway、配置治理、可观测查询和 P1 退出评审。
- P2-1 动态 scene 读取。
- P2-2 Bad Case 标注、关闭、导出训练样本。
- P2-3 最小指标、Prometheus 文本、基础告警快照、P95/P99 长尾耗时。
- P2-4 模型服务 adapter、FastAPI 示例、容器端到端 smoke、scene 级模型策略、动态 endpoint/timeout、Bearer token 引用鉴权、缺 token 失败关闭。
- P2-5 LLM 受控兜底、预算审计、原子预占、失败释放、stale pending 补偿、Spring AI Alibaba 预接入、DashScope smoke 准备、运维样例。

当前仍未完成的生产化缺口：

- Secret/Vault/K8s Secret 未接入，当前 token 解析只支持系统属性和环境变量。
- DashScope 未使用真实沙箱密钥完成外部调用证据。
- 模型服务鉴权未做真实服务联调，仅完成本地 header 注入与缺 token 失败关闭。
- LLM 预算、多实例并发、PostgreSQL 行级一致性尚未做压力验证。
- Prometheus/Grafana/Alertmanager 仍是样例，未形成真实试点执行记录。
- 配置发布流程已有 API 与审计，但还没有 GitOps/审批/差异审查闭环。

## P2-6 密钥治理与外部联调准入

### 目标

把模型服务和 LLM 的外部调用从“能接”推进到“可安全试点接入”。

### 范围

- 统一 Secret 引用解析边界。
- 保持配置、数据库和文档中只保存引用名，不保存明文密钥。
- 对模型服务和 LLM 使用同一套 Secret 解析语义。
- 补齐缺 Secret、Secret 轮换、鉴权失败、真实外部 smoke 的验证入口。

### 实施步骤

1. 抽象 Secret 解析端口
   - 新增类似 `SecretResolverPort` / `SecretRefResolver` 的基础设施边界。
   - 首版实现仍可支持 system property + environment variable。
   - 为后续 Vault/K8s Secret/Nacos encrypted config 预留 adapter，不在领域层引入供应商依赖。

2. 统一模型服务与 LLM 的 Secret 解析
   - 模型服务继续使用 `modelPolicy.authTokenRef`。
   - LLM/DashScope 继续读取环境或 Secret 引用，不允许写入配置包。
   - 缺 Secret 统一失败关闭，路径或状态可观测，但不输出 secret 值。

3. 增加外部联调前检查脚本
   - 扩展或新增 smoke preflight，检查 token 引用是否存在、服务地址是否可达、健康检查是否返回预期字段。
   - 失败时输出缺哪类配置，不输出 token 内容。

4. 执行真实外部 smoke
   - 模型服务：使用带鉴权的真实或模拟服务验证 Bearer header。
   - LLM：在提供 DashScope 沙箱凭证后执行 `scripts/dashscope-smoke.ps1`。
   - 记录 trace、recognitionPath、metrics、budget usage 和 bad case 情况。

5. 文档和审计
   - 更新 P2-4/P2-5 trace。
   - 更新 `ops/production-readiness-checklist.md` 的 Secret 与外部联调检查项。
   - 新增或复用试点执行记录模板，固化本次证据。
   - 真实远端模型服务或 DashScope smoke 必须复制 `ops/external-integration-smoke-record-template.md` 留存 Secret 引用、preflight、鉴权、trace、预算、指标和安全复核证据。

### 验证方式

- 单元测试覆盖：
  - Secret 引用存在且解析成功。
  - Secret 引用存在但解析失败。
  - 不同 scene 使用不同 token 引用。
  - 不把 token 写入日志、trace、bad case、配置导出。
- 集成或 smoke 覆盖：
  - 模型服务鉴权 header。
  - DashScope 沙箱触发 LLM 路径。
  - 预算预占、消费、失败释放。

### 风险与回滚

- 风险：错误配置导致外部调用失败。
  - 回滚：关闭 scene 级 `modelPolicy.enabled` 或 `llmPolicy.enabled`，恢复规则主链路。
- 风险：Secret 轮换导致旧客户端缓存继续使用旧 token。
  - 控制：P2-6 需要明确客户端缓存 key 是否包含 token 版本或 token 值摘要；轮换后应能刷新或重建客户端。
- 风险：真实 LLM 成本不可控。
  - 控制：保留全局预算和 scene 预算取较小值；沙箱 smoke 使用极小预算。

### 完成标准

- Secret 解析边界清晰，明文不入库、不入仓、不入 trace。
- 模型服务和 LLM 都能复用同一类 Secret 引用治理。
- 缺 Secret 会失败关闭并可观测。
- 至少完成一次带鉴权模型服务 smoke；DashScope smoke 在凭证可用时完成。
- 本地带鉴权模型服务 smoke 只能证明最小链路；真实远端模型服务和 DashScope/LLM smoke 需要按外部联调记录模板留证后，才能作为试点准入证据。

## P2-7 多实例一致性与压测

### 目标

验证 Intent Hub 在多实例下的关键一致性能力，尤其是 LLM 日预算原子预占、幂等记录、trace/bad case 写入和配置读取。

### 实施步骤

1. 新增本地多实例压测脚本或测试说明。
2. 使用同一 PostgreSQL，启动 2 个以上 Intent Hub 实例。
3. 并发触发：
   - 同 requestId 异步动作幂等。
   - LLM 日预算接近耗尽时的并发预占。
   - 模型服务异常/缺 token fallback。
4. 查询数据库验证：
   - `idempotency_record` 无重复业务键。
   - `llm_budget_usage` 不超预算。
   - pending 能被释放或补偿。
   - trace 数量与请求数一致。

### 验证方式

- 压测脚本输出请求数、成功数、拒识数、fallback 数、预算消耗、pending 差额。
- PostgreSQL 查询脚本输出关键表聚合结果。
- 相关模块测试继续通过。

### 完成标准

- 多实例并发下不超 LLM 日预算。
- 幂等键稳定且不重复触发异步动作。
- 失败释放和后台补偿指标可观察。

## P2-8 观测告警真实试点

### 目标

把 `ops/` 下的样例推进为 dev/staging 试点接入记录。

### 实施步骤

1. 复制 `ops/pilot-execution-record-template.md` 为环境专属记录。
2. 接入 Prometheus scrape。
3. 加载 Prometheus rules。
4. 导入 Grafana dashboard。
5. 配置 Alertmanager receiver。
6. 按 `ops/alert-drill-scenarios.md` 做演练。
7. 回填截图、PromQL、触发时间、恢复时间和问题清单。

### 验证方式

- Prometheus target UP。
- Dashboard 有请求、耗时、decision、fallback、预算数据。
- 至少触发并恢复 2 条 warning 和 1 条 critical 演练。

### 完成标准

- 形成一份真实试点执行记录。
- Runbook 能指导定位。
- 告警阈值有初步调整建议。

## P2-9 配置发布治理增强

### 目标

把 Admin API 配置治理从“可发布、可回滚、可审计”增强到“可评审、可对比、可同步”。

### 实施步骤

1. 配置版本 diff API。
2. 发布前 dry-run 校验报告。
3. 导出 bundle 的 GitOps 文件结构建议。
4. 配置审批状态预留：`DRAFT -> REVIEWING -> APPROVED -> PUBLISHED`。
5. 继续完善跨对象引用校验和 action schema 安全校验。

### 完成标准

- 发布前能看到变更差异。
- 配置包可以进入 Git 审查。
- 回滚和审计仍保持兼容。

## 推荐执行顺序

第一优先级：P2-6。

原因：

- 它直接承接最近完成的模型服务动态路由和 `authTokenRef` 鉴权。
- 它也是真实 DashScope smoke、真实模型服务鉴权联调和后续多实例压测的前置条件。
- 它能进一步强化三条架构铁律：LLM 受控、模型服务受控、防腐层不碰业务数据。

第二优先级：P2-7。

原因：

- LLM 预算、幂等和 PostgreSQL 行级一致性只有在并发下才真正暴露问题。
- P2-7 可以为生产试点提供准入证据。

第三优先级：P2-8。

原因：

- 当前观测资料已经足够完整，但仍缺真实试点证据。
- P2-8 能把运维样例变成可交付的试点记录。

P2-9 可并行或后置。

原因：

- 配置治理已经足够支撑 P2 试点。
- GitOps/审批/diff 会明显提升平台化能力，但不是外部联调的前置阻塞。

## 下一步最小动作

建议立即开始 P2-6 的第一个小闭环：

1. 新建 Secret 解析端口与默认 env/system property 实现。
2. 将模型服务 token 解析迁移到该端口。
3. 为 LLM/DashScope 预留同一解析入口。
4. 补缺 Secret 失败关闭测试、token 不落 trace 测试。
5. 同步 P2-4/P2-5 trace 和 status。

该闭环不需要真实生产凭证，也不会改变默认关闭语义，适合作为下一次实现任务。

# Intent Hub P2-7 多实例一致性与压测审查

## 审查结论

P2-7 本地四段验证通过。

本阶段已完成本地双实例一致性最小验证：两个 Intent Hub 实例共享同一个 PostgreSQL 16 数据库，由实例 A 发布配置后，实例 A 与实例 B 对同一 `requestId` 的异步识别请求返回同一个非空幂等键，数据库 `idempotency_record` 仅保留 1 条对应记录，`recognition_trace` 按两次请求写入 2 条记录。

第二段已补充 LLM 日预算多实例并发预占 smoke：两个 Intent Hub 实例共享同一个 PostgreSQL 16 数据库和本地 mock LLM 服务，发布 `dailyBudget=2` 的 LLM 策略后，6 个请求在实例 A/B 间交替并发发起，最终只有 2 个请求成功命中 LLM，剩余请求进入 `LLM_FALLBACK:REJECTED`；管理端预算查询显示 `reservedUnits=2` 且派生 pending 为 0，证明日预算门禁没有跨实例超额。

第三段已补充 LLM stale pending 后台补偿 smoke：两个 Intent Hub 实例同时开启 `intent-hub.llm.budget-reconciliation.enabled=true`，脚本直接写入一组过期预算数据 `__budget__/__daily__:2:2.0000` 与 `http-contract/mock-llm:1:1.0000`，等待调度后只发生 1 次补偿，最终预算行校正为 `__budget__/__daily__:1:1.0000`，provider 外呼审计保持 `http-contract/mock-llm:1:1.0000`。

第四段已补充基础小规模双实例并发压测：两个 Intent Hub 实例共享同一个 PostgreSQL 16 数据库，发布纯规则识别 scene 后并发发送 40 个请求，其中 32 个命中 `ORDER_CANCEL/SUCCESS`，8 个进入 `UNKNOWN/REJECTED`，fallback 为 0，平均耗时 246.48ms，P95 为 319ms，P99 为 471ms；数据库 `recognition_trace=40`、`bad_case=8`。

该验证证明当前 JDBC 幂等唯一约束、已发布配置读取、跨实例 trace 写入、LLM 日预算预占门禁、失败释放、stale pending 后台补偿和基础并发请求处理在本地双实例场景下可以形成最小闭环。它不是完整生产压力测试，也不覆盖真实 LLM provider、Kubernetes 多副本或生产负载均衡。

## 本次交付物

- `scripts/smoke-multi-instance-consistency.ps1`
  - 启动临时 PostgreSQL 16。
  - 启动两个 Intent Hub `local-jdbc` 实例，默认端口为 `18082` 和 `18083`。
  - 通过实例 A 发布专用 scene 配置。
  - 对实例 A/B 分别发送相同 `tenantId + sceneId + requestId` 的异步识别请求。
  - 验证两个响应均为 `ORDER_CANCEL/ASYNC_ACCEPTED`。
  - 验证两个响应返回同一个非空 `idempotencyKey`。
  - 查询 PostgreSQL 验证 `idempotency_record` 只有 1 条共享幂等键记录。
  - 查询 PostgreSQL 验证 `recognition_trace` 有 2 条对应 trace。
  - 结束后清理 Java 进程和 PostgreSQL 容器。
- `scripts/smoke-llm-budget-multi-instance.ps1`
  - 启动临时 PostgreSQL 16。
  - 启动本地 mock LLM HTTP 服务，默认端口为 `18086`。
  - 启动两个 Intent Hub `local-jdbc` 实例，默认端口为 `18084` 和 `18085`。
  - 通过实例 A 发布专用 scene 配置，开启 LLM 策略并设置 `dailyBudget=2`。
  - 对实例 A/B 交替并发发送 6 个未知意图请求。
  - 验证 LLM 成功命中数等于日预算 2。
  - 验证预算耗尽和异常释放后的请求进入 `LLM_FALLBACK:REJECTED`。
  - 验证管理端预算查询 `reservedUnits` 不超过日预算且 `pendingUnits=0`。
  - 查询 PostgreSQL 验证 `llm_budget_usage` 仅有预算行和 provider 行，`recognition_trace` 数量等于请求数。
  - 结束后清理 Java 进程、mock LLM 进程和 PostgreSQL 容器。
- `scripts/smoke-llm-budget-reconciliation-multi-instance.ps1`
  - 启动临时 PostgreSQL 16。
  - 启动两个 Intent Hub `local-jdbc` 实例，默认端口为 `18087` 和 `18088`。
  - 两个实例均开启默认关闭的 LLM 预算补偿 scheduler，并将 `stale-after/interval` 设置为 `PT1S`。
  - 直接写入一组过期 `llm_budget_usage` 数据，模拟补偿前 pending 状态。
  - 通过管理端预算查询验证补偿前派生 pending 为 1。
  - 等待后台补偿执行，验证派生 pending 变为 0。
  - 验证补偿指标跨实例合计为 1。
  - 查询 PostgreSQL 验证预算行被校正到 provider confirmed 用量，provider 外呼审计不被回滚。
  - 结束后清理 Java 进程和 PostgreSQL 容器。
- `scripts/stress-multi-instance-basic.ps1`
  - 启动临时 PostgreSQL 16。
  - 启动两个 Intent Hub `local-jdbc` 实例，默认端口为 `18089` 和 `18090`。
  - 通过实例 A 发布纯规则识别 scene。
  - 并发向两个实例交替发送请求。
  - 默认发送 40 个请求，每 5 个请求制造 1 个未知意图样本。
  - 输出请求数、成功数、拒识数、fallback 数、错误数、平均/P95/P99 耗时。
  - 查询 PostgreSQL 验证 `recognition_trace` 与 `bad_case` 数量。
  - 结束后清理 Java 进程和 PostgreSQL 容器。

## 验证证据

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-multi-instance-consistency.ps1 -SkipPackage
```

结果：

- 两个实例健康检查均返回 `UP`。
- Flyway 已应用 `V1/V2/V3`。
- 实例 A 发布配置成功。
- 实例 A 返回 `ORDER_CANCEL/ASYNC_ACCEPTED`。
- 实例 B 返回同一个幂等键。
- `idempotency_record` 共享幂等键记录数为 1。
- `recognition_trace` 对应请求记录数为 2。
- 脚本结束后已停止两个 Java 进程并删除临时 PostgreSQL 容器。

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stress-multi-instance-basic.ps1 -SkipPackage
```

结果：

- 两个 Intent Hub 实例健康检查均返回 `UP`。
- 实例 A 发布基础压测配置成功。
- 压测请求数为 40。
- 成功数为 32。
- 拒识数为 8。
- fallback 数为 0。
- transport error 数为 0。
- 平均耗时为 246.48ms。
- P95 耗时为 319ms。
- P99 耗时为 471ms。
- 数据库 `recognition_trace=40`。
- 数据库 `bad_case=8`。
- 脚本结束后已停止两个 Java 进程并删除临时 PostgreSQL 容器。

同时执行过 PowerShell Parser 语法检查，结果通过。

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-multi-instance.ps1 -SkipPackage
```

结果：

- 两个 Intent Hub 实例健康检查均返回 `UP`。
- 本地 mock LLM 健康检查返回 `UP`。
- 实例 A 发布 LLM budget smoke 配置成功。
- 6 个请求中 LLM 成功命中数为 2，等于 `dailyBudget=2`。
- 其余请求进入 `LLM_FALLBACK:REJECTED`。
- 管理端预算查询显示 reserved 预算没有超过日预算，派生 pending 为 0。
- 数据库预算明细为 `__budget__/__daily__:2:2.0000` 与 `http-contract/mock-llm:4:4.0000`。
- `recognition_trace` 对应请求记录数为 6。
- 脚本结束后已停止两个 Java 进程、mock LLM 进程并删除临时 PostgreSQL 容器。

预算口径说明：

- `__budget__/__daily__` 行是日预算门禁口径，表示当前成功预占且未释放的预算。
- provider/model 行是真实外呼尝试审计口径，包含并发下已经发起但后续异常释放预占的尝试。
- 因此在异常释放存在时，`consumedUnits` 可以大于成功命中数，但 `reservedUnits` 必须不超过日预算，且同步请求结束后派生 pending 应为 0。

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-llm-budget-reconciliation-multi-instance.ps1 -SkipPackage
```

结果：

- 两个 Intent Hub 实例健康检查均返回 `UP`。
- 脚本成功插入 stale pending 预算数据。
- 补偿前派生 pending 为 1。
- 后台补偿后派生 pending 为 0。
- 数据库预算明细为 `__budget__/__daily__:1:1.0000` 与 `http-contract/mock-llm:1:1.0000`。
- 两个实例的补偿指标合计为 1。
- 脚本结束后已停止两个 Java 进程并删除临时 PostgreSQL 容器。

## 风险与边界

- 本阶段验证的是本地双实例和同库一致性，不代表生产多副本压测完成。
- 当前 LLM smoke 使用本地 mock LLM 服务，不代表真实 DashScope、OpenAI 或其他 provider 外呼成功。
- 当前已覆盖 LLM 日预算多实例并发预占、同步失败释放和 stale pending 后台补偿 smoke。
- 当前 stress 仍是本地小规模并发验证，不等同于生产压测。
- 当前 smoke/stress 未覆盖 Kubernetes、负载均衡、滚动发布、连接池耗尽、数据库锁等待等生产因素。

## 后续建议

下一段建议：

- 将基础压测扩展到模型服务异常/缺 token fallback 组合场景。
- 将压测结果与 Prometheus 指标口径对齐，为 P2-8 真实观测告警试点提供证据。
- 在 staging/Kubernetes 环境补真实多副本压测，覆盖负载均衡、连接池、数据库锁等待和滚动发布。

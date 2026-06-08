# P2-3 指标采集与观测接口

## 恢复胶囊

- 任务需求：继续 P2，补齐最小指标采集与观测查询接口，为后续 Prometheus/Grafana 接入打基础。
- 关键决策：P2-3 先不引入 Actuator/Micrometer 依赖，不改变 `/api/v1/admin/health` 口径；通过应用层端口 + 内存实现 + Admin API 暴露 JSON 和 Prometheus 文本。
- 当前阶段：已完成，后续已追加 P95/P99 长尾耗时指标与告警；新提交待用户明确指令后推送 GitHub。
- 已完成产物：Metrics 端口/模型/服务、内存实现、Admin API、P95/P99 长尾耗时、测试、README/status/design/HTML/trace 文档同步。
- 剩余工作：无；后续进入真实模型服务、小流量 LLM 兜底或 Micrometer/OpenTelemetry 桥接。
- 重要发现：当前项目未暴露 `/actuator/health`，README 明确健康检查为 `GET /api/v1/admin/health`；P2-3 不应突然改变该契约。

## 步骤列表

- [v] 建立 P2-3 任务记录。
- [v] 实现最小指标采集与查询接口。
  - 当前产物：`IntentMetricsPort`、`MetricsAppService`、`MetricsSnapshot`、`InMemoryIntentMetricsRepository`、`AdminMetricsController`。
  - 下一步：已完成。
  - 涉及文件：`intent-hub-application`、`intent-hub-infrastructure`、`intent-hub-interfaces`
- [v] 补测试。
- [v] 同步 README/status/HTML/design/trace。
- [v] 运行测试并提交。
  - 当前产物：`mvn test` 已通过，共 26 个测试。
  - 下一步：已推送。
  - 涉及文件：全仓变更。

## 研究发现

- `RecognizeAppService` 是识别主链路编排点，适合记录请求总数、decision、intent、scene 和耗时。
- 当前没有 Micrometer/Actuator 依赖；直接引入会改变依赖面和运维口径，P2-3 先用自定义端口更稳。
- Prometheus 文本格式可由接口层从指标快照派生，后续再替换为 Micrometer registry。
- P2-3 已新增 `GET /api/v1/admin/metrics` 和 `GET /api/v1/admin/metrics/prometheus`。
- 当前指标为进程内内存指标，服务重启后清零；这是 P2-3 最小闭环的已知边界。
- 2026-06-08：已追加 `p95LatencyMillis` 与 `p99LatencyMillis`，内存实现采用有限窗口近似分位数；Prometheus 文本新增 `intent_hub_latency_millis_p95` / `intent_hub_latency_millis_p99`，基础告警快照新增 `P95_LATENCY_HIGH` / `P99_LATENCY_HIGH`。
- 本地提交：`200da56 Expose minimal intent metrics`。
- 推送记录：`git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 push origin main` 成功，push 输出显示 `d44f8e2..200da56 main -> main`；随后 `git ls-remote` 查询仍因 GitHub 443 超时失败，但 push 已返回成功。

## 错误记录

- GitHub 远端查询仍因 443 超时失败；P2-2 已本地提交但未推送。

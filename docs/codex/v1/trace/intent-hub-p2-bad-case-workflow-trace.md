# Intent Hub P2-2 Bad Case 标注流转审查

## 审查结论

通过。

P2-2 已完成 Bad Case 标注、关闭和训练样本导出的最小闭环，符合 P2 试点扩展目标：在不破坏 P1 Schema 的前提下，让拒识/低置信度样本从“可查询”推进到“可人工修正、可导出、可进入训练数据准备”。

## 范围

本次审查覆盖：

- 应用层 bad case workflow 端口与服务。
- Admin Observability API 新增写操作入口。
- memory 与 JDBC 双实现。
- 自动化测试。
- README、P1 设计、P1 下一步计划、status 和 HTML 阅读版同步。

## 实现结果

新增应用层模型与端口：

- `BadCaseWorkflowAppService`
- `BadCaseWorkflowPort`
- `BadCaseActionResult`
- `BadCaseTrainingSample`

新增/调整接口：

| 接口 | 方法 | 结果 |
| --- | --- | --- |
| `/api/v1/admin/observability/bad-cases/{traceId}/annotate` | POST | 标注 bad case，状态变为 `ANNOTATED` |
| `/api/v1/admin/observability/bad-cases/{traceId}/close` | POST | 关闭 bad case，状态变为 `CLOSED` |
| `/api/v1/admin/observability/bad-cases/export` | GET | 导出训练样本，可选 `markExported=true` 标记为 `EXPORTED` |

最小状态流转：

- `OPEN`：识别链路自动沉淀。
- `ANNOTATED`：人工修正意图与备注。
- `EXPORTED`：训练样本已导出。
- `CLOSED`：人工确认处理完成。

## 架构一致性

### 双阶段路由

本次变更不改变识别主链路和前后置路由契约，只在识别结果沉淀后的运营回流侧增加状态流转。因此不会破坏“先选怎么认，再选谁来干”的主线。

### LLM 受控

本次变更未引入 LLM 调用，也未把 LLM 放进主识别路径。Bad Case 导出的训练样本可服务后续规则、模型或 LLM 评估，但不会绕开策略治理。

### 防腐层

本次变更仅操作 Intent Hub 自有 `bad_case` 运行域数据，不触碰业务库、不生成 SQL 下发给下游、不改变 `downstream_action` 的防腐层边界。

### DDD 分层

应用层定义 workflow port 和用例服务；基础设施层实现 memory/JDBC 写操作；接口层仅做 HTTP 入参出参转换。领域层没有新增 Spring Web、JDBC 或外部 SDK 依赖。

## 验证证据

已执行：

```bash
mvn test
```

结果：

- Reactor build success。
- 测试共 24 个。
- 失败 0，错误 0，跳过 0。

覆盖点：

- `BadCaseWorkflowRepositoryTest` 覆盖 memory 模式 `OPEN -> ANNOTATED -> EXPORTED -> CLOSED`。
- `AdminObservabilityControllerTest` 覆盖 annotate、close、export 三个接口契约。
- 既有识别、配置治理、动态 scene 读取测试仍全部通过。

## 当前限制

- P2-2 不新增 DB migration，JDBC 标注复用 `bad_case.intent_code` 存修正意图，复用 `bad_case.reason` 存备注。
- 目前没有独立 `bad_case_annotation` 历史表，因此不能保留多次标注、审核人、审核时间和标注版本。
- actor 尚未写入 `audit_log`。
- 导出训练样本仅返回 JSON 列表，未写对象存储、未发 Kafka、未触发训练任务。
- JDBC 导出使用 PostgreSQL `input_snapshot::text`，与当前 PostgreSQL 16+ 选型一致；若未来引入 H2/JDBC 通用集成测试，需要补兼容 SQL。

## 后续建议

P2-3 优先进入指标采集与观测看板：

- Prometheus/OpenTelemetry 基础指标。
- 识别量、拒识率、澄清率、规则命中率、bad case 生成率、标注完成率、导出量。
- Grafana 看板和基础告警。

P2.x 再增强 Bad Case 平台化：

- 新增 `bad_case_annotation` 表。
- 写入 actor、annotated_at、review_status、export_batch_id。
- 支持批量导出、对象存储、Kafka 训练数据 topic。
- 将导出批次与模型训练任务、模型版本和回滚策略关联。

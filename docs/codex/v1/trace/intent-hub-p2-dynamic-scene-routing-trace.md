# IntentHub P2-1 动态 scene 读取审查

## 结论

通过。P2-1 已完成最小动态 scene 读取闭环，解决 P1 `JdbcSceneConfigRepository` 固定 `order-scene` 的主要扩展限制。

## 变更范围

- `intent-hub-infrastructure/src/main/java/com/intenthub/infrastructure/config/JdbcSceneConfigRepository.java`
- `intent-hub-infrastructure/src/test/java/com/intenthub/infrastructure/config/JdbcSceneConfigRepositoryTest.java`
- `intent-hub-infrastructure/pom.xml`
- README、状态文档、P1/P2 规划文档与 HTML 阅读版

## 行为对照

| 需求/风险 | 当前结果 | 证据 |
| --- | --- | --- |
| 不再固定 `order-scene` | 已完成 | `loadPublishedConfig` 先解析 Envelope metadata，再读取对应发布版本 |
| 多租户/多场景配置读取 | 已完成最小闭环 | 查询均带 `tenant_id + scene_id + version` |
| 显式 scene 选择 | 已完成 | 支持 `metadata.scene_id` 与 `metadata.sceneId` |
| 未指定 scene 的兼容行为 | 已完成 | 读取租户最新 `PUBLISHED` scene |
| 指定 scene 无发布版本 | 已完成 | 回退内置 `order-scene/v1-p1`，避免线上请求直接失败 |
| DDD 分层 | 通过 | 未修改 application/domain 契约；动态读取留在 infrastructure 端口实现 |

## 验证

- `mvn test` 通过，共 20 个测试。
- `JdbcSceneConfigRepositoryTest` 覆盖：
  - metadata 指定 `invoice-scene` 时读取 `invoice-scene/v-invoice`。
  - metadata 未指定 scene 时读取租户最新发布 scene。
  - 指定 scene 无发布版本时回退 `order-scene/v1-p1`。

## 剩余风险

- `scene_routing_rule.match_condition` 的条件表达式尚未规则化解析；P2-1 只完成显式 metadata scene 与发布版本选择。
- 当前 “未指定 scene 取最新发布” 适合作为最小兜底，生产环境后续应补按 source/channel/tenant 的 PRE_ROUTE 规则。
- 尚未做真实 PostgreSQL HTTP 冒烟复验；本轮以仓储级 H2 测试和全量 Maven 测试证明核心行为。

## 下一步

1. P2-2：补 bad case 标注、关闭、导出和训练样本格式。
2. P2-3：补 Prometheus/OpenTelemetry 指标与 Grafana 基础看板。
3. P2-4：把 `scene_routing_rule.match_condition` 固化为可配置条件示例与解析器。

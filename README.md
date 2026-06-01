# Intent Hub 意图中枢

Intent Hub 是一个面向企业业务系统的意图识别与路由中枢。它不是单纯的 NLU 服务，而是把输入接入、意图识别、双阶段路由、受控 LLM 兜底、下游动作适配、配置治理、审计追踪和 bad case 回流放在同一套工程闭环中管理。

当前项目处于 P1 阶段：最小识别闭环已经可编译、可启动、可测试，并完成 PostgreSQL/Flyway 持久化和 Admin 配置版本生命周期 API 的 JDBC 联调。

## 核心原则

### 双阶段路由

先选“怎么认”，再选“谁来干”。

- 前置路由：根据租户、来源、渠道、场景等信息选择识别策略、规则集、模型策略和 LLM 兜底策略。
- 后置路由：在意图、槽位、置信度和风险条件明确后，再选择下游动作，例如 API、MQ、Webhook、MQTT。

### LLM 受控

LLM 是最后一道防线，不是主力识别路径。

- P1 默认以规则识别为主。
- LLM 通过 Spring AI 抽象接入，默认实现预留 Spring AI Alibaba。
- LLM 触发必须受策略控制，包括开关、预算、超时、重试、fallback decision 和审计。

### 防腐层

输出适配层只发指令，不碰业务数据。

- 下游动作只表达“调用什么动作”，不携带 SQL、DB 连接串或业务库写入能力。
- P1 允许的动作类型包括 API、MQ、Webhook、MQTT。
- Intent Hub 不成为业务数据孤岛，也不越界成为业务执行系统。

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 语言与框架 | Java 17 + Spring Boot 4.x |
| 工程结构 | Maven 多模块 + DDD 分层 |
| 数据库 | PostgreSQL 16+ |
| Migration | Flyway |
| 缓存与会话 | Redis 7.x，后续接入 |
| 消息队列 | Kafka，后续接入 |
| 配置中心 | Nacos 3.x，后续用于服务发现和运行时配置分发 |
| LLM 接入 | Spring AI 抽象，P1 默认预留 Spring AI Alibaba Adapter |
| 可观测 | OpenTelemetry + Prometheus + Grafana + Loki/ELK，后续完善 |

## 模块结构

```text
intent-hub-parent
├── intent-hub-domain          # 领域层：核心识别、配置、会话、策略抽象
├── intent-hub-application     # 应用层：用例编排、端口定义、配置治理服务
├── intent-hub-infrastructure  # 基础设施层：JDBC、Flyway、LLM Adapter、内存/JDBC 适配器
├── intent-hub-interfaces      # 接口层：REST API、Admin API、启动类
├── docs                       # 需求、设计、计划、评审与 HTML 阅读版
└── scripts                    # 本地环境检查脚本
```

依赖方向约束：

- `intent-hub-domain` 不依赖 Spring Web、DB、Redis、Kafka、Spring AI Alibaba。
- `intent-hub-application` 只依赖领域模型和端口。
- `intent-hub-infrastructure` 实现持久化、LLM、外部系统等端口。
- `intent-hub-interfaces` 只做 HTTP 入参出参转换和启动装配。

## 已实现能力

### 识别闭环

- `POST /api/v1/intent/recognize`
- Envelope 输入契约
- IntentResult 输出契约
- `SUCCESS`、`CLARIFY`、`REJECTED`、`HANDOFF`、`BLOCKED`、`ASYNC_ACCEPTED` 决策枚举
- 轻量规则识别
- 前置路由和后置路由
- 缺槽澄清
- 异步动作幂等键
- trace、bad case、idempotency 内存与 JDBC 记录

### 配置治理

- `POST /api/v1/admin/config/versions`：创建配置草稿
- `GET /api/v1/admin/config/versions/{version}`：查询配置版本
- `POST /api/v1/admin/config/versions/{version}/validate`：校验配置
- `POST /api/v1/admin/config/versions/{version}/publish`：发布配置
- `POST /api/v1/admin/config/versions/{version}/rollback`：回滚配置
- `GET /api/v1/admin/config/versions/{version}/export`：导出配置
- `POST /api/v1/admin/config/versions/import`：导入配置
- `POST /api/v1/admin/config/versions/{version}/{objectType}`：新增或更新配置对象
- `GET /api/v1/admin/config/versions/{version}/{objectType}`：查询配置对象列表

`objectType` 支持：

- `intents`
- `slots`
- `synonyms`
- `strategies`
- `routes`
- `downstream-actions`

配置对象编辑仅允许发生在 `DRAFT` 版本，已发布版本通过发布/回滚控制线上生效。

### 持久化

Flyway 已落地 P1 必需表：

- `config_version`
- `intent_definition`
- `slot_definition`
- `synonym_mapping`
- `nlu_strategy`
- `scene_routing_rule`
- `downstream_action`
- `recognition_trace`
- `bad_case`
- `idempotency_record`
- `audit_log`

## 本地运行

### 环境要求

- JDK 17+
- Maven 3.9+
- 可选：Docker，用于本地 PostgreSQL 联调

Windows 环境可先执行检查脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-p1-env.ps1
```

### 构建与测试

```bash
mvn test
mvn clean package
```

当前验证结果：`mvn test` 通过，共 15 个测试。

### 启动默认内存模式

```bash
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar
```

健康检查：

```bash
curl http://localhost:8080/api/v1/admin/health
```

当前项目暴露的是 `/api/v1/admin/health`，未暴露 `/actuator/health`。

### 识别请求示例

```bash
curl -X POST http://localhost:8080/api/v1/intent/recognize \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "demo",
    "source": "app",
    "channel": "chat",
    "inputType": "TEXT",
    "text": "取消订单 O20260601001",
    "requestId": "REQ-001"
  }'
```

预期核心结果：

```json
{
  "intentCode": "ORDER_CANCEL",
  "decision": "ASYNC_ACCEPTED",
  "idempotencyKey": "..."
}
```

### PostgreSQL 本地联调

启动 PostgreSQL 示例：

```bash
docker run --name intent-hub-postgres \
  -e POSTGRES_DB=intent_hub \
  -e POSTGRES_USER=intent_hub \
  -e POSTGRES_PASSWORD=intent_hub \
  -p 5432:5432 \
  -d postgres:16-alpine
```

使用 JDBC profile 启动：

```bash
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-jdbc
```

`local-jdbc` 会连接 `localhost:5432/intent_hub`，并启用 Flyway migration。

## 当前 P1 进度

- P0 契约与 DB Schema 已评审通过。
- 技术选型已确认。
- DDD/Maven 多模块骨架已落地。
- P1-1 工程可编译、本地可启动已完成。
- P1-2 自动化验收已完成。
- P1-3 PostgreSQL/Flyway 持久化真实联调已完成。
- P1-4 Admin 配置版本生命周期 API 已完成，并通过 JDBC 联调。
- P1-4 配置对象最小 Upsert/List API 已完成，并通过默认 memory 模式 HTTP 冒烟。
- P1-4 识别链路读取 PostgreSQL 最新 `PUBLISHED` 配置已完成，并通过 `local-jdbc` 冒烟。

P1-4 JDBC 联调结果：

- 创建 `v-jdbc-1`、`v-jdbc-2` 草稿。
- 完成校验、发布、发布新版本、回滚和导出。
- 数据库终态：`v-jdbc-1=PUBLISHED`、`v-jdbc-2=ARCHIVED`。
- 审计结果：`audit_log_count=6`。

## 下一步

- 补齐配置对象删除、批量导入和更细的字段校验。
- 补更多场景的已发布配置读取测试，并让前置路由动态决定 scene。
- 完善可观测指标与 bad case 查询回流。
- 完成 P1 退出评审，明确 P2 准入条件。

## 关键文档

- 总体状态：`docs/codex/v1/status.md`
- 需求文档：`docs/codex/v1/requirements/intent-hub-requirements.md`
- 总体设计：`docs/codex/v1/designs/intent-hub-design.md`
- P0 契约与 Schema：`docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md`
- P1 最小闭环设计：`docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md`
- P1 下一步计划：`docs/codex/v1/plans/intent-hub-p1-next-step-plan.md`
- HTML 阅读版：`docs/codex/v1/intent-hub-lifecycle.html`

# Intent Hub 意图中枢

Intent Hub 是一个面向企业业务系统的意图识别与路由中枢。它把用户输入识别为标准 `IntentResult`，并通过双阶段路由把识别策略和下游动作解耦。

README 保留仓库入口、整体架构摘要和技术方案摘要。服务规划、关键约束、当前进度和详细评审记录请阅读 `docs/codex/v1/` 下的正式文档。

## 整体架构

Intent Hub 采用模块化单体 + 独立模型服务的阶段性架构。在线识别链路按“接入治理 -> 输入适配 -> 前置路由 -> 意图识别 -> 后置路由 -> 输出适配 -> 观测回流”组织。

核心链路：

- 接入治理：鉴权、限流、TraceID 和基础安全拦截。
- 输入适配：将 REST、Webhook、Chat 等来源统一为 `Envelope`。
- 前置路由：先选“怎么认”，按租户、来源、渠道和 scene 选择规则、模型、阈值和 LLM 策略。
- 意图识别：按 Rule -> Model -> LLM 的顺序产生候选；LLM 只作为最后兜底，不承担主识别流量。
- 后置路由：再选“谁来干”，根据意图、槽位、置信度和风险条件选择下游动作。
- 输出适配：作为防腐层只发指令，不直连业务库、不执行 SQL、不持有业务数据。
- 观测回流：记录 trace、bad case、幂等、指标、预算和审计信息，用于评估与优化。

架构图参考：[架构图](docs/assets/architecture/intent-hub-architecture.png)、[数据流向图 v2](docs/assets/architecture/intent-hub-data-flow-v2.png)、[核心交互时序图](docs/assets/architecture/intent-hub-sequence.png)。

## 技术方案

当前工程采用 Java 17 + Spring Boot 4.x + Maven 多模块 + DDD 分层。领域层保留识别、配置和策略抽象；应用层编排用例与端口；基础设施层实现 JDBC、Flyway、LLM、模型服务和内存适配；接口层提供 REST/Admin API。

主要技术选型：

| 方向 | 方案 |
| --- | --- |
| 后端框架 | Java 17 + Spring Boot 4.x |
| 工程结构 | Maven 多模块 + DDD 分层 |
| 数据库 | PostgreSQL 16+ |
| Migration | Flyway |
| 模型服务 | Python/FastAPI 或 Triton，当前提供 FastAPI 示例 |
| 规则识别 | 轻量规则 + 正则/关键词 |
| LLM 接入 | Spring AI 抽象，P1/P2 默认接入 Spring AI Alibaba 分支并保留 HTTP fallback |
| 可观测 | 当前提供 Admin metrics/Prometheus 文本，后续桥接 OpenTelemetry/Micrometer |

详细方案见 [总体设计](docs/codex/v1/designs/intent-hub-design.md) 和 [P1 最小闭环设计](docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md)。

## 快速开始

环境要求：

- JDK 17+
- Maven 3.9+
- 可选：Docker，用于本地 PostgreSQL 联调

构建与测试：

```bash
mvn test
mvn package -DskipTests
```

启动默认内存模式：

```bash
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar
```

健康检查：

```bash
curl http://localhost:8080/api/v1/admin/health
```

识别请求示例：

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

## 常用入口

- 识别接口：`POST /api/v1/intent/recognize`
- 健康检查：`GET /api/v1/admin/health`
- 配置版本：`/api/v1/admin/config/versions`
- 观测查询：`/api/v1/admin/observability`
- 指标快照：`GET /api/v1/admin/metrics`
- Prometheus 文本：`GET /api/v1/admin/metrics/prometheus`
- 告警快照：`GET /api/v1/admin/metrics/alerts`
- LLM 预算查询：`GET /api/v1/admin/llm/budget-usage`

## 文档导航

- [HTML 生命周期阅读版](docs/codex/v1/intent-hub-lifecycle.html)
- [总体状态](docs/codex/v1/status.md)
- [需求文档](docs/codex/v1/requirements/intent-hub-requirements.md)
- [总体设计](docs/codex/v1/designs/intent-hub-design.md)
- [P0 契约与 Schema](docs/codex/v1/designs/intent-hub-p0-contract-schema-design.md)
- [P1 最小闭环设计](docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md)
- [P1 下一步计划](docs/codex/v1/plans/intent-hub-p1-next-step-plan.md)
- [P1 退出评审](docs/codex/v1/trace/intent-hub-p1-exit-review.md)
- [P2-1 动态 scene 读取审查](docs/codex/v1/trace/intent-hub-p2-dynamic-scene-routing-trace.md)
- [P2-2 Bad Case 标注流转审查](docs/codex/v1/trace/intent-hub-p2-bad-case-workflow-trace.md)
- [P2-3 指标观测审查](docs/codex/v1/trace/intent-hub-p2-metrics-observability-trace.md)
- [P2-4 模型服务适配审查](docs/codex/v1/trace/intent-hub-p2-model-service-adapter-trace.md)
- [P2-5 LLM 受控兜底审查](docs/codex/v1/trace/intent-hub-p2-llm-governance-trace.md)
- [FastAPI 模型服务示例](examples/model-service-fastapi/README.md)
- [运维样例总入口](ops/README.md)

## 本地端到端冒烟

模型服务容器与 Intent Hub jar 的完整本地联调可直接运行：

```powershell
.\scripts\smoke-model-service-e2e.ps1
```

脚本会自动打包、启动模型服务容器、启动 Intent Hub、验证 `model_service.healthy=true`、模型服务 `modelVersion` 健康详情和 `ModelRecognitionPolicy` 识别路径，并在结束后清理本地进程与容器。

## 架构图片

- [架构图](docs/assets/architecture/intent-hub-architecture.png)
- [数据流向图 v2](docs/assets/architecture/intent-hub-data-flow-v2.png)
- [核心交互时序图](docs/assets/architecture/intent-hub-sequence.png)

## DashScope 冒烟

DashScope 冒烟只通过环境变量读取密钥，仓库不保存明文凭证。

```powershell
$env:DASHSCOPE_API_KEY="<your-dashscope-api-key>"
$env:DASHSCOPE_CHAT_MODEL="qwen-plus"
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar --spring.profiles.active=dashscope-smoke
```

另开终端执行：

```powershell
.\scripts\dashscope-smoke.ps1 -BaseUrl "http://localhost:8080"
```

## 代码结构

```text
intent-hub-parent
├── intent-hub-domain
├── intent-hub-application
├── intent-hub-infrastructure
├── intent-hub-interfaces
├── docs
└── scripts
```

详细模块职责、依赖方向和演进计划见 [总体设计](docs/codex/v1/designs/intent-hub-design.md) 与 [P1 最小闭环设计](docs/codex/v1/designs/intent-hub-p1-minimal-loop-design.md)。

# Intent Hub 意图中枢

Intent Hub 是一个面向企业业务系统的意图识别与路由中枢。它把用户输入识别为标准 `IntentResult`，并通过双阶段路由把识别策略和下游动作解耦。

README 只作为仓库入口。整体架构、技术方案、服务规划、关键约束和当前进度请阅读 `docs/codex/v1/` 下的正式文档。

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

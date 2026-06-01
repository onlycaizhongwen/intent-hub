# IntentHub 技术选型确认

## 结论

本次技术选型已确认，可作为 P1 工程骨架和后续迭代的默认技术基线。后续需求、设计、计划、实现和验收均以本文为准；如企业内部技术基线、安全规范或运维能力要求发生冲突，应作为变更项重新评审并回写 `status.md`。

## 已确认技术栈

| 层级 | 领域 | 确认选型 | 执行说明 |
| --- | --- | --- | --- |
| 基础设施层 | 后端框架 | Java 17/21 + Spring Boot 4.x | 以 Java LTS 为基础，优先使用虚拟线程、观测、配置绑定等现代 JVM/Spring 能力；若企业生产基线暂未允许 Spring Boot 4.x，可短期降级到 Spring Boot 3.5.x，但代码结构按 4.x 迁移友好设计。 |
| 基础设施层 | 网关 | Apache APISIX 或 Spring Cloud Gateway | APISIX 适合独立网关、K8s、插件治理和多语言入口；Spring Cloud Gateway 适合团队已深度使用 Spring Cloud、希望降低独立网关运维成本的场景。 |
| 基础设施层 | 配置中心 | Nacos 3.x | 用于服务发现、动态配置、配置监听和回滚。P1 配置事实源仍以 Admin Portal + `config_version` 为准，Nacos 用于运行时分发和基础设施配置。 |
| 数据与缓存层 | 数据库 | PostgreSQL 16+ | 作为配置、运行记录、审计、bad case、幂等记录的主库；JSONB 用于 `recognition_trace`、`context_snapshot`、`latency_breakdown` 等半结构化信息。 |
| 数据与缓存层 | 缓存/会话 | Redis 7.x | 用于会话缓存、配置缓存、短期幂等状态、限流辅助和分布式锁。核心审计与幂等最终状态仍落 PostgreSQL。 |
| 中间件层 | 消息队列 | Kafka | 用于训练数据回流、bad case 异步标注、审计事件、异步动作投递和最终一致性回调。P1 可先以同步记录或轻量 topic 起步，避免一开始把闭环复杂化。 |
| 业务与算法层 | 规则引擎 | 轻量规则 + AC 自动机/正则 | 不引入 Drools 作为 P1 默认依赖；高频意图、前置过滤和槽位抽取优先采用可解释、可热更新的轻量规则。 |
| 业务与算法层 | 模型服务 | Python/FastAPI 或 Triton | 模型推理与 Java 业务服务分离部署，通过 HTTP/gRPC 通信。FastAPI 适合 P1/P2 中小规模模型和快速迭代；Triton 适合 GPU、高并发、多模型生产推理。 |
| 业务与算法层 | LLM 接入 | Spring AI 抽象 + Spring AI Alibaba 默认实现 + Provider Adapter | P1 默认采用 Spring AI Alibaba 对接通义/DashScope 等能力，但应用层和领域层保持 Spring AI/自定义端口抽象。LLM 是受控兜底，不是主识别路径，Provider Adapter 统一超时、预算、审计、降级和熔断。 |
| 可观测层 | 可观测 | OpenTelemetry + Prometheus + Grafana + Loki/ELK | OTel 作为跨 Java、Python、网关和 worker 的统一 telemetry 标准；Prometheus/Grafana 做指标，Loki 或 ELK 做日志检索。 |

## 条件化决策

### 网关落点

默认保留 Apache APISIX 和 Spring Cloud Gateway 两个确认候选，但执行时必须二选一：

- 选择 APISIX：团队具备 K8s/独立网关运维能力，需要插件化网关、多协议接入、统一鉴权限流、跨语言服务治理。
- 选择 Spring Cloud Gateway：团队主要是 Spring Cloud 技术栈，期望降低网关运维面，网关逻辑与 Java 服务治理深度绑定。

P1 建议：如果当前还没有成熟独立网关平台，先用 Spring Cloud Gateway 或应用内网关能力跑通闭环；如果企业已有 APISIX 平台，则直接接入 APISIX。

### 模型服务落点

默认采用“Java 业务服务 + 独立模型服务”架构，但模型服务实现按阶段选择：

- 选择 FastAPI：P1/P2、模型数量少、QPS 中等、需要快速迭代、以 CPU 或单模型服务为主。
- 选择 Triton：P3+、GPU 推理、高并发、多模型、多版本并存、需要批处理和推理资源池化。

P1 建议：先以 FastAPI stub 或本地 mock 接口验证识别闭环，不让模型部署复杂度阻塞规则识别、双阶段路由和防腐层验收。

## 与三大核心变更点的关系

- 双阶段路由：网关、Nacos、PostgreSQL 配置版本、Redis 缓存共同支撑“先选怎么认，再选谁来干”。
- LLM 受控：P1 默认实现使用 Spring AI Alibaba，但 Spring AI/自定义端口仍是抽象边界；必须受 `llm_policy`、预算、熔断、fallback decision 和审计约束。
- 防腐层：Kafka/Webhook/API 只承载业务指令或事件，Output Adapter 不直接接触业务数据库。

## 风险与约束

| 风险 | 影响 | 控制措施 |
| --- | --- | --- |
| Spring Boot 4.x 与企业生产基线不一致 | 可能影响依赖兼容、运维镜像和安全扫描 | P1 工程按 Java 17/21 与 Spring Boot 4.x 设计；若内部暂不允许，短期用 Spring Boot 3.5.x 实施并保持迁移路径。 |
| APISIX 运维能力不足 | 网关插件、路由、证书、限流策略可能成为交付阻塞 | P1 执行前确认是否已有 APISIX 平台；没有则先选 Spring Cloud Gateway。 |
| Kafka 运维成本偏高 | 小规模 P1 可能过早承担 broker、topic、消费组和积压治理复杂度 | P1 只接入必要 topic；异步闭环可先以记录 `ASYNC_ACCEPTED` 和幂等表为主，再扩展真实异步消费。 |
| Nacos 与配置版本事实源混淆 | 可能绕过 Admin Portal 和 `config_version`，破坏审计与回滚 | 明确 Admin Portal + PostgreSQL 是意图配置事实源，Nacos 负责运行时分发、基础设施配置和服务发现。 |
| LLM 抽象层被滥用为主链路 | 成本、延迟、稳定性、可解释性失控 | Spring AI Alibaba 只能作为 Provider Adapter 默认实现，必须强制执行 `llm_policy`，默认不进入 P1 主链路。 |
| 模型服务过早上 Triton | P1 交付被 GPU、镜像、模型仓库和推理调优拖慢 | P1 用 FastAPI stub/mock，Triton 作为高并发生产阶段升级项。 |

## 官方参考

- Spring Boot 文档：https://docs.spring.io/spring-boot/documentation.html
- Spring Cloud Gateway 文档：https://docs.spring.io/spring-cloud-gateway/reference/index.html
- Apache APISIX 文档：https://apisix.apache.org/docs/apisix/getting-started/
- Nacos 发布历史：https://nacos.io/en/download/release-history/
- PostgreSQL JSON Types 文档：https://www.postgresql.org/docs/current/datatype-json.html
- Redis 文档：https://redis.io/docs/latest/
- Apache Kafka 文档：https://kafka.apache.org/documentation/
- FastAPI 文档：https://fastapi.tiangolo.com/
- NVIDIA Triton Inference Server 文档：https://docs.nvidia.com/deeplearning/triton-inference-server/user-guide/docs/
- Spring AI 文档：https://docs.spring.io/spring-ai/reference/
- Spring AI Alibaba 文档：https://sca.aliyun.com/en/docs/ai/overview/
- Spring AI Alibaba GitHub：https://github.com/alibaba/spring-ai-alibaba
- OpenTelemetry 文档：https://opentelemetry.io/docs/

## 对 P1 的落地要求

1. P1 工程骨架默认按 Java 17/21 + Spring Boot 4.x + PostgreSQL 16+ + Redis 7.x 建立。
2. 配置治理默认使用 Admin Portal API + PostgreSQL `config_version`，Nacos 用于运行时配置分发和服务发现。
3. 规则识别优先实现轻量规则、正则和 AC 自动机接口，先不引入 Drools。
4. 模型服务先接 FastAPI stub/mock 契约，保留 Triton 部署适配位。
5. Spring AI Alibaba 作为 P1 默认 Provider 实现放在 `intent-hub-infrastructure/llm`，应用层和领域层只依赖 Spring AI/自定义端口抽象；先完成接口隔离、超时、预算、审计、fallback，不把真实 LLM 放入 P1 主链路。
6. 可观测从第一版接口开始埋入 `trace_id`、结构化日志、Prometheus 指标和 OTel trace。

## P1 Maven Module 骨架

P1 采用按领域上下文拆分的 Maven 多模块结构，防止代码变成“大泥球”。默认骨架如下：

```text
intent-hub-parent
├── intent-hub-application          (应用层：编排用例，调用领域服务)
│   └── RecognizeAppService
├── intent-hub-domain               (领域层：核心业务逻辑)
│   ├── recognition                  (识别领域)
│   │   ├── RecognitionTask.java     (聚合根)
│   │   ├── IntentRecognizer.java    (领域服务)
│   │   └── policy                   (策略模式：Rule/Model/Llm)
│   ├── conversation                 (会话领域)
│   │   └── Conversation.java
│   └── config                       (配置领域)
│       └── SceneConfig.java
├── intent-hub-infrastructure        (基础设施层)
│   ├── persistence                  (DB 实现，MyBatis/JPA)
│   ├── cache                        (Redis)
│   ├── mq                           (Kafka/RocketMQ)
│   └── llm                          (Spring AI Alibaba 适配器)
│       └── TongyiLlmAdapter.java
└── intent-hub-interfaces            (用户接口层)
    ├── web                          (REST API)
    └── admin                        (管理后台 API)
```

模块依赖规则：

- `intent-hub-domain` 不依赖 Spring AI Alibaba、数据库、Redis、Kafka 或 Web 框架。
- `intent-hub-application` 编排用例，只依赖领域服务和端口接口。
- `intent-hub-infrastructure/llm` 承载 `TongyiLlmAdapter` 等供应商实现，通过 Spring AI Alibaba 对接通义/DashScope。
- `intent-hub-interfaces` 只负责 REST/Admin 入参出参转换，不承载识别规则和业务状态机。
- LLM Provider 替换只能发生在 infrastructure adapter 层，不允许侵入 domain 的 `policy`。

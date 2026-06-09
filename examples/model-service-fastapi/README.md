# Intent Hub FastAPI 模型服务示例

这是 P2 阶段的最小模型服务样例，用于验证 Intent Hub 的 `HttpModelClientAdapter`。

它只返回识别候选，不返回 SQL、不返回业务动作、不访问业务数据库，符合防腐层边界。

当前样例模拟一个轻量推理服务，响应中会带上 `modelVersion` 和 `threshold`，用于验证模型版本、阈值和候选输出的扩展字段兼容性。Intent Hub Java adapter 当前只消费 `intentCode`、`confidence`、`slots` 和 `explanation`，扩展字段不会改变核心契约。

如果设置 `MODEL_SERVICE_AUTH_TOKEN`，`/recognize` 会要求请求携带 `Authorization: Bearer <token>`；未设置时保持无鉴权本地开发模式。`/health` 不要求鉴权，便于容器和发布前预检判断服务是否存活。

## 启动

### 本地 Python 启动

```bash
cd examples/model-service-fastapi
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 18081
```

### Docker Compose 启动

启动前可先校验容器化配置，不启动容器：

```powershell
.\scripts\validate-model-service-container.ps1
```

如果要执行完整端到端冒烟，脚本会自动打包 Intent Hub、启动模型服务容器、启动 Intent Hub jar、验证健康检查和识别链路，结束后默认清理本地进程与容器：

```powershell
.\scripts\smoke-model-service-e2e.ps1
```

如果要验证模型服务鉴权、`authTokenRef` 和已发布 JDBC 配置读取链路，可执行：

```powershell
.\scripts\smoke-model-service-e2e.ps1 -WithAuth
```

该模式会启动临时 PostgreSQL 16、FastAPI 模型服务容器和 Intent Hub jar，发布一个带 `modelPolicy.authTokenRef` 的专用 scene，并验证无 token 调模型会被拒绝、带 token 的 Intent Hub 识别链路会进入 `ModelRecognitionPolicy`。脚本不会打印 token 明文。

如果要验证文件挂载 Secret 轮换，可执行：

```powershell
.\scripts\smoke-secret-rotation.ps1
```

该模式会让模型服务和 Intent Hub 同时读取同一个挂载文件中的 token：先使用初始 token 完成识别，再改写 token 文件，验证旧 token 直连被拒绝、新 token 直连通过，并确认 Intent Hub 第二次识别仍可通过 `ModelRecognitionPolicy`。

启动模型服务容器：

```bash
cd examples/model-service-fastapi
docker compose up --build -d
```

停止模型服务容器：

```bash
cd examples/model-service-fastapi
docker compose down
```

## 健康检查

```bash
curl http://localhost:18081/health
```

示例响应：

```json
{
  "status": "UP",
  "modelVersion": "fastapi-example-2026-06-08",
  "threshold": 0.7
}
```

## 识别接口

```bash
curl -X POST http://localhost:18081/recognize ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"cancel A100\",\"sceneId\":\"order-scene\"}"
```

示例响应：

```json
{
  "intentCode": "ORDER_CANCEL",
  "confidence": 0.86,
  "slots": {
    "order_id": "A100"
  },
  "explanation": "fastapi example matched order cancel",
  "modelVersion": "fastapi-example-2026-06-08",
  "threshold": 0.7
}
```

Windows 终端如果要测试中文文本，建议使用 PowerShell 原生 JSON：

```powershell
$body = @{ text = "取消订单 A100"; sceneId = "order-scene" } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri http://localhost:18081/recognize -Method Post -ContentType "application/json" -Body $body
```

开启鉴权后的 PowerShell 示例：

```powershell
$env:MODEL_SERVICE_AUTH_TOKEN = "local-dev-token"
$headers = @{ Authorization = "Bearer local-dev-token" }
$body = @{ text = "cancel A100"; sceneId = "order-scene" } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri http://localhost:18081/recognize -Method Post -Headers $headers -ContentType "application/json" -Body $body
```

内置样本：

| 输入示例 | 返回意图 |
| --- | --- |
| `cancel A100` / `取消订单 A100` | `ORDER_CANCEL` |
| `查询订单 A100` | `ORDER_QUERY` |
| `申请退款 A100 破损` | `REFUND_APPLY` |
| `查一下物流 A100` | `LOGISTICS_QUERY` |
| `开普通发票 A100` | `INVOICE_APPLY` |

## Java 服务接入

启动 Intent Hub 时打开模型服务 adapter：

```bash
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar ^
  --intent-hub.model-service.enabled=true ^
  --intent-hub.model-service.base-url=http://localhost:18081 ^
  --intent-hub.model-service.timeout-ms=2000
```

识别顺序仍然是 Rule -> Model -> LLM。规则命中时不会调用模型服务；只有规则未命中时，模型服务才作为 LLM 之前的候选来源。

`modelPolicy.authTokenRef` 只保存引用名，例如 `INTENT_HUB_MODEL_TOKEN`。运行时可通过三种最小后端解析：

```bash
# 1. 环境变量或 JVM system property
set INTENT_HUB_MODEL_TOKEN=local-dev-token

# 2. 文件挂载 Secret，例如 K8s Secret 或 Vault Agent 写入的文件。
#    FastAPI 示例支持 MODEL_SERVICE_AUTH_TOKEN_FILE 动态读取文件内容，便于本地演练轮换。
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar ^
  --intent-hub.secret.file-root=/run/secrets/intent-hub

# 3. 托管配置映射，例如 Nacos/Apollo/Spring Config 完成解密后注入配置
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar ^
  --intent-hub.secret.managed-config.enabled=true ^
  --intent-hub.secret.managed-config.refs.INTENT_HUB_MODEL_TOKEN=local-dev-token
```

托管配置模式只表示 Intent Hub 从运行时配置读取引用映射；密文加解密、权限、审计和轮换应由 Nacos、Apollo、Config Server、Vault Agent 或平台侧完成。

## 边界

- 当前 Docker Compose 只用于本地部署化联调，不是生产部署方案。
- 当前镜像使用 CPU Python/FastAPI 示例，只模拟模型版本与阈值输出，不包含真实模型权重、GPU 调度、模型仓库或 Triton。
- `MODEL_SERVICE_AUTH_TOKEN` 只用于本地鉴权 smoke；生产环境应通过 Secret 后端、挂载文件或托管配置注入提供凭证，不应写入仓库。
- 模型服务仍只能返回识别候选，不返回 SQL、不返回业务动作、不访问业务数据库。

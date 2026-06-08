# Intent Hub FastAPI 模型服务示例

这是 P2 阶段的最小模型服务样例，用于验证 Intent Hub 的 `HttpModelClientAdapter`。

它只返回识别候选，不返回 SQL、不返回业务动作、不访问业务数据库，符合防腐层边界。

当前样例模拟一个轻量推理服务，响应中会带上 `modelVersion` 和 `threshold`，用于验证模型版本、阈值和候选输出的扩展字段兼容性。Intent Hub Java adapter 当前只消费 `intentCode`、`confidence`、`slots` 和 `explanation`，扩展字段不会改变核心契约。

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

## 边界

- 当前 Docker Compose 只用于本地部署化联调，不是生产部署方案。
- 当前镜像使用 CPU Python/FastAPI 示例，只模拟模型版本与阈值输出，不包含真实模型权重、GPU 调度、模型仓库或 Triton。
- 模型服务仍只能返回识别候选，不返回 SQL、不返回业务动作、不访问业务数据库。

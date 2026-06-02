# Intent Hub FastAPI 模型服务示例

这是 P2 阶段的最小模型服务样例，用于验证 Intent Hub 的 `HttpModelClientAdapter`。

它只返回识别候选，不返回 SQL、不返回业务动作、不访问业务数据库，符合防腐层边界。

## 启动

```bash
cd examples/model-service-fastapi
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 18081
```

## 健康检查

```bash
curl http://localhost:18081/health
```

## 识别接口

```bash
curl -X POST http://localhost:18081/recognize ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"cancel A100\",\"sceneId\":\"order-scene\"}"
```

Windows 终端如果要测试中文文本，建议使用 PowerShell 原生 JSON：

```powershell
$body = @{ text = "取消订单 A100"; sceneId = "order-scene" } | ConvertTo-Json -Compress
Invoke-RestMethod -Uri http://localhost:18081/recognize -Method Post -ContentType "application/json" -Body $body
```

## Java 服务接入

启动 Intent Hub 时打开模型服务 adapter：

```bash
java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar ^
  --intent-hub.model-service.enabled=true ^
  --intent-hub.model-service.base-url=http://localhost:18081 ^
  --intent-hub.model-service.timeout-ms=2000
```

识别顺序仍然是 Rule -> Model -> LLM。规则命中时不会调用模型服务；只有规则未命中时，模型服务才作为 LLM 之前的候选来源。

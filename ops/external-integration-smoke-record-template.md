# Intent Hub 外部联调冒烟记录模板

本文档用于记录 P2-6 模型服务与 LLM/DashScope 外部联调证据。每次真实联调建议复制本模板，按环境命名为 `external-integration-smoke-record-<env>-<date>.md`，再填入实际结果。

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 联调环境 |  |
| 租户 |  |
| scene |  |
| Intent Hub 版本/提交 |  |
| Intent Hub 地址 |  |
| 模型服务地址 |  |
| LLM Provider |  |
| 执行时间窗口 |  |
| 执行人 |  |
| 参与人 |  |

## Secret 与准入边界

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 模型服务只配置 `authTokenRef`，不配置明文 token |  |  |
| DashScope/LLM 只配置 Secret 引用或环境变量名 |  |  |
| 数据库、配置导出、trace、bad case 中未出现明文密钥 |  |  |
| Secret 后端类型已记录，例如 env/system property、文件挂载、Vault Agent、K8s Secret、Nacos 加密配置 |  |  |
| Secret 轮换或等效演练策略已确认 |  |  |
| 本次记录未粘贴真实 token、API key、签名密钥 |  |  |

说明：

```text

```

## Preflight

推荐命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/preflight-external-integration.ps1 `
  -IntentHubBaseUrl "http://localhost:8080" `
  -ModelServiceBaseUrl "http://localhost:18081" `
  -ModelAuthTokenRef "INTENT_HUB_MODEL_TOKEN" `
  -DashScopeSecretRef "DASHSCOPE_API_KEY" `
  -SecretFileRoot "/run/secrets/intent-hub" `
  -RequireModelAuth `
  -RequireDashScope
```

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Intent Hub health 返回 `UP` |  |  |
| 模型服务 health 可达 |  |  |
| 模型服务 `authTokenRef` 可解析 |  |  |
| DashScope Secret 引用可解析 |  |  |
| 文件挂载 Secret 场景下，`-SecretFileRoot` 只输出来源类型，不输出文件内容 |  |  |
| 预检输出未打印密钥值 |  |  |

输出摘要：

```text

```

## 模型服务冒烟

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 无 token 直连 `/recognize` 被拒绝，例如 401/403 |  |  |
| 带 token 直连 `/recognize` 返回识别候选 |  |  |
| Intent Hub 通过已发布 `modelPolicy.authTokenRef` 注入 Bearer token |  |  |
| Intent Hub 返回符合预期的 `intentCode`、`decision`、`confidence` |  |  |
| `recognitionPath` 包含 `ModelRecognitionPolicy` |  |  |
| 模型 fallback 指标未异常增加，或异常原因已记录 |  |  |

请求样例：

```json
{
  "tenant_id": "demo-tenant",
  "request_id": "REQ-EXTERNAL-MODEL-001",
  "trace_id": "TRACE-EXTERNAL-MODEL-001",
  "text": "cancel order A100",
  "metadata": {
    "scene_id": "external-model-smoke"
  }
}
```

结果摘要：

```text

```

## DashScope/LLM 冒烟

执行前提：

- 全局 `intent-hub.llm.enabled=true`。
- 全局和 scene 级 `dailyBudget` 均为极小预算。
- scene 级 `llmPolicy.enabled=true`。
- 规则和模型路径故意不命中，确保只验证受控兜底。

推荐流程：

1. 先执行 preflight 并要求 DashScope Secret 可解析。
2. 再执行 `scripts/dashscope-smoke.ps1`。
3. 执行后立即查询 trace、metrics 和预算使用。

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `scripts/dashscope-smoke.ps1` 执行成功 |  |  |
| `recognitionPath` 包含 `LlmRecognizePolicy` |  |  |
| LLM 结果只包含识别候选，不包含业务动作或 SQL |  |  |
| `llm_budget_usage` 记录 confirmed/reserved/pending 符合预期 |  |  |
| `intent_hub_llm_budget_attempts_total` 或相关指标有预期变化 |  |  |
| bad case 未出现敏感明文或密钥 |  |  |

结果摘要：

```text

```

## 指标与审计

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| `GET /api/v1/admin/metrics` 可看到请求量、fallback、预算相关指标 |  |  |
| `GET /api/v1/admin/metrics/prometheus` 可导出对应文本指标 |  |  |
| trace 可按 `trace_id` 查询到识别路径 |  |  |
| 配置版本审计可查询到本次发布或回滚记录 |  |  |
| 幂等记录未重复触发下游动作 |  |  |

证据摘要：

```text

```

## 安全复核

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 日志未打印 token 或 API key |  |  |
| 联调记录未粘贴 token 或 API key |  |  |
| 外部调用失败时是失败关闭，不降级为无鉴权调用 |  |  |
| 关闭 `modelPolicy.enabled` 或 `llmPolicy.enabled` 后可回滚到规则主链路 |  |  |
| 防腐层边界保持：Intent Hub 只发动作指令，不直连业务库、不执行 SQL |  |  |

## 问题记录

| 问题 | 影响 | 处理结果 | Owner |
| --- | --- | --- | --- |
|  |  |  |  |

## 结论

| 项目 | 结论 |
| --- | --- |
| 模型服务外部冒烟是否通过 |  |
| DashScope/LLM 外部冒烟是否通过 |  |
| 是否允许进入下一环境 |  |
| 阻塞项 |  |
| 后续 owner |  |

## 注意事项

- 本模板记录引用名、检查结果和证据摘要，不记录真实密钥值。
- Preflight 只证明联调准入条件满足，不等同于真实识别链路通过。
- 本地带鉴权模型服务 smoke 不等同于真实远端模型服务或生产级 Secret 系统落地。
- 涉及业务数据修复时，只记录已转交业务系统 owner，不在 Intent Hub 内执行修复。

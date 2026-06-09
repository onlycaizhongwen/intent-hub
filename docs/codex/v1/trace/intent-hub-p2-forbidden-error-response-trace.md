# Intent Hub P2-19 统一 403 响应审查

## 审查结论

P2-19 统一 403 响应最小闭环已完成，通过。

本阶段承接 P2-17/P2-18 的配置评审权限模型，把应用层抛出的 `SecurityException` 统一映射为 Admin HTTP API 可消费的 403 JSON 响应，避免权限失败在接口层表现为 500 或非结构化异常。

## 本次交付物

- `ApiErrorResponse`
  - 新增统一错误响应体。
  - 当前字段为 `code`、`message`、`status`。
- `GlobalExceptionHandler`
  - 新增 `@RestControllerAdvice`。
  - 将 `SecurityException` 映射为 HTTP 403。
  - 响应 `code` 固定为 `FORBIDDEN`，`status` 固定为 `403`，`message` 使用应用层明确的权限阻断原因。
- `AdminConfigControllerTest`
  - 新增 `mapsPermissionFailureToForbiddenHttpResponse`。
  - 覆盖错误角色调用 approve 时返回 403 JSON。

## 契约语义

权限失败响应示例：

```json
{
  "code": "FORBIDDEN",
  "message": "approve config version requires role CONFIG_APPROVER",
  "status": 403
}
```

该响应只表达调用方无权执行当前 Admin 配置治理动作，不改变 P2-17 的角色判定规则，也不替代后续真实 IAM、JWT 或网关鉴权。

## 一致性审查

- 与 P2-17 一致：角色门禁仍由应用层 `CONFIG_APPROVER` / `CONFIG_PUBLISHER` 判断，接口层只负责错误映射。
- 与 P2-18 一致：工作台数据面隐藏无权限动作；动作接口若仍被直接调用，则返回结构化 403。
- 与 Admin API 可用性一致：前端可以稳定按 `code=FORBIDDEN` 和 HTTP status 403 做提示、拦截或跳转。
- 与防腐层边界一致：本次只处理 Intent Hub 管理接口错误响应，不触碰业务数据、不连接下游业务系统。

## 验证证据

新增或扩展测试：

- `AdminConfigControllerTest.mapsPermissionFailureToForbiddenHttpResponse`
  - 构造 `REVIEWING` 配置版本。
  - 使用错误角色 `CONFIG_OPERATOR` 调用 approve。
  - 验证 HTTP 403。
  - 验证 JSON 字段 `code=FORBIDDEN`、`status=403`、`message=approve config version requires role CONFIG_APPROVER`。

已执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 32 个测试、基础设施层 59 个测试、接口层 24 个测试，合计 115 个测试。

## 风险与边界

- 当前只统一了 `SecurityException` 到 403；参数校验、状态流转失败、配置校验失败等领域异常仍未纳入统一错误响应模型。
- 错误响应暂不包含 `traceId`、`requestId`、时间戳或错误详情列表，后续接入真实网关和可观测链路时应补齐。
- roles 仍来自请求体/查询参数，真实登录态、IAM、tenant/scene 级权限仍待后续。
- `message` 直接来自异常信息，当前为内部可读英文；后续如果开放给业务运营页面，需要补 i18n 或前端映射。

## 后续建议

- 接入真实登录态/IAM 后，把调用方身份、角色、tenant 和 scene 范围注入应用层，不再信任请求体/查询参数 roles。
- 扩展统一错误响应到参数错误、状态冲突、配置校验失败和资源不存在。
- 在错误响应中增加 `traceId/requestId`，便于 Admin Portal 与后端日志联动排查。
- 将权限错误文案稳定为错误码 + 参数，由前端或网关层统一展示。

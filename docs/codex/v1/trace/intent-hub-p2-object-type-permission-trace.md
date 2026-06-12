# Intent Hub P2-29 配置对象类型级编辑权限审查

## 审查结论

P2-29 配置对象类型级编辑权限最小闭环已完成，通过。

本阶段承接 P2-22 的 `CONFIG_EDITOR` 配置对象编辑权限，在不破坏旧总编辑角色的前提下，补齐对象类型级细分 editor role。目标是让 Admin Portal 或 IAM 可以只授予“路由编辑”“下游动作编辑”等更窄权限，减少配置治理面的误操作半径。

## 本次交付物

- `ConfigPermission`
  - 新增对象类型 editor role：
    - `CONFIG_INTENT_EDITOR`
    - `CONFIG_SLOT_EDITOR`
    - `CONFIG_SYNONYM_EDITOR`
    - `CONFIG_STRATEGY_EDITOR`
    - `CONFIG_ROUTE_EDITOR`
    - `CONFIG_ACTION_EDITOR`
  - 新增 `requireObjectEditor(...)`。
  - 拒绝审计 detail 增加 `alternativeRole` 与 `objectType`。
- `ConfigObjectAppService`
  - upsert、bulk upsert、delete 改为要求 `CONFIG_EDITOR` 或对应对象类型 editor role。
  - 仍保留 `roles == null` 的内部兼容语义。
- `AdminConfigController`
  - 复用现有 roles 透传，不新增 HTTP 字段。
  - header/JWT roles 仍优先于请求体/query。
- 测试
  - 应用层覆盖对象类型角色允许和拒绝。
  - 接口层覆盖 header 传入对象类型角色、错误类型角色返回结构化 403。

## 权限语义

旧总权限继续生效：

```text
CONFIG_EDITOR
CONFIG_EDITOR:tenant:scene
CONFIG_EDITOR:tenant:*
CONFIG_EDITOR:*:scene
CONFIG_EDITOR:*:*
```

对象类型权限按同一 scoped role 语义生效：

```text
CONFIG_INTENT_EDITOR[:tenant:scene]
CONFIG_SLOT_EDITOR[:tenant:scene]
CONFIG_SYNONYM_EDITOR[:tenant:scene]
CONFIG_STRATEGY_EDITOR[:tenant:scene]
CONFIG_ROUTE_EDITOR[:tenant:scene]
CONFIG_ACTION_EDITOR[:tenant:scene]
```

映射关系：

| 对象类型 | 细分角色 |
| --- | --- |
| `INTENT` | `CONFIG_INTENT_EDITOR` |
| `SLOT` | `CONFIG_SLOT_EDITOR` |
| `SYNONYM` | `CONFIG_SYNONYM_EDITOR` |
| `STRATEGY` | `CONFIG_STRATEGY_EDITOR` |
| `ROUTE` | `CONFIG_ROUTE_EDITOR` |
| `DOWNSTREAM_ACTION` | `CONFIG_ACTION_EDITOR` |

## 一致性审查

- 与 P2-21 一致：继续复用 `ROLE:tenant:scene`、`ROLE:tenant:*`、`ROLE:*:scene`、`ROLE:*:*` scoped role 语义。
- 与 P2-22 一致：`CONFIG_EDITOR` 总编辑角色继续兼容，现有脚本和调用方不需要立即迁移。
- 与 P2-24/P2-25 一致：权限拒绝仍写入 `CONFIG_PERMISSION_DENIED`，并继续进入权限拒绝指标和告警。
- 与 P2-26/P2-27 一致：对象类型角色可以来自请求体、header 或 JWT claims，JWT/header 可信上下文仍优先。

## 验证证据

已执行定向测试：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-interfaces -am '-Dtest=ConfigVersionAppServiceTest,AdminConfigControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：通过。应用层 `ConfigVersionAppServiceTest` 24 个测试通过，接口层 `AdminConfigControllerTest` 20 个测试通过。

已执行三层回归：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' -pl intent-hub-application,intent-hub-infrastructure,intent-hub-interfaces -am test
```

结果：通过。应用层 37 个测试、基础设施层 61 个测试、接口层 39 个测试，合计 137 个测试。

## 风险与边界

- 当前只做到对象类型级权限，没有做到字段级权限，例如只允许改 `llmPolicy.dailyBudget` 或只允许改 `routeStage=POST`。
- 当前角色仍是字符串角色模型，完整 IAM/OIDC/JWKS 策略源仍待后续接入。
- 当前 `CONFIG_ACTION_EDITOR` 覆盖所有下游动作配置；若未来需要更细，可继续拆 `CONFIG_API_ACTION_EDITOR`、`CONFIG_MQ_ACTION_EDITOR`。
- 当前只限制写入操作；读取仍由 `CONFIG_VIEWER` 或继承读权限控制。

## 后续建议

- 在 IAM/OIDC/JWKS 接入时，把对象类型角色作为标准 claim 或 group 映射。
- 在 Admin Portal 中按对象类型隐藏不可编辑按钮，减少后端 403 噪音。
- 继续补结构化 review history，把对象类型权限、审批意见和配置 diff 串成可审计轨迹。

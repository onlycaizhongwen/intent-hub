# P1-4 Admin Portal 最小配置治理 API 过程

## 恢复胶囊

- 任务需求：继续推进 P1-4，落地 Admin Portal 最小配置治理 API，覆盖草稿、校验、发布、回滚、导入导出和审计。
- 关键决策：P1 先以 API 闭环代替完整 UI；事实源为 PostgreSQL `config_version` + 配置表，Nacos/GitOps 后置；保持 DDD 分层，接口层只做入参出参。
- 当前阶段：P1-4 配置对象最小 CRUD 已完成。
- 已完成产物：任务记录、配置版本应用服务、配置对象应用服务、内存/JDBC 适配器、Admin REST API、自动化测试、默认 memory 模式 API 冒烟、`local-jdbc` PostgreSQL 审计写入联调。
- 剩余工作：补配置对象删除/批量导入细节，并将识别配置读取切到已发布版本。
- 重要发现：P1-3 已完成真实 PostgreSQL/Flyway 联调，现有 `config_version` 与 `audit_log` 可支撑最小治理闭环。

## 步骤列表

- [v] 注册任务记录。
- [v] 读取现有 schema 与接口风格。
  - 当前产物：确认 P1-4 可复用 `config_version` 与 `audit_log`。
  - 下一步：实现最小 API 与测试。
- [v] 实现配置版本应用服务与端口。
  - 当前产物：`ConfigVersionAppService`、`ConfigVersionPort`、`AuditLogPort`、`ConfigBundle`、`ConfigValidationResult`。
- [v] 实现 JDBC/内存适配器。
  - 当前产物：`InMemoryConfigGovernanceRepository`、`JdbcConfigVersionRepository`、`JdbcAuditLogRepository`。
- [v] 实现 Admin REST API。
  - 当前产物：`AdminConfigController` 与草稿、校验、发布、回滚、导入导出请求模型。
- [v] 增加自动化测试并运行构建。
  - 验证结果：`mvn test` 通过，共 13 个测试。
  - 验证结果：`mvn clean package` 通过，共 13 个测试。
  - 冒烟结果：默认 memory 模式 jar 启动后，草稿创建、校验、发布、导出接口可用。
- [v] 同步 P1 文档与 HTML。
- [v] 使用 `local-jdbc` profile 完成 Admin API PostgreSQL 联调。
  - 验证结果：创建 `v-jdbc-1`、`v-jdbc-2` 草稿，完成校验、发布、再次发布、回滚和导出。
  - 数据库结果：`v-jdbc-1=PUBLISHED`，`v-jdbc-2=ARCHIVED`。
  - 审计结果：`audit_log` 写入 `CONFIG_DRAFT_CREATED`、`CONFIG_PUBLISHED`、`CONFIG_ROLLED_BACK`、`CONFIG_EXPORTED`，共 6 条。
  - 口径差异：当前可用健康检查为 `GET /api/v1/admin/health`；`/actuator/health` 未暴露，返回 404。
- [v] 实现配置对象最小 CRUD。
  - 当前产物：`ConfigObjectAppService`、`ConfigObjectPort`、`ConfigObjectType`、`JdbcConfigObjectRepository`、`ConfigObjectRequest`。
  - 支持对象：意图、槽位、同义词、策略、前置/后置路由、下游动作。
  - 支持接口：`POST/GET /api/v1/admin/config/versions/{version}/{objectType}`。
  - 约束：仅允许编辑 `DRAFT` 版本，发布版本不可直接修改。
  - 验证结果：`mvn test` 通过，共 15 个测试；默认 memory 模式 HTTP 冒烟已验证 intent、slot、downstream-action 写入并进入 export bundle。

## 研究发现

- `config_version` 已有 tenant、scene、version、status、description、created_by、published_at 和唯一约束。
- `audit_log` 已有 action、target_type、target_id、detail，可用于发布、回滚、导入导出审计。
- P1 最小版本治理可先不实现完整配置对象 UI，先提供 JSON 导入导出与版本生命周期。
- 发布逻辑应只归档同租户同场景下旧的 `PUBLISHED` 版本，不能把尚未发布的草稿提前归档。
- 当前第一阶段导入接口只建立版本草稿并保留 bundle 边界；细粒度写入意图、槽位、路由、动作配置表留到后续配置对象 CRUD。
- `local-jdbc` 联调证明配置版本生命周期 API 已真实写入 PostgreSQL：`config_version_count=2`、`audit_log_count=6`。
- 配置对象最小 CRUD 采用通用对象类型入口，先满足 P1 Admin API 闭环；后续完整后台可按对象拆更细的专用接口和表单校验。
- 发布后只读是配置治理底线：对象编辑必须发生在 `DRAFT`，通过发布/回滚改变线上版本。

## 错误记录

- 测试首次暴露发布 v1 时会把 v2 草稿归档；已修正为只归档旧 `PUBLISHED` 版本。
- 配置对象测试首次编译失败，原因是接口层测试缺少 `java.util.List` import；已补齐并复验通过。

# P2-1 动态 scene 路由与多场景配置读取

## 恢复胶囊

- 任务需求：替换 P1 中 `JdbcSceneConfigRepository` 固定读取 `order-scene` 的实现，支持多租户/多场景下根据 Envelope 选择已发布配置。
- 关键决策：P2-1 先做最小闭环，不改变 application/domain 契约；scene 解析放在 infrastructure 的 `SceneConfigPort` 实现内，后续 P2-2 再扩展基于 `scene_routing_rule` 的复杂条件表达式。
- 当前阶段：实现与文档同步已完成，待提交和推送。
- 已完成产物：动态 scene 读取实现、仓储级测试、README/status/design/plan/HTML/trace 文档同步。
- 剩余工作：提交并尝试推送。
- 重要发现：P1-6 本地提交 `1a04b19` 已存在，但 GitHub 443 超时导致远端推送暂未确认。

## 步骤列表

- [v] 建立 P2-1 任务记录。
- [v] 实现动态 scene 读取。
  - 当前产物：`JdbcSceneConfigRepository` 已支持 metadata scene、租户最新发布 scene 和内置回退。
  - 下一步：无。
  - 涉及文件：`intent-hub-infrastructure/src/main/java/com/intenthub/infrastructure/config/JdbcSceneConfigRepository.java`
- [v] 同步文档与 HTML。
- [v] 运行测试。
- [~] 提交并推送。
  - 当前产物：`mvn test` 通过，共 20 个测试。
  - 下一步：执行 git status、提交、推送。

## 研究发现

- `SceneConfigPort.loadPublishedConfig(Envelope envelope)` 已经携带完整 Envelope，可在不改应用层契约的前提下完成最小 scene 解析。
- 当前 JDBC 实现固定 `findPublishedVersion(envelope.tenantId(), "order-scene")`，并在所有配置表查询中固定 `order-scene`。
- `Envelope.metadata` 是 `Map<String, String>`，适合先约定 `scene_id` / `sceneId` 作为 P2-1 显式场景选择入口。
- `mvn test` 已通过，共 20 个测试；新增 `JdbcSceneConfigRepositoryTest` 覆盖三条 P2-1 关键路径。

## 错误记录

- PowerShell 下 `intent-hub-*` 作为路径 glob 会触发“文件名、目录名或卷标语法不正确”，后续使用 `rg ... .` 或明确目录。
- `git push origin main` 当前失败：`Failed to connect to github.com port 443: Timed out`。

# P1 构建与接口验收过程

## 恢复胶囊

- 任务需求：继续推进 P1 最小识别闭环，验证工程可编译、本地可启动和接口最小验收。
- 关键决策：使用已安装的 JDK 17 和本机 Maven 3.9.7；启动接口模块时需要使用 Maven reactor `-am` 一并构建依赖模块。
- 当前阶段：已完成。
- 已完成产物：`mvn clean package` 全 Reactor 构建成功；打包 jar 可启动；核心 REST 接口手工冒烟通过。
- 剩余工作：进入 P1-2 自动化验收测试固化。
- 重要发现：直接执行 `mvn -pl intent-hub-interfaces spring-boot:run` 会因本地仓库缺少 sibling module artifact 而失败；`-am` 会让 Spring Boot run 作用到父 POM。当前可靠启动方式是先 `mvn clean package`，再 `java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar`。

## 步骤列表

- [v] 确认上一次 Maven 构建结果。
  - 结果：全 Reactor `BUILD SUCCESS`，耗时 01:25。
- [v] 记录单模块启动失败原因。
  - 结果：`intent-hub-application` 和 `intent-hub-infrastructure` sibling artifacts absent；需使用 `-am`。
- [v] 启动接口模块并执行 HTTP 冒烟。
  - 结果：`java -jar intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar` 启动成功，Tomcat 监听 8080。
  - 验证：健康检查 `UP`，识别接口覆盖 `SUCCESS`、`ASYNC_ACCEPTED`、`CLARIFY`、`REJECTED`。
- [v] 同步 P1 验证结果到文档。
  - 结果：已更新 status、P1 design、P1 plan、HTML。
- [v] 关闭本地服务。
  - 结果：Spring Boot graceful shutdown complete。

## 研究发现

- JDK 17 已安装在 `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`。
- Maven 3.9.7 已存在于 `C:\Users\caizw01\.m2\wrapper\dists\apache-maven-3.9.7-bin\3k9n615lchs6mp84v355m633uo\apache-maven-3.9.7\bin\mvn.cmd`。
- 当前 PowerShell 会话需要临时设置 `JAVA_HOME` 和 `Path`。
- `查一下订单` 返回 `ORDER_QUERY/SUCCESS`。
- `取消订单 O20260601001` 返回 `ORDER_CANCEL/ASYNC_ACCEPTED`，并生成幂等键。
- `帮我取消订单` 返回 `ORDER_CANCEL/CLARIFY`，且不生成幂等键。
- `给我讲个笑话` 返回 `UNKNOWN/REJECTED`。
- 重复提交 `REQ-P1-002` 返回相同幂等键。

## 错误记录

- `mvn -pl intent-hub-interfaces spring-boot:run` 失败：只构建目标模块时，Maven 无法解析未安装到本地仓库的 sibling module artifacts。
- `mvn -pl intent-hub-interfaces -am spring-boot:run` 失败：Spring Boot Maven Plugin 会先作用到父 POM，父 POM 没有 main class。

# AGENTS.md

开始任何开发工作之前，必须先完整阅读 `.code/` 下的四份规范文档，再按任务类型翻 `.code/skills/` 里对应的手册。这份要求适用于所有 AI 工具（Qoder / Codex / Cursor / Claude Code 等），不因工具而异。

本仓库是**后端独立包**，不含任何前端代码（见 `docs/ARCHITECTURE.md` 第 3 行的声明）。三个前端在同级目录 `E:\Project\vidora\vidora-web`、`vidora-mobile`、`vidora-admin`，各有自己的 `.code/`。**不要跨出本目录改东西**。

## 项目一句话

vidora 视频平台的 **后端**：Spring Cloud 微服务单体仓库（Java 25 + Spring Boot 4.0.7 + Spring Cloud 2025.1.3 + MyBatis-Plus + Nacos + Redis + RocketMQ + MinIO），9 个可运行服务 = gateway + auth + system + video + content + interact + message + search + recommend，经网关 `/api/**` 同时服务 web / mobile / admin 三端；**接口契约的唯一权威源就是这里的 Controller**。

根包名是 `org.tiglor`（Maven groupId 同为 `org.tiglor`），不是 `vidora`。这是既有事实，**不要「顺手改正」**。

## 构建与验证命令

本机的 JDK 和 Maven 仓库都不在默认位置，命令必须照抄（已在本仓库实测通过）：

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"

# 单模块编译（推荐，改动小、反馈快）
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile

# 全仓编译
mvn -o -Dmaven.legacyLocalRepo=true compile
```

三个易错点：

| 项 | 现状 | 后果 |
| --- | --- | --- |
| 默认 `java -version` | `1.8.0_191` | 不设 `JAVA_HOME` 直接编译必挂（工程要求 release 25） |
| `-o` + `-Dmaven.legacyLocalRepo=true` | 依赖离线缓存在 `F:\repository` | 缺任一个会走网络解析并失败 |
| Maven Wrapper | 不存在 | 只有全局 `mvn`（`D:/apache-maven-3.9.6`） |

`compile` 是本仓库**唯一可靠的机械门禁**。测试基础设施不完整，详见 `.code/agent-rules.md` 第三节。

## 目录速览

| 路径 | 职责 |
| --- | --- |
| `pom.xml` | 父 POM：版本托管全在这里，子模块不写版本号 |
| `vidora-gateway/` | WebFlux 网关（8080）：路由表 + `GatewayAuthFilter` + `TraceIdGlobalFilter` |
| `vidora-auth/` | auth-service（8101）：注册、登录签发 JWT、`/profile/**` 用户自助偏好 |
| `vidora-common/common-core/` | `ApiResult` / `BizException` / `ResultCode` / `BaseEntity` / `JwtUtil` / 安全头与 `UserContext` / `TraceIds` / `logback/logback-base.xml` |
| `vidora-common/common-user/` | RBAC 实体与 Mapper（User / Role / Menu / Client / 关联表），auth 与 system **共用** `user_service` 库 |
| `vidora-common/common-log/` | `@OperLog` + `BusinessType` + `OperLogAspect` + `AuditJson`（脱敏）+ `FileLogSink` / `RemoteLogSink` |
| `vidora-common/common-redis/` | Spring Cache 装配 + `CacheNames` 常量表 |
| `vidora-common/common-test/` | 只有一个 `MpTestSupport.java`，放在 **main** 源码目录；只有 `vidora-interact`、`vidora-message` 两个 pom 以 test scope 引它 |
| `vidora-api/vidora-api-{服务名}/` | **服务间调用契约**：interface + DTO 由**被调用方**拥有并发布，不引 `spring-web`（保持传输层中立）。新增子模块要同时登记 `vidora-api/pom.xml` 的 `<modules>` 与根 `pom.xml` 的 `dependencyManagement`；判据与 Dubbo 3 的触发条件见 `docs/ARCHITECTURE.md` 6.2.1 |
| `vidora-integration/` | 第三方适配层（防腐层）：平台**外部**系统的客户端与模型转换。内部服务间调用不放这里（放 `vidora-api`）；尚无真实调用方，别为了填模块造假客户端 |
| `vidora-modules/vidora-{system,video,content,interact,message,search,recommend}/` | 7 个业务服务，端口 8102–8108 |
| `SQL/` | `01_user_service.sql` … `09_sys_log.sql`，**由用户手工执行**，AI 不得自行跑 |
| `deploy/docker-compose.yml` | 基础设施 + 9 服务编排；`../SQL` 只在 MySQL **首次初始化**时生效 |
| `docs/ARCHITECTURE.md` | 架构落地文档 + Boot 4 / JDK 25 迁移踩坑表 |

## .code 导览

| 文件 | 讲什么 | 什么时候翻 |
| --- | --- | --- |
| `.code/README.md` | 索引：四份规范各讲什么、阅读顺序、任务→skill 对照、红线清单 | 第一次接触本仓库 |
| `.code/agent-rules.md` | AI 行为规范：动手前必读清单、不许凭记忆断言、验证命令原文、禁止动作、需先问用户的改动、交付口径 | 每次动手前 |
| `.code/coding-standards.md` | 编码规范：模块与包布局、命名、Controller/Service/Mapper/Entity/DTO/VO 分层归属、响应包装、异常、yml 配置、日志、注释 | 写代码时 |
| `.code/requirements.md` | 需求规范：后端拥有什么、加能力的归属判定、四段式需求模板、契约由 Controller + DTO 定义、完成定义 | 接到需求或「这该哪端做」有疑问时 |
| `.code/skills/add-a-crud-endpoint.md` | 新增一套 CRUD 接口（entity → mapper → service → controller → SQL → 网关路由） | 要加资源域时 |
| `.code/skills/add-admin-only-endpoint.md` | 管理端专用接口：**四处同步**，漏一处等于把接口裸奔给用户 | 要加后台接口时（最易出事） |
| `.code/skills/add-an-audited-write.md` | 给写操作补 `@OperLog` + `BusinessType`，以及哪些接口**不该**打审计 | 关键写操作需要追责时 |
| `.code/skills/add-a-db-migration.md` | `SQL/NN_*.sql` 编号、幂等写法、为什么不自动生效 | 要改表或补种子数据时 |
| `.code/skills/verify-a-backend-change.md` | 编译 + 起服务 + curl 三段式验证 | 交付前，无论改动大小 |

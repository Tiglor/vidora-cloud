# vidora-cloud · AI 协作规范

**开始任何开发工作之前，必须先完整读完本目录下的四份规范（`agent-rules.md` / `coding-standards.md` / `requirements.md` 与本文件），再按下面的任务映射翻 `.code/skills/` 里对应的手册。** 这份要求适用于所有 AI 工具（Qoder / Codex / Cursor / Claude Code 等），不因工具而异。

本仓库**刻意不放根 `AGENTS.md`**：入口只留这一处，避免两份文件各说一套、改一处忘一处。工具如果不自动读到这里，就把本文件的路径显式喂给它。

## 项目一句话

vidora 视频平台的 **后端独立包**：Spring Cloud 微服务单体仓库（Java 25 + Spring Boot 4.0.7 + Spring Cloud 2025.1.3 + MyBatis-Plus 3.5.17 + Nacos 3.2.4 + Redis + RocketMQ 5.3.2 + MinIO + Dubbo 3.3.6），9 个可运行服务 = gateway(8080) + auth(8101) + system(8108) + video(8102) + content(8103) + interact(8104) + message(8105) + search(8106) + recommend(8107)，经网关 `/api/**` 同时服务 web / mobile / admin 三端；**接口契约的唯一权威源就是这里的 Controller**。

本仓库不含前端代码。三个前端在同级目录 `E:\Project\vidora\vidora-web`、`vidora-mobile`、`vidora-admin`，各有自己的 `.code/`。**不要跨出本目录改东西**。

根包名是 `org.tiglor`（Maven groupId 同为 `org.tiglor`），不是 `vidora`。这是既有事实，**不要「顺手改正」**。

架构全量落地文档见 [ARCHITECTURE.md](./ARCHITECTURE.md)（Boot 4 / JDK 25 迁移踩坑表、微服务拆分、每个服务的详细设计、待完成清单）。

## 构建与验证命令

本机的 JDK 和 Maven 仓库都不在默认位置，命令必须照抄（已在本仓库实测通过）：

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"

# 单模块编译（推荐，改动小、反馈快）
mvn -o -q -Dmaven.legacyLocalRepo=true -pl <模块路径> -am compile

# 全仓编译
mvn -o -Dmaven.legacyLocalRepo=true compile
```

三个易错点：

| 项 | 现状 | 后果 |
| --- | --- | --- |
| 默认 `java -version` | `1.8.0_191` | 不设 `JAVA_HOME` 直接编译必挂（工程要求 release 25） |
| `-o` + `-Dmaven.legacyLocalRepo=true` | 依赖离线缓存在 `F:\repository` | 缺任一个会走网络解析并失败 |
| Maven Wrapper | 不存在 | 只有全局 `mvn`（`D:/apache-maven-3.9.6`） |

`compile` 是本仓库**唯一可靠的机械门禁**。测试基础设施不完整，详见 `agent-rules.md` 第三节。

## 目录速览

| 路径 | 职责 |
| --- | --- |
| `pom.xml` | 父 POM：版本托管全在这里，子模块不写版本号 |
| `vidora-gateway/` | WebFlux 网关（8080）：路由表 + `GatewayAuthFilter` + `TraceIdGlobalFilter` |
| `vidora-auth/` | auth-service（8101）：注册、登录签发 JWT、`/profile/**` 用户自助偏好。**不连库**——pom 里没有 mybatis-plus starter 也没有 mysql 驱动，用户与客户端全部经 Dubbo 向 system-service 取 |
| `vidora-common/common-core/` | `ApiResult` / `BizException` / `ResultCode` / `BaseEntity` / `JwtUtil` / 安全头与 `UserContext` / `TraceIds` / `logback/logback-base.xml` |
| `vidora-common/common-log/` | `@OperLog` + `BusinessType` + `OperLogAspect` + `AuditJson`（脱敏）+ `FileLogSink` / `RemoteLogSink` |
| `vidora-common/common-redis/` | Spring Cache 装配 + `CacheNames` 常量表 |
| `vidora-common/common-test/` | 只有一个 `MpTestSupport.java`，放在 **main** 源码目录；只有 `vidora-interact`、`vidora-message` 两个 pom 以 test scope 引它 |
| `vidora-api/vidora-api-{服务名}/` | **服务间调用契约**：interface + DTO 由**被调用方**拥有并发布，不引 `spring-web`（保持传输层中立）。HTTP 侧是 `XxxApi` + `ApiResult`，RPC 侧是 `RemoteXxxApi` + 裸 DTO，**两套并存且不合并**。新增子模块要同时登记 `vidora-api/pom.xml` 的 `<modules>` 与根 `pom.xml` 的 `dependencyManagement`；判据见 `ARCHITECTURE.md` 6.2.1 |
| `vidora-integration/` | 第三方适配层（防腐层）：平台**外部**系统的客户端与模型转换。内部服务间调用不放这里（放 `vidora-api`）；尚无真实调用方，别为了填模块造假客户端 |
| `vidora-modules/vidora-{system,video,content,interact,message,search,recommend}/` | 7 个业务服务，端口 8102–8108。`vidora-system` 是 RBAC 实体与 Mapper 的**唯一归属**（原 `common-user` 已删），并以 `@DubboService` 在 20880 暴露 `RemoteUserApi` / `RemoteClientApi` |
| `SQL/` | **一个业务库、一个文件**：`vidora_cloud.sql` 是 `SQL/` 下唯一的文件，库名 `vidora_cloud`、33 张表按业务功能分 8 节，只描述目标态（建库 + 全部建表 + 种子），不保留迁移脚本。建库建表是普通 DDL（不带 `IF NOT EXISTS`，已有库上重跑直接报错），只有种子用 `INSERT IGNORE`；表与列都必须带 `COMMENT`。**默认由用户手工执行**，AI 不自行跑，除非用户在当前会话点名（见 `agent-rules.md` 第四节） |
| `deploy/docker-compose.yml` | 基础设施 + 9 服务编排；`../SQL` 只在 MySQL **首次初始化**时生效 |
| `ARCHITECTURE.md` | 架构落地文档 + Boot 4 / JDK 25 迁移踩坑表 |

---

## .code 规范目录

这个目录是 vidora-cloud 的 AI 协作规范。它存在的理由：这个项目会由不同的 AI 工具接手，而每个工具都不会天然知道本仓库的约定、坑和验证手段。所有条文的目的是让一个陌生 AI 在读完后能做出**符合本仓库既有风格**的改动，而不是按「业界常识」重写一遍。

三个前端（`vidora-web` / `vidora-mobile` / `vidora-admin`）各有同构的 `.code/` 目录，四端口径一致：**契约在这里定义，前端只是消费者**。

规范全部来自代码现场，不来自想象。每一条都能指到具体文件；如果你发现某条规范和代码对不上，**以代码为准，然后来改这份规范**。

## 四份规范各讲什么

| 文件 | 管什么 | 典型触发问题 |
| --- | --- | --- |
| `agent-rules.md` | AI 的行为纪律：动手前读什么、真实可跑的验证命令原文、什么操作必须先问、交付时怎么区分已验证与未验证 | 「我能不能直接 mvn install」「这 SQL 我能执行吗」「这话我能说成已验证吗」 |
| `coding-standards.md` | 代码怎么写才像本仓库作者写的：模块与包布局、命名、五层分层归属、响应包装、异常语义、yml 约定、日志、注释纪律、SQL 与建表约定（第 12 节） | 「这个字段该放 entity 还是 VO」「分页返回 Page 还是 PageResult」「新配置写哪个 yml」「新表建在哪个 SQL 文件」 |
| `requirements.md` | 需求侧边界：后端负责什么、三端各自拿哪些出口、加一个能力该落在哪个服务、需求四段式怎么写、什么算完成 | 「前端要我加个字段合理吗」「这逻辑该服务算还是前端算」 |
| `skills/*.md` | 可执行手册：一类改动的具体步骤、要动哪几个文件、每步怎么验证、已知坑（见下方任务映射表） | 「加一套 CRUD 要从哪一步开始」「管理端接口到底要同步几处」 |

## 建议阅读顺序

1. `agent-rules.md` —— 先搞清楚本仓库的编译到底怎么跑起来（它和「常识」差得很远）、什么动作会造成不可逆后果。
2. `requirements.md` —— 再搞清楚职责边界：9 个服务各管一片，网关和 system-service 有两处重复的权限判定，搞不清就会把接口暴露错。
3. `coding-standards.md` —— 最后落到怎么写才和现有 29 个 Mapper、7 个服务的风格一致。

之后的日常开发不必重读全文：所有入口信息都在本文件，按下面的任务映射只翻需要的那几节。

## 什么任务翻哪份 skill

| 任务 | 手册 |
| --- | --- |
| 新增一套 CRUD 接口（分类、标签那种） | `skills/add-a-crud-endpoint.md` |
| 新增**只有管理端能用**的接口 | `skills/add-admin-only-endpoint.md`（四处同步清单，漏一处 = 用户 token 可读后台数据） |
| 给一个写操作补审计记录 | `skills/add-an-audited-write.md` |
| 加表 / 加列 / 补种子数据 | `skills/add-a-db-migration.md` |
| 改完任何东西，准备说「done」之前 | `skills/verify-a-backend-change.md` |
| 只是改逻辑、不加接口、不改表 | 没有专门手册，按 `agent-rules.md` 第三节跑编译 + 按下述红线自查 |

skill 是「可执行步骤清单」：改哪些文件、什么顺序、每步怎么验证、常见坑。照着做即可，不必通读全仓。

## 一句话红线（详见 agent-rules.md）

- **不许凭记忆断言代码现状**：包名、版本号、字段名、路径、枚举数字一律现场 Read/Grep。
- **编译命令必须带 `-o -Dmaven.legacyLocalRepo=true` 且显式设 `JAVA_HOME` 指向 JDK 25**；默认 `java` 是 1.8。
- **`SQL/vidora_cloud.sql` 由用户手工执行**。AI 不得连库跑 DDL/DML，不得因为「docker-compose 里挂了 SQL 目录」就以为它会自动生效（只在 MySQL 首次初始化、数据卷为空时执行一次）。
- **SQL 一个业务库一个文件、按业务分节、表和列必须带注释**：`vidora_cloud.sql` 是 `SQL/` 下唯一的文件（库 `vidora_cloud`，33 表分 8 节），只描述目标态（含全部建表与种子），**不保留迁移脚本**，也不给单张表另开文件。表级 `COMMENT='...'` 与每一列的 `COMMENT '...'` 在建表时就要写全（`sys_client` 漏过四列，空了很久才回填）。建库建表用普通 DDL，**不写 `IF NOT EXISTS`**：已有库上重跑要当场报错停下，而不是静默跳过让人以为改动生效了。所以改结构之后存量库只能重建（`DROP DATABASE vidora_cloud;` 后重跑）或按交付里给出的等价 `ALTER` 手工执行。详见 `coding-standards.md` 第 12 节与 `skills/add-a-db-migration.md`。
- **管理端接口要同时改两处**：网关路由 + `GatewayAuthFilter.ADMIN_PATH_PREFIXES`。只加路由，普通用户 token 就能访问。
- **Boot 4 / MP 3.5.17 的三个改名陷阱**：`spring-boot-starter-aop` → `spring-boot-starter-aspectj`；`IService`/`ServiceImpl` → `com.baomidou.mybatisplus.spring.service[.impl]`；容器内 ObjectMapper 是 Jackson 3（`tools.jackson`），本工程统一用 Jackson 2（`com.fasterxml`）。
- **行尾混用（CRLF/LF 逐文件不同）**：保留每个文件原有行尾，禁止整文件格式化；脚本改完要做字节级核对。
- **不要自己造测试并声称「测试通过」**：本仓库的测试基础设施不完整（见 `agent-rules.md` 第三节的事实描述）。
- 交付说明必须分成「真跑过的命令 + 结果」和「未验证项」两块。

## 这个目录本身也可以被修改

规范过期比没规范更危险。当你发现：

- 某条规范与实际代码冲突；
- 某个坑反复被不同人踩（包括你自己）；
- 某个手册的步骤缺了导致返工；

就直接改对应文件，并在改动处留下能让下一个人看懂为什么改的痕迹。但**改 `.code/` 下的内容属于新增/修改受控文档，先征求用户同意**。

# .code 规范目录

这个目录是 vidora-cloud（后端）的 AI 协作规范。它存在的理由：这个项目会由不同的 AI 工具接手，而每个工具都不会天然知道本仓库的约定、坑和验证手段。所有条文的目的是让一个陌生 AI 在读完后能做出**符合本仓库既有风格**的改动，而不是按「业界常识」重写一遍。

三个前端（`vidora-web` / `vidora-mobile` / `vidora-admin`）各有同构的 `.code/` 目录，四端口径一致：**契约在这里定义，前端只是消费者**。

规范全部来自代码现场，不来自想象。每一条都能指到具体文件；如果你发现某条规范和代码对不上，**以代码为准，然后来改这份规范**。

## 四份规范各讲什么

| 文件 | 管什么 | 典型触发问题 |
| --- | --- | --- |
| `agent-rules.md` | AI 的行为纪律：动手前读什么、真实可跑的验证命令原文、什么操作必须先问、交付时怎么区分已验证与未验证 | 「我能不能直接 mvn install」「这 SQL 我能执行吗」「这话我能说成已验证吗」 |
| `coding-standards.md` | 代码怎么写才像本仓库作者写的：模块与包布局、命名、五层分层归属、响应包装、异常语义、yml 约定、日志、注释纪律 | 「这个字段该放 entity 还是 VO」「分页返回 Page 还是 PageResult」「新配置写哪个 yml」 |
| `requirements.md` | 需求侧边界：后端负责什么、三端各自拿哪些出口、加一个能力该落在哪个服务、需求四段式怎么写、什么算完成 | 「前端要我加个字段合理吗」「这逻辑该服务算还是前端算」 |
| `skills/*.md` | 可执行手册：一类改动的具体步骤、要动哪几个文件、每步怎么验证、已知坑（见下方任务映射表） | 「加一套 CRUD 要从哪一步开始」「管理端接口到底要同步几处」 |

## 建议阅读顺序

1. `agent-rules.md` —— 先搞清楚本仓库的编译到底怎么跑起来（它和「常识」差得很远）、什么动作会造成不可逆后果。
2. `requirements.md` —— 再搞清楚职责边界：9 个服务各管一片，网关和 system-service 有两处重复的权限判定，搞不清就会把接口暴露错。
3. `coding-standards.md` —— 最后落到怎么写才和现有 29 个 Mapper、7 个服务的风格一致。

之后的日常开发不必重读全文：`AGENTS.md` 在仓库根目录会强制你回到这里，按下面的任务映射只翻需要的那几节。

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
- **`SQL/*.sql` 由用户手工执行**。AI 不得连库跑迁移，不得因为「docker-compose 里挂了 SQL 目录」就以为它会自动生效（只在 MySQL 首次初始化时执行）。
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

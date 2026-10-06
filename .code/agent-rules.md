# AI 行为规范（agent-rules）

本文件约束 AI 在本仓库的行为。目标是：**在不了解全貌时不做破坏性动作，在没有证据时不做确定性断言。**

---

## 一、动手前的必读清单

按顺序做完，缺一项就补做，不要跳：

1. 本文件 + `coding-standards.md` + `requirements.md` + `README.md`（项目定位、构建命令、目录速览都在 README.md）。
2. `pom.xml`（根）—— 真实的版本托管与 `<modules>`。**所有版本号只在这里**，子模块 pom 不写 version；要加依赖先在 `dependencyManagement` 里确认已有托管。
3. `ARCHITECTURE.md` 第 0 节「Boot 4 / JDK 25 迁移要点」—— 六条已付费的坑，别重新踩。注意其正文存在与代码不一致处（例如技术栈表写 MyBatis-Plus 3.5.14，而根 `pom.xml` 是 `3.5.17`）：**以 `pom.xml` 为准**。
4. 你要改的那个服务的现有代码，至少各读一个同类样本：
   - 动接口 → `vidora-modules/vidora-content/src/main/java/org/tiglor/content/controller/CategoryController.java`（分层最完整的一套 CRUD）
   - 动业务实现 → 同目录 `service/impl/CategoryServiceImpl.java`（校验、缓存失效、幂等写法都在这里）
   - 动权限/审计 → `vidora-modules/vidora-system/src/main/java/org/tiglor/system/controller/MenuController.java`、`OperLogController.java`
   - 动鉴权链路 → `vidora-gateway/src/main/java/org/tiglor/gateway/filter/GatewayAuthFilter.java` + `vidora-common/common-core/src/main/java/org/tiglor/common/core/security/HeaderAuthenticationFilter.java`
5. `vidora-gateway/src/main/resources/application.yml` —— **路由的唯一出处**。新接口没有对应 `Path=` 断言就是 404，跟 Controller 写得对不对无关。
6. 涉及响应结构、异常、日志、traceId 时，读 `vidora-common/common-core/` 与 `vidora-common/common-log/` 的对应类，不要在业务服务里重新实现一遍。

---

## 二、不许凭记忆断言代码现状

这是本项目最容易出事的地方：工作区有 **159 项未提交改动**（`git status --porcelain | wc -l`），而历史对话里可能出现过「已经修好」的结论。

规则：

- **任何关于「当前代码是怎么写的」的断言，必须当场 Read/Grep 确认后再说。** 包括「这个鉴权已经加了」「这个字段叫 heatScore」「这张表有 is_deleted」。
- 引用代码时给出文件路径（必要时带行号），不要复述大意。
- 「我记得 / 通常来说 / 一般来说 Spring 都是」这类措辞出现时，等于在说自己没查证，立即停止这种表达。**本仓库大量东西和「一般常识」不同**：包根是 `org.tiglor` 不是 `com.vidora`；分页返回的是 MyBatis-Plus 的 `Page` 而不是自研封装；网关是 WebFlux 且刻意不用 MDC。
- 数字、枚举值、字段名、路径这四类东西**永远现场查**。举例：`BusinessType` 是 0–9（`common-log/.../annotation/BusinessType.java`）、`sys_oper_log.status` 是 0-失败 1-成功、`transcode_task.status` 是 0-3、`video_info.status` 是 0-4，`content_security_audit.manual_result` 用 **NULL 表示未复核而不是 0**。这些都在 `SQL/` 的列注释里，第一手出处就是那里。
- 三端前端类型文件里的字段名**不算证据**，它们会漂。后端 Controller/DTO/entity 才是定义方。

---

## 三、构建 / 检查 / 验证门禁：具体命令

### 3.1 唯一可靠的机械门禁：离线编译

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"

mvn -o -q -Dmaven.legacyLocalRepo=true -pl <模块路径> -am compile   # 单模块 + 其依赖
mvn -o -Dmaven.legacyLocalRepo=true compile                          # 全仓
```

实测记录（本次会话真实执行）：两条命令均 `BUILD SUCCESS`（退出码 0）。控制台会有若干 `WARNING: ... jansi / sun.misc.Unsafe ...`，来自 Maven 自身跑在新 JDK 上，**不是构建错误**。

为什么每个参数都不能少：

| 参数 | 原因 |
| --- | --- |
| `JAVA_HOME` 显式指向 BellSoft JDK 25.0.4.1 | 默认 `java -version` 是 `1.8.0_191`；工程 `<release>25</release>` |
| `-o`（offline） | 依赖离线缓存在 `F:\repository`；联网解析会失败或超时 |
| `-Dmaven.legacyLocalRepo=true` | 本地仓库缺少 `_remote.repositories` 元数据，不加会被判定为「不可用」 |
| `-pl <模块> -am` | 只编受影响模块及其上游公共模块，反馈最快 |

模块路径写法：`vidora-gateway`、`vidora-auth`、`vidora-common/common-core`、`vidora-modules/vidora-content`（父级 `vidora-common` / `vidora-modules` 是聚合 POM，也可以单独作为 `-pl` 目标）。

改了 `vidora-common/*` 之后，依赖它的服务要用上面的 `-am` 一起编，或者干脆全仓编；只编单个下游服务不会重编公共模块。

### 3.2 关于测试：基础设施不完整，不要声称跑过

现场事实（自己查到的，不要引用本文当依据）：

- `find . -path "*/src/test/*" -name "*.java"` 命中 **10 个文件**，集中在 `vidora-interact`(4)、`vidora-message`(3)、`vidora-video`(3) 三个模块的 `service/impl` 与 `mq`；其余 6 个服务连 `src/test` 目录都没有。
- `spring-boot-starter-test`（test scope）**9 个模块 pom 里都有**，所以给别的服务加测试不缺这个依赖。
- `vidora-common-test` 只有 `vidora-interact`、`vidora-message` 两个 pom 引了（用它的 7 个测试类都基于 `MpTestSupport`）；`vidora-video` 那 3 个测试类没引，别照抄它的 pom 当模板。
- `vidora-common/common-test/` 只有一个 `MpTestSupport.java`，而且它位于 **main** 源码目录。
- 根 `pom.xml` 的注释明确写了 surefire 必须钉版本（`3.6.0`），否则 JUnit 5 测试会被**静默跳过**。

结论与纪律：

- **不要把「跑过 `mvn test`」当成放行条件**，也不要在交付里写「测试通过」，除非你确实执行了并贴出结果摘要。
- **不要因为某个服务没有 `src/test` 就去补一套推测性单元测试**（新建测试目录、引入 mock 框架、造 fixture）。这不在需求范围内，且会与既有 10 个文件的组织方式冲突。要补测试先问用户。
- 已有的 10 个测试文件是既成事实，改动相关 Service 时**可以读它们来理解预期行为**（它们是意图文档），但不要顺手扩大范围。

### 3.3 没有的东西，别写进交付说明

- 没有 Maven Wrapper（仓库里没有 `mvnw`），只有全局 `mvn`（`D:/apache-maven-3.9.6/bin/mvn`）。
- 没有 checkstyle / spotless / sonar 配置文件，因此**不存在「格式检查通过」「lint 干净」这种说法**。
- 没有 Flyway / Liquibase：`SQL/vidora_cloud.sql` 是纯手工脚本，见 `.code/skills/add-a-db-migration.md`。
- Nacos **只做服务注册与发现**，配置中心未接入：所有配置都在各服务自己的 `application.yml` 里（该文件内的注释就是这么写的，恢复步骤见 `ARCHITECTURE.md` 6.2）。所以「改配置去 Nacos 推」是错的。

### 3.4 运行时验证（需要用户同意起服务）

编译通过 ≠ 接口能通。路由缺失、`ADMIN_PATH_PREFIXES` 漏配、SQL 没执行这三类问题**只有真实调用才会暴露**。curl 的具体做法见 `.code/skills/verify-a-backend-change.md`。启动服务、连数据库属于用户的运行环境，AI 不得自行拉起或重启。

---

## 四、禁止动作清单

未经用户明确同意，**不得执行**下列任一操作：

| 类别 | 具体 | 为什么危险 |
| --- | --- | --- |
| 依赖变更 | 改任何 `pom.xml` 的 version / 新增或删依赖 / 升 lombok、mybatis-plus、nacos-client、spring-boot 版本 | 根 POM 的注释记录了成套的版本约束（Nacos 全家桶必须同版本、MP 分页器拆构件、RocketMQ 故意不用 starter），单点升级会连锁炸 |
| 本地仓库 | `mvn install` / `mvn deploy` / 清理 `F:\repository` | 污染或摧毁离线缓存，之后所有模块都编不过 |
| 数据库 | 连 MySQL 执行任何写 SQL、跑 `SQL/vidora_cloud.sql`、DDL、`TRUNCATE`、建库 | **默认**由用户手工执行；只有用户在当前会话里点名要你跑才可以跑，跑完必须贴出执行前后的库状态（审计表按设计无删除口，见 `OperLogController` 注释） |
| Git 写操作 | `git commit` / `git push` / `git reset --hard` / `git checkout --` / `git clean -f` / 改写历史 | 工作区有 159 项未提交改动，任何 reset/checkout/clean 都会直接吃掉别人的活 |
| 绕过检查 | `--no-verify`、跳过 hook、改 CI 配置 | 不允许 |
| 删文件 | 删除任何既有源文件、`SQL/vidora_cloud.sql`、`deploy/.env.example`、文档 | 不可逆且难发现 |
| 格式化 | 对 Java/pom/yml/SQL 做整文件格式化、批量统一行尾 | **行尾逐文件混用**（实测：`pom.xml` CRLF、`vidora-gateway/.../application.yml` LF、`SQL/vidora_cloud.sql` LF、`CategoryController.java` CRLF；同为服务 pom，`vidora-system/pom.xml` 是 LF 而 `vidora-auth/pom.xml` 是 CRLF）。统一后 diff 会变成全文重写，看不出真实改动 |
| Git 配置 | 新增 `.gitattributes`、改 `core.autocrlf`、`git add --renormalize` | 见本节末尾的成因说明 —— 这会一次性重写全仓的行尾表示 |
| 配置 | 改 `application.yml` 的端口、datasource url、`jwt.secret`、redis 口令、Nacos 地址；新增环境变量 | 影响联调目标；`deploy/.env` 已被 gitignore，改错了别人复现不了 |
| 服务 | 启停/重启网关或任一微服务、改 `deploy/docker-compose.yml` 后 up | 属于用户的运行环境 |
| 跨项目 | 修改 `E:\Project\vidora\vidora-cloud` 以外的目录（三个前端、根目录文档） | 那些端各有负责人和节奏；后端单方面改契约会让三端同时挂 |
| 安全边界 | 从 `GatewayAuthFilter.ADMIN_PATH_PREFIXES` 移除前缀、给 `/internal/**` 之外的路径加 `permitAll`、放宽 `@PreAuthorize` | 这是唯一的越权防线，去掉就是开洞 |

允许执行的只读或局部安全操作：`ls` / `cat` / `find` / `grep` / `git status` / `git diff` / `git log` / `git ls-files` / 第三节的 `mvn ... compile`。

### 为什么行尾是混的（成因，别当 bug 去修）

本机 `git config core.autocrlf` = **`true`**，而仓库里**没有 `.gitattributes`**。于是：

- checkout 时 Git 把仓库内的 LF 写成工作区的 CRLF；
- 提交时再把 CRLF 折回 LF。

结果就是同一个文件「在磁盘上是 CRLF、在 diff 里像 LF」，不同文件还各自停在不同的状态（同为服务 pom，`vidora-system/pom.xml` 是 LF 而 `vidora-auth/pom.xml` 是 CRLF）。这解释了为什么 `git diff` 会莫名提示 `LF will be replaced by CRLF the next time Git touches it`。

对 AI 的三个实际影响：

1. **编辑既有文件时按磁盘上看到的原样保留**，不要为了「统一」动它 —— 你的改动一旦跨行尾，Git 会把整个文件视为已修改。
2. **新建文件用 LF**（与仓库内表示一致），并在交付里说明该文件原本是 LF/CRLF。
3. **不要试图用 `.gitattributes` + `git add --renormalize` 顺手根治这件事**。那是一次全仓重写，会和当前 159 项未提交改动撞车，属于必须由用户单独决策的变更。

---

## 五、哪些改动必须先征求用户同意

除了第四节，以下**代码层面**的改动也要先问：

1. **改鉴权与授权口径**：`GatewayAuthFilter` 的四张名单（`PUBLIC_GET_PATHS` / `PUBLIC_GET_PREFIXES` / `VIDEO_DETAIL_PATH` / `ADMIN_PATH_PREFIXES`）、各服务 `config/SecurityConfig.java` 的 `requestMatchers(...)`、以及任何 `@PreAuthorize` 表达式。前端隐藏按钮不是安全边界，这里才是。
2. **改响应外壳与异常语义**：`common-core/.../ApiResult.java`、`GlobalExceptionHandler.java`、`ResultCode.java`。九个服务和三端前端都建立在 `{code,message,data}` + 「HTTP 状态码与业务码一致」之上。
3. **改契约形状**：Controller 方法的返回类型、DTO 字段名、分页参数名（现在是 `current` / `size`）。这是跨端变更，会影响 web / mobile / admin 三个仓库，必须由用户决策何时同步。
4. **新增第三方依赖或换实现**（哪怕只是换个 HTTP 客户端、加个工具库）。
5. **动 `common-*` 公共模块**：一处改动放大到九个服务。**动 `vidora-api-*` 契约同理**——接口签名或 DTO 字段一改，provider（`vidora-system` 的 `dubbo/`、`controller/`）与所有 consumer（`vidora-auth`、`vidora-video`）同时受影响，而 RPC 那条路是运行期序列化，编译绿不代表对得上。
6. **改审计链路**：`common-log` 的切面、`AuditJson` 脱敏规则、`LogSink` 的 Bean 选择逻辑、`logback-base.xml`。合规设施，改错是静默失效。
7. **删除或重命名任何 Controller 方法 / 公开接口路径**：三端可能都在调。
8. **新建第二个 SQL 文件或任何迁移载体**，或对 `SQL/vidora_cloud.sql` 里已定型的建表段做结构性重写（往节尾追加补种子的 `INSERT IGNORE` 是可以的，历史上菜单 38–48 就是这么加的）。
9. 任何**大范围重写**（批量换 `@Autowired`→构造注入、把所有 service 改成不用 MP 的 `lambdaQuery`、统一改名）。本仓库风格高度一致，重写会让 diff 失去可读性。

---

## 六、交付时如何区分「已验证」与「未验证」

交付说明必须分成两块，逐条对齐真实跑过的命令。

**已验证**：写清「跑了什么命令 + 观察到什么结果」。例：

- `mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile` 退出码 0，无 error。
- `GET /api/categories/page` 用 admin token 返回 200 且 `data.records` 有 7 条；换 web token 返回 403。（这条要真的调过才能写）

**未验证**：把没做过但相关的项显式列出来，不要沉默。例：

- 未运行服务，新路由的实际转发与 StripPrefix 行为本次未验证。
- 新表的 DDL 未在库上执行（按约定默认由用户手工执行），因此依赖它的查询与菜单种子未验。
- 三端前端的同名调用点未同步检查（不在本次范围）。

硬性禁令：

- **不要把没跑过的检查说成跑过。** 包括但不限于：「测试通过」（基础设施不完整）、「lint 干净」（不存在）、「DDL 已在库上生效」（默认 AI 无权执行）、「前端也验过了」（除非真在同级仓库核对过对应 Controller 消费点）。
- 不要因为「代码看起来对」就写「已验证」。视觉判断不是验证。
- 无法验证时直说阻塞在哪（服务没起、缺 admin 账号、库没建、迁移未执行），而不是含糊过去。
- 结论与证据分离：先给证据（命令 / 文件路径 / 行号），再给结论。
- 交付末尾附一行**行尾声明**：本次改动的文件各自保持了原有行尾（或指出某文件原本是混合行尾）。

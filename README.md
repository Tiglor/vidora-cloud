# Vidora Cloud - 云端服务工程（Server）

> ⚠️ **本项目是「后端独立包」。前端是三个独立包**，通过网关 REST API 与后端协作：
> - **Web 端**：`../vidora-web`（Vue3 + Vite + Element Plus，参考 B 站/腾讯视频/爱奇艺）
> - **移动端**：`../vidora-mobile`（uni-app，H5 + 小程序）
> - **管理端**：`../vidora-admin`（Vue3 + Vite + TS + Element Plus，运营/RBAC 后台）
> 四个仓库的 AI 规范都在各自的 `.code/` 目录里（不再有根 `AGENTS.md`）；本仓库负责提供 JSON API 与 Docker 部署编排（每个微服务一一对应一份 Dockerfile）。

基于《视频播放类APP分布式微服务技术架构方案（第三版）》与《项目架构体系》落地。

## 技术栈

| 层面 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 后端运行时 | JDK | **25 LTS**（25.0.4.1） | BellSoft JDK 25 |
| 后端框架 | Spring Boot | **4.0.7** | Spring Framework 7.0 |
| 微服务体系 | Spring Cloud Alibaba | **2025.1.0.0** | 底层 Spring Cloud 2025.1.3（Oakwood），对应 Boot 4.0.x |
| 注册中心（配置中心暂未接入） | Nacos client | **3.2.4** | SCA 默认 3.1.1，已在父 POM 显式覆盖（全家桶 5 构件钉死） |
| ORM | MyBatis-Plus | **3.5.17** + `mybatis-plus-jsqlparser` + `mybatis-spring 4.0.0` | Boot 4 需 mybatis-spring 4.x；版本一律以根 `pom.xml` 为准 |
| 接口文档 | SpringDoc OpenAPI + therapi-runtime-javadoc | **3.1.0** + **0.15.0** | Boot 4 对应 springdoc 3.x；接口说明**只写 Javadoc、不贴 `@Tag`/`@Operation`**，由 scribe 注解处理器在编译期把注释烘进 `target/classes/**/*__Javadoc.json`，springdoc 运行期读出来填进 `/v3/api-docs`（约定见 `.code/coding-standards.md` 3.1） |
| 对象存储 | MinIO / 本地磁盘 | SDK **8.5.17** | 视频文件上传/存储，可切换 |
| 多媒体处理 | FFmpeg（ffmpeg + ffprobe） | 本机 CLI 依赖 | 仅 `vidora-modules/vidora-video` 转码/探测时需要，见下文「FFmpeg 前置依赖」 |
| 数据库 | MySQL | 8.0 | 单库 `vidora_cloud`，内部按业务功能分 8 节；边界靠表名前缀与服务间调用守，不再做库级隔离 |
| 缓存 | Redis | 7 | |
| 消息队列 | RocketMQ | 引擎 **5.3.2** / 集成库 2.3.4（暂未启用） | starter 版本号 ≠ 引擎版本号，详见 `.code/ARCHITECTURE.md` 6.4 |
| 分布式锁 | Redisson | **4.7.0**（原生 jar，不用 starter） | `common-redis` 引 `org.redisson:redisson`，`RedissonConfig` 手动产 `RedissonClient`；starter 会把 `LettuceConnectionFactory` 换成 `RedissonConnectionFactory`，动的就是现有 `@Cacheable`。recommend 的 feed 刷新已在用 |
| 服务间调用契约 | `vidora-api-{服务名}` | 本仓库内部 jar | interface + DTO 由**被调用方**拥有并发布，契约保持传输层中立 |
| 服务间同步调用 | Spring Cloud OpenFeign + **Dubbo 3** | Feign 由 Spring Cloud 2025.1 BOM 管理；Dubbo **3.3.6**（逐构件钉版本，不用 dubbo-bom） | Feign 走 `vidora-api-*` 的 HTTP 契约；Dubbo 走同一模块里的 `Remote*Api` 内部契约。当前唯一 Dubbo 链路是 auth-service → system-service 取用户与客户端，判据见 `.code/ARCHITECTURE.md` 6.2.1 |
| 第三方对接 | `vidora-integration`（防腐层） | 预留边界 | 云转码 / 内容安全 / 短信 / 支付等外部客户端将来落在这里 |

## 目录结构

```
vidora-cloud/（云端服务，共 19 个模块 = 4 个聚合 pom + 9 个可运行服务 + 6 个库）
├── pom.xml                          # 根工程与统一依赖管理（版本全在这里钉，子模块不写版本号）
├── vidora-common/                   # 公共库聚合
│   ├── common-core/                 # 统一响应/异常/BaseEntity/JWT/安全头透传/logback-base.xml
│   ├── common-redis/                # Spring Cache 装配 + CacheNames 常量表 + RedissonClient（锁）
│   ├── common-log/                  # @OperLog 审计切面 + 分级滚动日志 + FileLogSink/RemoteLogSink
│   └── common-test/                 # MyBatis-Plus 单测支撑（放 main 源码，各模块以 test scope 引）
├── vidora-api/                      # ★ 服务间调用契约聚合：契约由**被调用方**拥有并发布
│   └── vidora-api-system/           # system-service 的契约：HTTP 侧 UserApi + RemoteUserDTO，RPC 侧 RemoteUserApi / RemoteClientApi + Remote*DTO
├── vidora-integration/              # ★ 第三方适配层（防腐层）：外部系统的客户端落这里
├── vidora-gateway/                  # 网关 8080（路由 + JWT 鉴权 + 权限头透传），WebFlux
├── vidora-auth/                     # 登录/注册/JWT 签发 + 用户自助偏好 8101；**不连库**，用户与客户端走 Dubbo 向 system 取
├── vidora-modules/                  # 业务服务聚合
│   ├── vidora-video/                # 视频/存储/转码 8102
│   ├── vidora-content/              # 内容分类与标签 8103
│   ├── vidora-interact/             # 评论/点赞/弹幕/关注 8104
│   ├── vidora-message/              # 站内消息 8105
│   ├── vidora-search/               # 搜索与联想词 8106
│   ├── vidora-recommend/            # 推荐位与特征 8107
│   └── vidora-system/               # 用户/角色/菜单/RBAC/审计日志 8108；RBAC 实体与 Mapper 的唯一归属，兼 Dubbo provider（20880）
├── SQL/                             # 全量建库脚本（单库 vidora_cloud、按业务分 8 节；只写目标态，不保留迁移脚本）
├── deploy/                          # 部署编排：docker-compose.yml + nginx 反代 + .env + 部署文档
└── .code/                           # AI 进本仓库的第一必读：README.md 索引 + 行为规范、编码规范、需求规范、skills 手册、ARCHITECTURE.md
```

> 每个可运行模块目录（vidora-gateway、vidora-auth、vidora-system、vidora-video、vidora-content、vidora-interact、vidora-message、vidora-search、vidora-recommend）下各有一份**一一对应**的 `Dockerfile`；
> `vidora-common/*`、`vidora-api/*`、`vidora-integration` 都是库，只出 jar 不单独打包运行。

## 服务间调用分层

判据一句话：**对端是否和你在同一个仓库、同一次发布**。是 → 内部调用；否 → 第三方。

| 场景 | 落在哪 | 现状 |
|------|--------|------|
| 服务 A 同步调服务 B（HTTP） | 契约 `vidora-api/vidora-api-{B}`，调用实现留在 A 的 `client/` 包 | `UserApi` + `RemoteUserDTO` ← `vidora-video` 的 `SystemUserClient extends UserApi` |
| 服务 A 同步调服务 B（内部 RPC） | 同一个 `vidora-api-{B}` 里的 `Remote*Api` + `Remote*DTO`，provider 侧 `@DubboService`、消费侧 `@DubboReference` | `RemoteUserApi` / `RemoteClientApi` ← `vidora-auth` 的 `AuthServiceImpl`、`ProfileServiceImpl`；provider 在 `vidora-system` 的 `dubbo/` 包 |
| 对接平台外部系统 | `vidora-integration`（防腐层），OpenFeign 留在这条线上 | 模块边界已立，尚无真实调用方（第一个该入驻的是内容安全或云转码） |
| 不要求立即返回 | RocketMQ 事件，见 `.code/ARCHITECTURE.md` 6.4 / 8.2 | 转码任务已接（`vidora-transcode-task`，默认 `enabled=false`） |

三条规则：

1. **契约由被调用方拥有并发布**，调用方不许在自己模块里手抄 DTO。这次清掉的历史包袱就是 `RemoteUserDTO`——原先抄在 `vidora-video/client/dto`，字段与 `User` 全靠人肉对齐。
2. **契约保持传输层中立**：`vidora-api-*` 不引 `spring-web`（所以没有 `@GetMapping`），HTTP 注解由调用方的 Feign 接口重新声明，靠 `@Override` 锁住方法签名与返回类型。
   ⚠️ 没闭环的另一半：`UserController.getById` 的出口仍是 `User` 实体而不是 `RemoteUserDTO`，所以「provider 少发一个字段」这类**字段级**漂移编译期抓不到；要改属于跨端契约变更，需先拍板。
3. **同一个 `vidora-api-*` 里 HTTP 契约与 RPC 契约并存，且必须分开写**。原先设想「一份接口将来直接被 Dubbo provider `implements`」，落地时推翻了：`ApiResult` 是 HTTP 响应外壳，Dubbo 侧裹一层等于让内部调用替前端解析状态码。所以 RPC 走 `Remote*Api` + 裸 DTO。两条硬约束：
   - **RPC 契约不抛业务异常**。Dubbo 的 `ExceptionFilter` 只原样透传 checked 异常、方法 `throws` 里声明过的、`java.*/javax.*/jakarta.*`、以及与接口同 codebase 的异常；`BizException` 在 common-core、接口在 vidora-api-system，两个 codebase 不同，会被拍平成 `RuntimeException(堆栈字符串)`、`code` 丢失。所以 `Remote*Api` 用 **`null` 表示「没找到 / 已存在」**，由调用方翻译成 `BizException`。
   - **敏感字段走独立 DTO**。`RemoteLoginUserDTO` 带 `passwordHash`（登录要在 auth 侧比对 BCrypt），它和会流到前端的 `RemoteUserDTO` 是两个类，避免哈希顺着 HTTP 契约漏出去。

Dubbo 3 目前只有 auth-service → system-service 这一条链路，收益是登录从「四次跨服务跳」压成一次（用户 + 角色 + 权限 + 客户端一起取回）。判据与三个前置事项的落地结果写全在 `.code/ARCHITECTURE.md` 6.2.1。

新增一个契约子模块的落点：在 `vidora-api/` 下建 `vidora-api-{服务名}`，同时登记 `vidora-api/pom.xml` 的 `<modules>` 与根 `pom.xml` 的 `dependencyManagement`。

## 编译

> 本机实测环境：Maven `D:\apache-maven-3.9.6`（`mvn` 在 Git Bash 与 PowerShell 下都可用），依赖缓存于 `F:\repository`，可用 `-o` 离线构建。唯一硬前提是 `JAVA_HOME` 必须指到下面那个**内层** jdk 目录。

**关键坑：JDK 25 的目录是嵌套的。**
BellSoft 解压后得到两层目录：

```
D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\   ← 外层（不是 JDK 根）
└── jdk-25.0.4.1\                                        ← 真正的 JDK 根，JAVA_HOME 必须指到这里
```

`JAVA_HOME` 必须指向**内层** `jdk-25.0.4.1`，若只指到外层目录，Maven 会报 `无效的目标发行版: 25`。

### 完整编译（19 个模块）

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' clean compile -o
```

Git Bash 下同样可用（已实测通过），只要 `JAVA_HOME` 指到**内层** jdk 目录：

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"
mvn -o -q -Dmaven.legacyLocalRepo=true compile
```

`-o` + `-Dmaven.legacyLocalRepo=true` 是离线走 `F:\repository` 缓存；去掉 `-o` 会去联网解析依赖。

### 仅编译某个服务（含其依赖）

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' -pl vidora-modules/vidora-video -am clean compile -o
```

## FFmpeg 前置依赖（仅 vidora-video 转码需要）

`vidora-modules/vidora-video` 的转码与媒体探测通过 `ProcessBuilder` 调用**本机** `ffmpeg` / `ffprobe` 命令行。

- **开发机当前未安装** ffmpeg/ffprobe（`where ffmpeg` 验证找不到）。只跑上传/列表/下载（不触发探测与转码）不影响编译与启动；一旦调用上传（会触发 ffprobe 探测）或转码，会抛出明确报错（含"请确认已安装并配置路径"提示）。
- **生产环境**：凡是运行 `vidora-modules/vidora-video` 且启用转码的节点，**必须预装 ffmpeg + ffprobe**（Linux `apt/yum install ffmpeg` 或官方静态构建；Windows 下载 ffmpeg.org 构建，并在 `application.yml` 的 `ffmpeg.ffmpeg-path` 配绝对路径）。
- 不想每个业务 Pod 都装 CLI 的方案：把转码抽成**专用转码集群 / Worker**，或改用**云转码**（阿里云 MPS / 腾讯云 MPS），`vidora-modules/vidora-video` 仅"提交任务 + 轮询结果"。详见 `.code/ARCHITECTURE.md` 第 8 节。

`transcode.enabled=false` 时可关闭上传自动转码（仅存储源文件，play-url 直接返回源文件地址）。

## 视频转码架构（vidora-video / 运行名 video-service / 8102，已落地）

采用**大厂式异步解耦 + 多清晰度 HLS** 流水线（参考 Jellyfin 的 `MediaEncoder` 进程管理思路）：

```
上传 → 落对象存储(MinIO/本地) → 建转码任务(video_transcode_task, PENDING)
     → @Async 线程池拉起 ffmpeg → 出 360/480/720/1080p + master.m3u8（CMAF/fmp4 切片）
     → 产物回传对象存储 → 暴露公共播放地址(CDN 思路) → 状态机 PENDING→PROCESSING→SUCCESS/FAILED
```

- 状态机：`video_transcode_task` 表，`status` 0-待处理 / 1-处理中 / 2-成功 / 3-失败，`progress` 进度，`retry_count` 失败自动重试。
- 硬件加速：支持 `none`(libx264) / `cuda`(h264_nvenc) / `qsv`(h264_qsv)，通过 `transcode.hwaccel` 配置。
- 生产演进：当前 `@Async` 线程池是原型可运行方案；上生产应替换为 **RocketMQ/Kafka + 独立转码集群/云 MPS**（接口与状态机不变）。

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < SQL/vidora_cloud.sql
```

> `SQL/vidora_cloud.sql` 是 `SQL/` 下唯一的文件：一个业务库 `vidora_cloud`、33 张表按业务功能分 8 节（用户与权限 / 审计日志 / 视频与转码 / 内容运营 / 互动 / 站内消息 / 搜索 / 推荐），只描述目标态（建库 + 全部建表 + 种子数据），不保留迁移脚本、也不给单张表另开文件；表和每一列都必须带 `COMMENT`。跑完这一个文件就齐了，审计表与全部菜单、分区种子都在里面。
> 建库建表是普通 DDL，**不带 `IF NOT EXISTS`**：库或表已存在就直接报错停下（`ERROR 1050 ... already exists`），而不是静默跳过让人以为改动生效了。所以它是全量建库脚本，不是可重跑的补丁 —— 改结构就改对应节里的 `CREATE TABLE`，存量库要么重建（`DROP DATABASE vidora_cloud;` 后重跑本文件），要么手工执行等价的 `ALTER`。只有种子数据用 `INSERT IGNORE`。
> 第 1 节已内置 RBAC 初始数据：管理员 `ROLE_ADMIN` 拥有 `menu:add/edit/delete/assign`、`role:assign`、`video:transcode` 等按钮权限。

### 2. 启动后端

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' clean install -DskipTests -o
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' -pl vidora-gateway,vidora-auth,vidora-modules/vidora-system,vidora-modules/vidora-video spring-boot:run -o
```

Nacos 当前**只做服务注册与发现**，没有接配置中心：每个服务的配置都在自己那一个 `src/main/resources/application.yml` 里。
本地没起 Nacos 各服务仍能启动（注册失败只告警），但网关的 `lb://xxx-service` 解析不到实例、请求会 503。
配置中心的重新启用步骤见 `.code/ARCHITECTURE.md` 6.2。

### 3. 前端对接

前端独立包通过网关 `http://127.0.0.1:8080` 调用，路径带 `/api` 前缀：

| 能力 | 接口 | 权限 | 说明 |
|------|------|------|------|
| 登录 | POST /api/auth/login | 公开 | |
| 注册 | POST /api/auth/register | 公开 | |
| 视频列表 | GET /api/videos/page | 登录 | |
| 我的投稿 | GET /api/videos/mine | 登录 | 当前用户自己的投稿（含待转码状态） |
| 视频详情 | GET /api/videos/{id} | 登录 | |
| 批量取视频 | POST /api/videos/batch | 登录 | 跨域聚合用：body 是 id 数组，**上限 50**（超出静默截断）；只回存在的行、**顺序不保证**，调用方必须按 id 建映射，不能按下标对齐 |
| 投稿用户 | GET /api/videos/{id}/owner | 登录 | video-service 经 OpenFeign 调用 system-service，契约在 `vidora-api/vidora-api-system` |
| 视频上传 | POST /api/videos/upload | `video:upload` | 落存储 + 探测元信息；`transcode.enabled=true` 时自动提交异步转码 |
| 提交转码任务 | POST /api/videos/{id}/transcode | `video:transcode` | 手动触发，返回任务 ID |
| 查询转码任务 | GET /api/videos/{id}/transcode-task | 登录 | 轮询状态(0待处理/1处理中/2成功/3失败)与进度 |
| 播放地址 | GET /api/videos/{id}/play-url | 登录 | 转码成功后返回 `master.m3u8` 公共地址，否则返回源文件地址 |
| 下载地址 | GET /api/videos/{id}/download | 登录 | MinIO 为限时预签名 URL |
| 评论列表 | GET /api/comments/video/{videoId} | 登录 | 按视频分页查询顶层评论 |
| 发布评论 | POST /api/comments | 登录 | 服务端从 JWT 透传身份，不信任客户端 userId |

- 除 `/api/auth/**` 外，其余接口需在请求头携带 `Authorization: Bearer <token>`，否则网关返回 **401**
- 网关校验通过后会把 `userId / 角色 / 权限` 以 `X-User-Id / X-User-Roles / X-User-Permissions` 请求头透传给下游服务

### 多端清单与职责

| 端 | 仓库 | 职责 | 交付面 |
|----|------|------|--------|
| 消费端 Web | `../vidora-web` | 看视频：首页推荐流、搜索+联想、详情播放（多清晰度 HLS）、评论、上传与转码进度、个人中心 | 浏览器 |
| 消费端 Mobile | `../vidora-mobile` | 同一批消费功能的移动形态：首页/发现/详情/消息中心/我的/上传 | uni-app 一份代码 → H5、微信小程序、App |
| 管理端 Admin | `../vidora-admin` | 运营与治理：用户/角色/菜单/客户端、分类/标签/热搜/审核、视频与评论管理、推荐与搜索词运营、操作/登录日志 | 浏览器，路由与菜单由 `sys_menu` 动态下发 |

三端打同一套 `/api` 接口，靠 JWT 里的 `clientId` + 网关前缀拦截区分身份；内容字典（分区/标签/热搜）由 admin 写、两个消费端只读。桌面 PC 客户端暂缓，规划上由 web 拓展、不新建仓库。

### 接口文档与三端契约

**后端只负责一件事：让 `/v3/api-docs` 里的接口与字段描述是完整的。** controller / DTO / VO / 实体上的 Javadoc 由 therapi 注解处理器在编译期烘进 `target/classes/**/*__Javadoc.json`，springdoc 运行期读出来填进 `/v3/api-docs`（可看 swagger-ui），不贴 `@Tag` / `@Operation`，写法约定见 `.code/coding-standards.md` 3.1。这份文档导进 Apifox 由人工执行，本仓库不做聚合、也不生成任何前端代码。

三个前端各自手抄了一份请求层（web `utils/request.js`、mobile `utils/request.ts`、admin `api/request.ts`），主题引擎在 web 与 admin 各存一份 —— 这些仍然是重复的。**契约类型同样是手抄的**：admin `src/api/types.ts`、mobile `src/types/index.ts`（两端 TS 各一份），web 是纯 JavaScript，形状只写在 `src/api/*.js` 每个函数上方的注释里。

手抄的代价要写明白：**后端改字段名或删字段，三端不会编译报错**，只会在运行时读到 `undefined`。已经发生过三起（热搜 `heat` 实际叫 `heatScore`、搜索历史形状错配、评论列表读一个后端从不返回的 `userName`）。所以约定是两条：

- 后端改动涉及出入参形状时，交付说明里必须点名「改了哪个字段 / 哪一端的哪个类型文件要跟着改」，不能只说「接口加了这个能力」。
- 三端接接口前先读 Controller 与 VO／实体，类型文件只是索引；`SQL/vidora_cloud.sql` 的列注释是档位与可空性的判据。

曾经试过并**已撤掉**的做法：离线把 springdoc 的文档 dump 成 `openapi/*.json` 存进仓库，再用 openapi-typescript 生成三端类型。它拦得住字段名拼错，但那份 JSON 靠人重跑脚本刷新，忘跑时「类型检查通过」证明的是旧契约——比没有类型门禁更容易误导，所以不留在仓库里。

尚未实施的下一步：**web 转 TS，三端请求层与主题引擎收敛进共享包**（pnpm workspace 或独立 npm 包）。实施前需先拍板仓库拓扑（三个前端目前是三个独立目录、admin 尚未进 git）。

## 前端 Web 工程（vidora-web）

参考 **B 站 / 腾讯视频 / 爱奇艺** 的版面与交互，采用 Vue3 + Vite + Element Plus + Pinia + Vue Router + hls.js 实现，与后端网关 `http://127.0.0.1:8080` 对接（所有接口带 `/api` 前缀）。

### 已实现页面（参考三大视频站）
- **首页（HomeView）**：顶栏搜索 + 左侧分类导航（参考 B 站分区）+ 视频卡片网格（参考爱奇艺/腾讯首屏）
- **视频详情（VideoDetailView）**：hls.js 自适应播放器 + 基本信息 + 评论区（参考三大站内页）
- **投稿上传（UploadView）**：文件上传 + 转码任务轮询进度
- **登录 / 注册（LoginView / RegisterView）**、**搜索（SearchView）**、**个人中心（UserCenterView）**

### 本地运行

```bash
cd ..\vidora-web
npm install
npm run dev        # 默认 http://localhost:5173，Vite 代理 /api → 网关 8080
npm run build      # 产出 dist/，可交给 nginx 托管
```

> 已验证：`npm run build` 通过（1704 模块，仅有 chunk > 500kB 的体积告警）。本端是纯 JavaScript，没有类型门禁，接口形状写在 `src/api/*.js` 每个函数上方的注释里。Vite 代理在 `vite.config.js`，把 `/api` 转发到 `VITE_GATEWAY`（默认 `http://localhost:8080`）；`VITE_API_BASE` 是 axios 的 baseURL 前缀（默认 `/api`），两个变量别混。登录态用 Pinia + localStorage 存 token，请求拦截器注入 `Authorization: Bearer <token>`，响应 401 自动跳登录。

## Docker 部署（每个微服务一一对应）

后端 9 个可运行服务各有一份**一一对应**的 `Dockerfile`（基础镜像 `eclipse-temurin:25-jdk`），`vidora-common/common-core` 是库不单独打包。统一用 `deploy/docker-compose.yml` 编排。

### 服务与 Dockerfile 对应关系
| 服务目录 | Dockerfile | 容器内端口 | 说明 |
|----------|-----------|-----------|------|
| `vidora-gateway/` | `Dockerfile` | 8080 | API 网关 |
| `vidora-auth/` | `Dockerfile` | 8101 | 登录/注册/JWT 认证 |
| `vidora-modules/vidora-system/` | `Dockerfile` | 8108 | 用户/角色/菜单/RBAC |
| `vidora-modules/vidora-video/` | `Dockerfile` | 8102 | 视频/转码 |
| `vidora-modules/vidora-content/` | `Dockerfile` | 8103 | 内容 |
| `vidora-modules/vidora-interact/` | `Dockerfile` | 8104 | 互动 |
| `vidora-modules/vidora-message/` | `Dockerfile` | 8105 | 消息 |
| `vidora-modules/vidora-search/` | `Dockerfile` | 8106 | 搜索 |
| `vidora-modules/vidora-recommend/` | `Dockerfile` | 8107 | 推荐 |

`deploy/` 还编排了基础设施：**MySQL 8 / Redis 7 / Nacos 3.2.4 / MinIO**，以及 **nginx 反代**（把 80 端口转发到网关 8080，并提供前端静态托管示例）。

### 构建与启动

```bash
# 1) 先用 Maven 把各服务打成 jar（JDK 25，依赖缓存于 F:\repository）
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' clean package -DskipTests -o

# 2) 构建镜像并启动（在 deploy/ 目录，读取 .env）
cd deploy
docker compose build
docker compose up -d

# 3) 校验
docker compose ps
```

> 详细变量说明、端口映射、停止/清理与常见问题见 `deploy/README.md`。
> ⚠️ `vidora-modules/vidora-video` 镜像若要启用转码，需基于 `jrottenberg/ffmpeg` 等多阶段镜像预装 ffmpeg/ffprobe（当前 Dockerfile 用 `eclipse-temurin:25-jdk` 基础镜像，未含 ffmpeg；生产转码建议改用"独立转码集群/云 MPS"，与 `.code/ARCHITECTURE.md` 第 8 节一致）。

## 文档

- 架构落地与演进：`.code/ARCHITECTURE.md`（含第 8 节「视频存储与 FFmpeg 多媒体处理 / 异步多清晰度 HLS 流水线」、第 9 节「待完善」）

## 后续待完善

- [x] 视频存储抽象（MinIO / 本地可切换）
- [x] FFmpeg 探测 / 多清晰度 HLS 转码 / 抽封面
- [x] 异步转码流水线 + 任务状态机（video-service）
- [x] 网关 JWT 鉴权 + 权限头透传
- [x] Spring Security 授权 + RBAC 菜单控制（接口级 `@PreAuthorize`）
- [x] MinIO 分片上传 + MD5 秒传（`POST /api/videos/multipart/init` 返回 `instant` 与 `uploadedIndexes`）
- [x] 操作审计与登录日志落库 + 管理端查询（`common-log` 切面 → system-service 的 `sys_oper_log` / `sys_login_log`；DDL 在 `SQL/vidora_cloud.sql` 第二节审计日志）
- [x] 服务间调用契约层 `vidora-api`（契约由被调用方拥有，传输层中立）
- [x] Dubbo 3 落地：auth-service 不再连库，用户 / 角色 / 权限 / 客户端经 `RemoteUserApi`、`RemoteClientApi` 向 system-service 取
- [x] 删除 `vidora-common/common-user`：RBAC 实体与 Mapper 收归 `vidora-modules/vidora-system`，单库之后 `sys_*` 也只有它一个读写方
- [x] 接口文档说明全覆盖：123 个 endpoint（30 个 `@RestController`）逐个写了 Javadoc，且统一成「第一行接口名 + 可选 `<p>` 描述段」的多行块（禁用 `/** 一句话 */` 紧凑式，`.code/tools/check-endpoint-javadoc.py` 源码与 `--baked` 两个口径各 123 个、0 问题），经 therapi 编译期烘进 `target/classes/**/*__Javadoc.json`、springdoc 运行期填进 `/v3/api-docs`，不贴 `@Tag`/`@Operation`（约定见 `.code/coding-standards.md` 3.1，坑见 `.code/ARCHITECTURE.md` 0 节 #9）。字段级注释也已补齐 —— 8 个对外模块共 442 个 entity／DTO／VO 实例字段逐个有 Javadoc，导入 Apifox 后请求体与响应体的属性描述不再是空的。唯一还留洞的是 `vidora-api-system` 那个契约模块烘不出 `__Javadoc.json`（注释在，处理器没产出，原因未明），影响面只有 `VideoController.owner` 的响应体 `ApiResult<RemoteUserDTO>` 带不出属性描述，细节见 `.code/coding-standards.md` 3.1
- [ ] provider 侧真正 `implements` 契约（当前 `UserController` 出口仍是 `User` 实体，字段级漂移编译期抓不到）
- [ ] 跨服务事件接 RocketMQ：转码触发已接（默认 `rocketmq.enabled=false`，走本地线程池兜底）；**计数更新、弹幕入库、转码完成通知仍未接**
- [ ] 独立转码集群 / 云 MPS（当前 ffmpeg 跑在 video-service 进程内）
- [ ] BFF 聚合层：feed 列表服务端补作者 + 计数 + 分类。Dubbo 已落地但只有 auth→system 一跳，**多跳收益要等这层才真正显现**，判据见 `.code/ARCHITECTURE.md` 6.2.1
- [ ] 前端契约的机械校验：三端类型仍是各自手抄的副本，后端改名不会让三端编译报错。曾落地过「离线快照 + openapi-typescript 生成」，2026-10-06 撤掉——快照靠人重跑脚本刷新，忘跑时「类型检查通过」证明的是旧契约，比没有门禁更误导。可靠的形态是服务起得来的 `/v3/api-docs` 直接对接，或三端转 TS 后共享契约包，见「接口文档与三端契约」
- [ ] 第三方对接落地：`vidora-integration` 目前只有边界，第一个客户应是内容安全审核
- [x] Redisson 分布式锁：`common-redis` 引原生 jar + `RedissonConfig` 产 `RedissonClient`，recommend 的 feed 刷新按「用户 + 场景」加锁（12.1）。interact 的计数仍是数据库原子自增，不需要锁
- [ ] Elasticsearch 全文检索（search-service 当前是 DB  LIKE + 联想词表）
- [ ] Sentinel 限流规则（流控/熔断，依赖已引入但无规则）
- [ ] 单元测试补齐（现 10 个测试类只在 interact / message / video，content / recommend / search 按约定无测试）

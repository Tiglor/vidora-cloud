# 视频播放平台 - 架构落地文档

> 📦 **本仓库是「后端独立包」。前端是另一个独立包**（由前端负责人维护），两包通过网关 REST API 协作；本仓库不含任何前端代码。
>
> **构建状态（2026-10-03）**：JDK 25 + Spring Boot 4.0.7 下，根工程 11 个模块 `mvn clean compile` 已 **BUILD SUCCESS**（产物字节码 major=69，即 Java 25）；本轮新增视频存储（MinIO/本地）、FFmpeg 多媒体处理、以及**异步多清晰度 HLS 转码流水线**（提交任务 + @Async Worker 池 + 状态机 + 重试）后重编译仍全绿。

## 0. Boot 4 / JDK 25 迁移要点（踩坑记录）

| # | 问题 | 处理 |
|---|------|------|
| 1 | **Spring Cloud Gateway 5.0 拆分 starter**：`spring-cloud-starter-gateway` 已不存在 | 改为 `spring-cloud-starter-gateway-server-webflux`（另有 `...-server-webmvc`） |
| 2 | **MyBatis-Plus 3.5.9+ 移除分页拦截器**：`PaginationInnerInterceptor` 不在 `mybatis-plus-extension` 中 | 单独引入 `com.baomidou:mybatis-plus-jsqlparser`（包名不变） |
| 3 | **Boot 4 需要 mybatis-spring 4.x**（Spring Framework 7） | 父 POM 显式托管 `org.mybatis:mybatis-spring:4.0.0` |
| 4 | **springdoc 2.x 属 Boot 3 线** | 升级为 `springdoc-openapi-starter-webmvc-ui:3.1.0` |
| 5 | **Lombok 需 ≥1.18.38 才支持 JDK 25**；编译插件需支持 release 25 | lombok `1.18.42` + `maven-compiler-plugin:3.14.1`（`<release>25</release>`） |
| 6 | **SCA 2025.1.0.0 默认 nacos-client 3.1.1**，不满足 Nacos 3.2.4 要求 | 父 POM 显式覆盖 **Nacos 3.2.4 全家桶**（client / client-basic / auth-plugin / encryption-plugin / log4j2-adapter），避免子构件回落到 3.1.1 造成版本混杂 |

## 1. 技术栈落地

| 层面 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 后端运行时 | JDK | **25 LTS**（25.0.4.1） | BellSoft JDK 25 |
| 后端框架 | Spring Boot | **4.0.7** | Spring Framework 7.0 / Jakarta EE |
| 微服务体系 | Spring Cloud Alibaba | **2025.1.0.0** | 底层 Spring Cloud 2025.1.3（Oakwood），对应 Boot 4.0.x |
| 注册中心（配置中心暂未接入，见 6.2） | Nacos client | **3.2.4** | SCA 默认 3.1.1，已显式覆盖 |
| ORM | MyBatis-Plus | **3.5.14** + `mybatis-plus-jsqlparser` + `mybatis-spring 4.0.0` | 逻辑删除 + 自动填充 + 分页 |
| 数据库 | MySQL | 8.0 | 每服务独立 schema |
| 缓存 | Redis | 7 | `spring-boot-starter-data-redis` + `vidora-common-redis`（Spring Cache 抽象，JSON 序列化 + 按缓存名分 TTL） |
| 认证 | JWT | 0.12.6 | BCrypt 加密密码 |
| 接口文档 | SpringDoc OpenAPI | **3.1.0** | Boot 4 对应 springdoc 3.x |
| 消息 | RocketMQ | **5.3.2**（原生 `rocketmq-client`） | 转码任务持久化投递；starter 未适配 Boot 4，见 6.4 |
| 前端 | — | 独立包（前端负责人维护） | 经网关 `/api/*` 调用本后端 |

> **已移除 Redisson**：其 `redisson-spring-data-*` 适配器最高只到 41（对应 spring-data-redis 4.1），
> 而 Boot 4.0.7 解析出的是 `spring-data-redis:4.0.6`，没有匹配的适配版本；加上代码里零使用，直接删除，
> 统一走 `spring-boot-starter-data-redis` 自带的 Lettuce。**分布式锁尚未选型**（见第 14 节）。

## 2. 微服务拆分与代码组织

| 服务 | 端口 | 核心实体（表） | 已实现能力 |
|------|------|----------------|------------|
| gateway-service | 8080 | — | 网关路由（/api/* → lb:// 各服务）、JWT 鉴权过滤器、Redis、Sentinel |
| auth-service | 8101 | User(sys_user) + RBAC 只读投影 | 注册、登录、JWT 签发 |
| system-service | 8108 | User/Role/Menu(sys_*) | 用户、角色、菜单与权限管理 |
| video-service | 8102 | VideoInfo(video_info) | 视频列表/详情/上传(自动异步转码)/下载/播放地址(多清晰度 HLS)/分片上传(断点续传+秒传) |
| content-service | 8103 | Category / Tag / FeedConfig / HotSearch / SecurityAudit | 分类树与标签字典、推荐流配置、热搜榜（按天快照 + 整批重排）、内容安全审核台，见第 11 节 |
| interact-service | 8104 | Comment / InteractAction / Danmaku / PlayCount | 评论楼中楼、点赞收藏分享、弹幕、播放计数与汇总，见第 9 节 |
| message-service | 8105 | MessageRecord / MessageConversation / PushDevice | 站内通知与互动消息、私信会话（未读红点、时间线）、推送设备绑定，见第 10 节 |
| search-service | 8106 | SearchHistory / SearchKeywordStat / SearchSuggest | 我的搜索历史、全站词频统计（按天）、建议词字典与自动挖掘；**检索本身未接 ES**，见第 13 节 |
| recommend-service | 8107 | RecommendResult / AlgoConfig / UserFeature | 推荐流（取出即标记曝光）、点击上报、算法配置（进 Redis）、用户画像批量写入，见第 12 节 |

每个微服务统一分层：

```
org.tiglor.{module}.{service}/
├── config/        # MybatisPlusConfig（分页 + MapperScan + 自动填充）
├── controller/    # HTTP 接口（返回 ApiResult）
├── service/       # 业务接口（继承 IService）
├── service/impl/  # 业务实现
├── mapper/        # MyBatis-Plus Mapper
└── entity/        # 实体（继承 BaseEntity 或独立）
```

## 3. common/core 公共能力

- `ApiResult<T>`：统一响应结构
- `ResultCode` / `BizException`：状态码与业务异常
- `GlobalExceptionHandler`：全局异常处理（参数校验/业务异常）
- `BaseEntity`：逻辑删除 + 自动填充基类（createTime/updateTime/isDeleted）
- `AutoFillHandler`：MetaObjectHandler 自动填充
- `JwtUtil`：Token 签发与解析
- `PageResult<T>`：分页结果封装

## 4. 前端对接

前端（**独立包**，由前端负责人维护）统一经网关 `http://127.0.0.1:8080` 访问，路径带 `/api` 前缀。
后端只提供 JSON API，交付给前端的契约如下：

| 能力 | 接口 |
|------|------|
| 登录 | POST /api/auth/login |
| 注册 | POST /api/auth/register |
| 视频列表 | GET /api/videos/page |
| 视频详情 | GET /api/videos/{id} |
| 投稿用户 | GET /api/videos/{id}/owner（视频服务通过 OpenFeign 调用 system-service） |
| 播放地址 | GET /api/videos/{id}/play-url |
| 视频上传 | POST /api/videos/upload |
| 视频转码(提交任务) | POST /api/videos/{id}/transcode |
| 转码任务状态 | GET /api/videos/{id}/transcode-task |
| 下载地址 | GET /api/videos/{id}/download |

前端侧需自行实现：从本地存储取 token 并注入 `Authorization` 头；收到 **401** 时跳转登录页。
（原 uni-app 的 `utils/request.ts` 已随前端包移出本仓库，位于 `../vidora-mobile/`。）

## 5. 数据初始化

按服务执行 `SQL/` 下 7 个脚本建库建表（详见 README.md 快速开始）。

## 6. 网关鉴权与 Nacos 配置

### 6.1 网关 JWT 鉴权过滤器（`GatewayAuthFilter`）
- 位置：`../vidora-gateway/.../filter/GatewayAuthFilter.java`，实现 `GlobalFilter` + `Ordered`（order=-1，早于路由）。
- 白名单：路径以 `/api/auth/**` 开头直接放行（登录/注册无需 Token）。
- 其余请求必须携带 `Authorization: Bearer <token>`，校验失败返回 **401**（前端按 401 跳登录）。
  **注意这个 401 没有响应体**：`GatewayAuthFilter.unauthorized()` 只设状态码和 `Content-Type: application/json`
  就 `setComplete()`，不写 `ApiResult`。业务服务经 `GlobalExceptionHandler` 抛出的错误则**有** body
  （`{code,message,data}`，且 HTTP 状态码与业务码一致）。因此只看 body 里 `code` 字段的客户端
  （如 uni-app：`uni.request` 对 4xx/5xx 也走 `success` 回调）必须同时判 `statusCode`，否则会把网关 401 当成成功。
- 校验通过后将 `userId` 以 `X-User-Id` 请求头透传给下游微服务，业务服务可直接取用。
- 复用 `../vidora-common/common-core` 的 `JwtUtil`（网关以「排除 spring-boot-starter-web」方式引入公共核心，避免与 WebFlux 冲突）。

### 6.2 配置管理现状：每服务一个 `application.yml`，Nacos 只做服务发现

- **配置中心暂未接入。** 每个服务（含网关）的全部配置都收在自己 `src/main/resources/application.yml` 这一个文件里，
  用 `# Datasource` / `# Redis` / `# MyBatis-Plus` / `# SpringDoc` / `# Logging` 这样的分段注释隔开。
- Nacos 只保留 `spring.cloud.nacos.discovery.server-addr`；`spring.cloud.nacos.config` 整块已删除，
  9 个服务的 pom 里也**不再有** `spring-cloud-starter-alibaba-nacos-config`。
- **为什么依赖必须一起删，而不是留着不用**：SCA 2025.1.0.0 的 `NacosConfigDataMissingEnvironmentPostProcessor`
  （其 `getPrefix()` 返回 `nacos:`）只要发现该 starter 在 classpath 上、而 `spring.config.import` 里又没有 `nacos:` 条目，
  就让服务**启动直接失败**。所以「删掉 import 但留着依赖」是个会让 9 个服务全起不来的陷阱。
  依赖删掉后这个失败模式不可能再发生；`nacos-discovery` 保留，父 POM 里 Nacos 3.2.4 全家桶的版本覆盖也保留。
- **重新启用配置中心的步骤**：
  1. 9 个 pom 加回 `spring-cloud-starter-alibaba-nacos-config`；
  2. 每个 `application.yml` 加回 `spring.cloud.nacos.config.server-addr` 与 `file-extension: yml`；
  3. 加回 `spring.config.import: - optional:nacos:${spring.application.name}.yml`
     （`optional:` 前缀保证 Nacos 未就绪时仍能启动，仅告警）；
  4. dataId 约定为 `<服务名>.yml`，放 public 命名空间；生产可改 `spring.cloud.nacos.config.namespace` 指定命名空间。

  那些分段注释就是为这一步留的切割线：按段拆回 `application-{concern}.yml` 即可与 dataId 一一对应。
- 凭据与地址一律写成 `${环境变量:开发默认值}`（`jwt.secret`、数据库/Redis 口令、MinIO AK/SK、`rocketmq.*`），
  因此容器（`deploy/docker-compose.yml`）与裸机/IDE 能用同一套变量名注入，不必改文件。

### 6.2.1 微服务之间如何调用

- **客户端访问后端**：统一进入 `gateway-service`，由 Spring Cloud Gateway 按 `/api/*` 路径路由到目标服务。
- **服务发现**：使用 Nacos Discovery；网关里的 `lb://auth-service`、`lb://system-service` 等逻辑服务名由 Spring Cloud LoadBalancer 解析为实例地址。
- **当前代码状态**：已在 `video-service` 集成 Spring Cloud OpenFeign，并提供 `SystemUserClient` 调用 `system-service` 的基础客户端；其他服务按实际调用关系逐步增加客户端，不为没有调用需求的服务强行引入依赖。
- **同步调用约定**：使用 Spring Cloud OpenFeign + Nacos，例如 `@FeignClient(name = "system-service")`，禁止写死 IP/端口。Feign 统一配置连接超时、读取超时、日志级别和后续降级策略。
- **身份透传**：`video-service` 的 Feign `RequestInterceptor` 会透传 `X-User-Id`、`X-User-Roles`、`X-User-Permissions`，下游服务继续复用 `HeaderAuthenticationFilter` 做接口授权。
- **异步调用**：转码任务已走 RocketMQ（`vidora-transcode-task`，见 6.4 / 8.2）。**跨服务事件尚未接**——转码完成通知、计数更新、弹幕入库等仍待落地；这类不要求立即返回的场景应发事件消息，避免服务之间形成同步调用链。
- **数据边界**：服务之间只调用对方 API 或订阅事件，不直接访问对方数据库表；认证服务与系统服务目前共享既有身份表是迁移阶段安排，后续可拆分为独立身份库。

### 6.3 Nacos 3.2.4 与 SCA 2025.1.0.0 的配套结论（已验证）

- **选型**：Spring Cloud Alibaba **2025.1.0.0**（2025.1 线在镜像上的唯一版本）+ Nacos client **3.2.4**（2026-08-28 发布，3.2.x 最新稳定版）。
- SCA 2025.1.0.0 默认捆绑的是 nacos-client **3.1.1**，因此必须在父 POM 显式覆盖；且覆盖的是**全家桶**
  （`nacos-client` / `nacos-client-basic` / `nacos-auth-plugin` / `nacos-encryption-plugin` / `nacos-log4j2-adapter`），
  避免只覆盖主构件、子构件回落 3.1.1 导致 `NoSuchMethodError`。
- **兼容性已实测**（`javap` 对比 3.1.1 → 3.2.4 公开 API）：

  | 类 | 差异 |
  |---|---|
  | `NamingService` / `NamingFactory` / `ConfigFactory` / `PropertyKeyConst` / `Instance` | 完全一致 |
  | `ConfigService` | 仅新增 default 方法 `getConfigWithResult` |
  | `Constants` | 仅新增常量 `TAG_V2`、`TAG_V2_PREFIX` |

  差异**全部为新增、无删除** ⇒ SCA（按 3.1.1 编译）在 3.2.4 上二进制兼容。
- **依赖树实测**：9 个模块的 nacos 构件统一为 3.2.4，无版本混杂、无 `omitted for conflict`。
  （`com.alibaba.nacos:logback-adapter:1.1.5` 为独立版本号的日志桥接件，不属 nacos 版本列车。）
- ⚠️ **部署注意**：Nacos **Server 也必须 3.2.4**（3.x client 不兼容 2.x server）；Nacos 3.x 要求运行环境 JDK 17+。

### 6.4 RocketMQ 接入方式：用原生 client，不用 spring starter

- **不用 `rocketmq-spring-boot-starter`**：2.3.x 是**按 Spring Framework 5.3.27 编译**的、尚未适配 Boot 4，
  而它的 `RocketMQAutoConfiguration` 又会被 Boot 4 的 `AutoConfiguration.imports` 加载——属于「一引入就有启动风险，
  但功能一行没用到」的依赖。因此父 POM 只管理引擎客户端版本：

  ```xml
  <rocketmq.client.version>5.3.2</rocketmq.client.version>
  ```

  > 版本号辨析：starter 的 `2.3.x` ≠ 引擎版本，2.3.4 内部依赖的引擎正是 `rocketmq-client` **5.3.2**。
  > 本项目直接依赖引擎客户端，所以 POM 里出现的就是 `5.3.2`。

- **不用 gRPC 版 `rocketmq-client-java`**：它要求 broker 侧开启 Proxy（8081 端口），
  与当前 `namesrv:9876 + broker:10911` 的 remoting 拓扑不匹配；且该构件与引擎版本不同步（5.3.2 未发布）。
  原生 `rocketmq-client` 直连 namesrv/broker，正好对上 `deploy/docker-compose.yml` 已暴露的端口。

- **代码落点**（均在 `vidora-modules/vidora-video`）：

  | 类 | 职责 |
  |---|---|
  | `mq/MqProperties` | `rocketmq.*` 配置绑定 |
  | `mq/RocketMqConfig` | 装配 `DefaultMQProducer` / `DefaultMQPushConsumer` 生命周期，仅 `rocketmq.enabled=true` 时生效 |
  | `mq/TranscodeTaskPublisher` | 投递：有 MQ 走 MQ，没有或投递失败退回本地线程池 |
  | `mq/TranscodeTaskListener` | `MessageListenerConcurrently`，在消费线程里**同步**跑完转码再 ack |
  | `service/impl/TranscodeTaskRunner` | 转码执行单元，同步、不带重试（重试策略归投递方） |

- **开关**：`rocketmq.enabled`（容器内由 `ROCKETMQ_ENABLED=true` 打开）。
  关闭时容器里没有 producer bean，`TranscodeTaskPublisher` 自动回落到 `transcodeExecutor`，
  **本地开发不需要起 broker**。
- **依赖副作用**：`rocketmq-client` 会带入 `io.netty:netty-all`，被 Boot BOM 统一到 4.2.15.Final
  （与 lettuce 已用的版本一致，不产生新的 netty 版本冲突），代价是镜像多出若干 netty 子模块。

## 7. 认证授权与菜单控制（Spring Security + RBAC）

采用 **网关认证 + 业务服务授权** 的分工：

```
前端 → 网关(GatewayAuthFilter 校验 JWT) → 透传身份头 → 业务服务(Spring Security 授权)
```

### 7.1 数据模型（写在 `SQL/01_user_service.sql`）
`sys_user` → `sys_user_role` → `sys_role` → `sys_role_menu` → `sys_menu`

`sys_menu` 同时承担菜单与权限：`menu_type`=1目录/2菜单/3按钮；`permission_code` 为权限标识（如 `video:upload`）。
按钮(3)不进菜单树，只作为接口级鉴权标识。

初始数据：角色 `ROLE_ADMIN`（全菜单）、`ROLE_USER`（视频列表+评论，无删除与系统管理）；示例菜单 11 条。

### 7.2 认证（auth-service）
- `POST /api/auth/login` 登录时从 DB 加载该用户的**角色编码**与**权限标识**，写入 JWT
  （`JwtUtil` 新增 `roles` / `perms` 两个 claim）。
- 返回 `LoginVO`：`token + userId + nickname + roles + permissions`。
- 注册时自动赋予默认角色 `ROLE_USER`（role_id=2）。

认证代码位于 `auth`，系统管理代码位于 `../vidora-modules/vidora-system`。认证服务当前对
`sys_user`、`sys_role`、`sys_menu` 采用认证所需的身份投影，后续可在不改变 API 契约的情况下演进为独立身份库。

### 7.3 网关透传（gateway-service）
`GatewayAuthFilter` 校验通过后透传三个头（常量见 `SecurityHeaders`）：

| 头 | 含义 |
|---|---|
| `X-User-Id` | 用户ID |
| `X-User-Roles` | 角色，逗号分隔 |
| `X-User-Permissions` | 权限标识，逗号分隔 |

白名单 `/api/auth/**` 放行。网关将认证请求路由到 `auth-service`，将 `/api/menus/**`、`/api/roles/**`、`/api/users/**` 路由到 `system-service`。

### 7.4 授权（各业务服务）
- 7 个业务服务引入 `spring-boot-starter-security` 并新增 `SecurityConfig`：
  无状态(STATELESS)、关闭 CSRF、放行 `/auth/**` 与文档路径，其余需认证；
  401/403 统一返回 `ApiResult` JSON。
- `HeaderAuthenticationFilter`（common/core）从上述请求头还原 `SecurityContext`，
  角色与权限都作为 `GrantedAuthority`，因此支持 `hasRole(...)`、`hasAuthority(...)`、`@PreAuthorize`。
- `UserContext`（ThreadLocal）供业务代码直接取当前 userId / 角色 / 权限。
- 示例：`POST /api/videos/upload` 标注 `@PreAuthorize("hasAuthority('video:upload')")`。

> 注：`../vidora-common/common-core` 只引入 `spring-security-core`（纯 API，无自动配置），
> 因此 WebFlux 网关不受影响；自动配置的 starter 只加在业务服务。

### 7.5 菜单控制接口
| 接口 | 说明 |
|---|---|
| `GET /api/menus` | **当前登录用户的菜单树**（前端据此渲染菜单） |
| `GET /api/menus/tree` | 全部菜单树（需 `menu:list`） |
| `GET /api/menus/role/{roleId}` | 某角色已授权菜单ID |
| `POST /api/menus/role/{roleId}` | 给角色授权菜单，全量覆盖（需 `menu:assign`） |
| `POST/PUT/DELETE /api/menus` | 菜单增删改（需 `menu:add` / `menu:edit` / `menu:delete`） |
| `GET /api/roles` | 角色列表（需 `role:list`） |
| `POST /api/roles/user/{userId}` | 给用户分配角色，全量覆盖（需 `role:assign`） |

## 8. 视频存储与 FFmpeg 多媒体处理（异步多清晰度 HLS 流水线）

视频服务（video-service，8102）负责视频的上传、存储、转码与播放地址分发。
本轮按**大厂思路 + 开源项目实践**重构了转码链路：从"上传即同步转码"改为
**上传落对象存储 → 提交转码任务 → 异步 Worker 池跑 ffmpeg → 产物回传对象存储 → 暴露主播放列表**，
与 Jellyfin 的 `MediaEncoder` 转码作业管理、以及 ffmpeg 生产级 ABR 配方对齐。

### 8.1 存储抽象（MinIO / 本地磁盘 可切换）
- `StorageService`（接口：`upload` / `downloadUrl` / `publicUrl` / `delete` / `fetchToTempFile` / `exists` / `listObjectNames` / `compose` / `deleteAll(默认方法)` / `uploadDir(默认方法)`）。
- 两种实现通过 `@ConditionalOnProperty(name="storage.type")` 自动切换，**切换只改 `application.yml`，业务代码零改动**：
  - `MinioStorageService`（默认，`storage.type=minio`）：MinIO SDK 8.5.17 上传对象；`downloadUrl` 返回**限时预签名 URL**（默认 3600s）；`publicUrl` 返回 `endpoint/bucket/object`（供 HLS 分片 / CDN 引用，无签名）。
  - `LocalStorageService`（`storage.type=local`）：写本地目录（`storage.local-dir`）；`publicUrl` 经 `storage.local-public-base` 拼接（需配套静态资源服务 / Nginx 映射）。
- `uploadDir(...)`：把 ffmpeg 生成的 HLS 切片目录**整体回传对象存储**（保留相对路径），是"对象存储 + CDN"分发的标准做法。
- `exists` / `listObjectNames` / `compose` / `deleteAll` 为分片上传服务（见 8.4）：
  - `exists` 用 `statObject`，只有明确的 `NoSuchKey`/404 才返回 `false`——**连接失败必须抛异常**。若把"MinIO 挂了"当成"文件不存在"，秒传会误判文件丢失，逼用户重传整个文件。
  - `compose` 用服务端 `composeObject`，分片数据**不流经应用进程**；本地实现则先拼到同目录临时文件再 `rename`，避免中途失败在目标对象名上留下半个损坏文件。
  - `deleteAll` 用 `removeObjects` 批量删除（其返回值是惰性的，必须遍历才真正发请求）。合并后要清掉成百上千个分片，逐个 `delete` 就是同样多次 HTTP 往返。
- `video_info.storage_path` 统一存对象名，便于两种存储互换。

### 8.2 转码架构：异步解耦 + 多清晰度 HLS（参考 Jellyfin / 大厂）
- **状态机**：`video_transcode_task` 表记录每次转码任务，状态 `0 待处理 → 1 处理中 → 2 成功 / 3 失败`，带 `progress`（0-100）、`retry_count`、`error_msg`。
- **任务投递**：`TranscodeService.submit(id)` 先建任务落库，再交 `TranscodeTaskPublisher` 投递。
  启用 MQ 时消息进 `vidora-transcode-task` topic（消息体只有 taskId，任务详情一律以数据库为准），
  由 `TranscodeTaskListener` 在 **broker 的消费线程里同步**执行——不能调 `@Async` 方法，
  否则会立刻 ack 而任务还在跑，进程一重启就丢，等于没接 MQ。
  未启用 MQ（`rocketmq.enabled=false`）或投递失败时，退回 `transcodeExecutor` 线程池（`AsyncConfig`）。
  前端通过 `GET /api/videos/{id}/transcode-task` **轮询**状态/进度。
- **失败重试**：两条路径各自持有重试策略，执行单元 `TranscodeTaskRunner` 本身不重试。
  - MQ 路径：抛异常 → 返回 `RECONSUME_LATER`，由 broker 按退避重投；重投次数达到 `rocketmq.max-reconsume-times`（默认 3）后置 `FAILED` 并 ack，避免任务永远停在「处理中」。
  - 本地路径：按 `transcode.max-retry`（默认 2）在同一线程内重试，耗尽后置 `FAILED`。
  - `video_transcode_task.retry_count` 记录重入次数（领取时已是「处理中」即视为一次重试），便于排查反复失败的任务。
- **幂等**：MQ 是至少一次投递，`TranscodeTaskRunner.run` 对已是终态（成功/失败）的任务直接跳过；`submit` 本身也对同视频的进行中任务做幂等返回。
- **生产演进**：MQ 已就位，剩下的是把消费者拆成**独立转码集群 / 云 MPS（阿里云 MPS / 腾讯云 MPS）**——
  转码是 CPU 重活，与 Web 请求同进程会互相抢资源。因为执行单元已与调度方式解耦，
  拆分时只需把 `TranscodeTaskRunner` 及其依赖搬进一个只跑消费者的部署单元，业务接口与状态机不变。

### 8.3 FFmpeg 多媒体处理
- `FfmpegService` 通过 `ProcessBuilder` 调用**本机** `ffmpeg` / `ffprobe` 命令行（不走数百 MB 的 JNI 依赖）：
  - `probe()`：ffprobe 探测时长 / 分辨率 / 编码（JSON 输出解析）。
  - `transcodeToAdaptiveHls()`：**一步产出多清晰度自适应 HLS**——单命令 `-filter_complex split+scale` + `-var_stream_map` + `-master_pl_name master.m3u8`，生成 ABR 阶梯（默认 360p/480p/720p/1080p）+ 主播放列表；支持 `none`（libx264 软编）/ `cuda`（h264_nvenc）/ `qsv`（h264_qsv）三档**硬件加速矩阵**（参考 Jellyfin），切片支持 `fmp4`（CMAF，现代默认）/ `ts`。VBV 码率约束（maxrate≈1.1×目标、bufsize≈2×maxrate）+ Closed-GOP（keyint=48）保证 HLS 带宽估算准确、自适应切档平滑。
  - `thumbnail()`：抽取封面（取第 1 秒）。
- `FfmpegProperties`：`ffmpeg-path` / `ffprobe-path` / `hls-time` / `work-dir`。
- `TranscodeProperties`：`enabled` / `hwaccel` / `segment-type` / `hls-time` / `master-name` / 线程池大小 / `max-retry` / `renditions`（清晰度阶梯，默认 4 档）。

### 8.4 分片上传：断点续传 + MD5 秒传

大文件单次 POST 在弱网下几乎必然失败，且失败即全部重来。`MultipartUploadService` 把上传拆成
**init → 并发传 chunk → complete** 三步，会话状态记在 `video_multipart_upload`。

- **会话状态机**：`0 上传中 → 1 分片已收齐 → 2 已合并`。对外只暴露 `uploadId`（UUID），不暴露自增主键。
- **分片命名**：`{objectKey}.parts/%05d`。5 位零填充是为了让对象名的**字典序等于数值序**——
  合并时按下标顺序取分片，绝不能依赖存储的列举顺序，顺序错一位产出的就是一个不报错但播放不了的坏文件。
- **分片大小的硬约束**：最小 5 MiB（S3 `composeObject` 要求除末片外每片 ≥ 5 MiB），
  最多 10000 片（单次 compose 的源对象上限），两者共同决定单文件上限约 50 GiB。超出则 init 直接拒绝，让前端调大 `chunkSize`。
- **完整性以存储为准**：`progress` / `complete` 都是**实时列举对象存储**得到已收分片下标，
  不用库里的 `completed_chunks`。客户端超时重传同一下标、或多个分片并发到达时，计数可能虚高，
  它只作列表页的进度提示；计数自增走 `set completed_chunks = completed_chunks + 1` 的条件更新，不用读改写。
- **幂等**：同一下标重复上传是覆盖写，只在分片首次出现时才自增计数；`complete` 在会话已回写 `video_id` 时直接返回已有视频，不会重复建记录。
- **一次上传尝试 = 一行会话**（不是一用户一文件一行）。表上只有普通索引 `idx_user_hash(user_id, file_hash)`：
  同一文件再投一次要能开新会话、建新视频，唯一键会把它挡回第一次的那个视频。
  断点续传只是从该索引里挑**最近一条未合并且分片参数一致**的会话继续传；分片参数变了则旧分片边界对不上，
  直接开新会话重传，而不是报错卡住用户。
- **MD5 秒传**：init 时按 `(file_hash, file_size, status=2)` **跨用户**扫最近若干条已合并会话，
  取第一个对象确实还在的 `objectKey`，**另插一行已合并的新会话**指向它，前端一片都不用传。
  跨用户是刻意的——秒传的价值就在于别人传过的文件不用再传一遍。
  - 复用发生在**存储层 blob**，不是会话记录：秒传绝不能改写别人那一行，否则原投稿者的会话就被顶掉了。
  - 代价：多条视频共享同一个存储对象，任何一方触发对象删除都会连带影响其他方的视频。
    **目前系统没有删源片的入口**；真要加删除，必须先给对象做引用计数。
  - 库里的 `status=2` 可能过期（对象被清理、换了 bucket），所以**必须实测 `exists`**，不能只信数据库；
    多扫几条而不是只看最新一条，就是为了容忍「最新那行的对象刚被清掉」。
  - `complete` 对已合并会话会**再查一次 `exists`**——init 到 complete 之间对象可能消失，
    否则会登记出一个 `storage_path` 指向空气的视频。
- **归属校验**：`uploadId` 猜不到，但不能只靠这一点——日志、代理、浏览器历史都可能把它泄露出去。
  所有操作都比对 `user_id`，不匹配返回 403。
- **废弃会话需要清理**：每次 init 都可能留下一行没人管的在传会话（用户关掉页面、分片参数变更作废），
  连同 `.parts/` 下的分片一起占着存储。目前**没有清理任务**，见第 14 节待完成项。
- **合并与入库不包在数据库事务里**：MinIO compose 与 ffprobe 都是分钟级操作，事务里长时间占着连接会拖垮连接池。
  合并成功后先落 `VideoInfo`（状态=待转码）再回写 `video_id`；中间失败则重试 `complete` 会重新合并一遍，无副作用。
- 合并完成后的入库复用 `VideoInfoService.registerStoredVideo(draft)`，与普通上传共享同一套
  「补 videoKey → 初始化计数 → ffprobe 探测 → 落库」流程，两条上传路径不会各自漂移。

### 8.5 上传 / 转码接口（video-service）
| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/videos/upload` | POST(multipart) | `video:upload` | 落存储 + ffprobe 探测元信息写 `video_info`；若 `transcode.enabled=true` 自动提交异步转码任务 |
| `/api/videos/multipart/init` | POST | `video:upload` | 用整文件 MD5 换 `uploadId`；`instant=true` 表示秒传命中，可直接调 complete；否则返回已收分片下标供断点续传 |
| `/api/videos/multipart/chunk` | POST(multipart) | `video:upload` | 上传单个分片（`uploadId` + `chunkIndex`），可并发、可重复 |
| `/api/videos/multipart/progress` | GET | 登录即可 | 返回服务端实测已收到的分片下标；刷新页面或换设备后靠它接着传 |
| `/api/videos/multipart/complete` | POST | `video:upload` | 服务端合并分片 + 建 `video_info`；若 `transcode.enabled=true` 同时提交转码任务 |
| `/api/videos/{id}/transcode` | POST | `video:transcode` | 手动提交转码任务，返回任务 ID（幂等：已有进行中任务则直接返回） |
| `/api/videos/{id}/transcode-task` | GET | 登录即可 | 查询最新转码任务状态/进度（前端轮询） |
| `/api/videos/{id}/download` | GET | 登录即可 | 返回下载 / 预签名 URL |
| `/api/videos/{id}/play-url` | GET | 登录即可 | 已转码返回 `master.m3u8` 公共地址，否则返回源文件地址 |
| `/api/videos/{id}` | GET | 登录即可 | 视频详情（含 `hlsUrl` / `coverUrl` / `status`） |

> 播放流程：上传 → 轮询 `transcode-task` 至 `status=2` → 用 `play-url`（即 `master.m3u8`）交给 HLS.js / 原生 `<video>` 播放，播放器按带宽自动切换清晰度。

### 8.6 部署要点：FFmpeg 是**服务端 CLI 依赖**（非 Java 依赖）
- **本机（开发机）当前未安装 ffmpeg/ffprobe**（`where ffmpeg` 验证找不到）。本地只跑上传/列表（不触发探测与转码）不影响编译与启动；一旦触发转码/探测会抛"未安装"异常。
- **生产服务器**：凡运行 video-service 且启用转码的节点**必须预装 ffmpeg + ffprobe**；或按 8.2 演进把转码拆到**专用转码集群 / 云 MPS**，业务节点不装 ffmpeg。
- 二进制缺失时 `FfmpegService` 抛出明确错误（含"请确认已安装并配置路径"），便于定位。

### 8.7 相关表
- `SQL/02_video_service.sql`：
  - `video_info`：`storage_path` / `hls_url` / `cover_url` / `status`（3=已发布）。
    `file_hash` 上的索引由 `UNIQUE KEY uk_file_hash` 降级为普通 `KEY idx_file_hash`：
    秒传复用的是**存储层 blob**，不是视频记录——同一文件被不同用户（或同一用户多次）投稿是合法的，
    各自要有独立的 `video_info` 行，唯一约束会让第二次投稿直接插入失败。
  - `video_transcode_task`：**单次转码任务 = 一次多清晰度 HLS 转码**（含全部档位），字段 `video_id` / `video_key` / `status` / `progress` / `source_path` / `hls_path`(master objectName) / `renditions`(JSON) / `error_msg` / `retry_count` / `finished_at`。
  - `video_multipart_upload`：分片上传会话，见 8.4。本轮补了 `user_id` / `file_size` 两列，
    并把 `uk_file_hash(file_hash)` 换成普通索引 `idx_user_hash(user_id, file_hash)`——
    **一次上传尝试一行**，唯一键（无论建在 hash 上还是 user+hash 上）都会让同一文件的第二次投稿无处落脚。
    该表**没有 `is_deleted` 列**，所以实体不继承 `BaseEntity`，靠状态机流转而不是逻辑删除。
  - 另有 `video_audit_record`（审核），供后续完善。

## 9. 互动服务（interact-service）

覆盖四块：评论（含楼中楼）、点赞/收藏/分享、弹幕、播放与计数汇总。
原先这个模块只有一个空的 `CommentController`，`SQL/04_interact_service.sql` 里的四张表全都没落地。

### 9.1 互动动作：状态翻转而不是增删行

`interact_action` 一行代表「某用户对某对象做过某动作」，`uk_user_target_action(user_id, target_type, target_id, action_type)`
决定了一个用户对一个对象的一类动作**只能有一行**。于是：

- 「取消点赞」不是 DELETE，而是把 `status` 从 1 翻回 0。真删了行，下次点赞又要 INSERT，
  自增 id 白白涨；更要紧的是取消/点赞来回切时，行本身承载的「历史上点过」信息就没了。
- 实体**不继承 `BaseEntity`**：表上没有 `is_deleted` 列，而且逻辑删除会和「取消靠 status 表达」打架——
  逻辑删除掉的行还占着唯一键，取消后再想点赞会直接被 `uk` 挡回来。
- 翻转写成**条件 UPDATE**（`... WHERE ... AND status <> ?`），影响行数就是「状态是否真的变了」的信号。
  不用读-改-写：两个并发请求同时读到旧值再各自写回会丢掉一次变更。

### 9.2 冗余计数只在状态真的翻转时移动

三处计数落点，都由 `InteractActionServiceImpl#applyDerivedCounters` 统一驱动：

| 动作 | 对象 | 落到哪 |
|------|------|--------|
| LIKE / SHARE | video | `interact_play_count` 当天那行的 `like_count` / `share_count` |
| LIKE | comment | `interact_comment.like_count` |
| FAVORITE | 任意 | 不进任何计数列（只影响「我的收藏」列表，没有对外展示的总数） |

「只在翻转时移动」是这里唯一的不变量：重复的点赞请求 `changed == false`，
若也去加一次，连点几下就能把 `like_count` 抬到比真实点赞数高，而且再也回不去。
并发插入撞上唯一键（`DuplicateKeyException`）时按**成功**返回给调用方，但计数**不加**——
抢到锁的那个请求已经加过了。

所有自增/自减都走 `SET x = x + 1` / `SET x = GREATEST(x - 1, 0)` 的固定 SQL 字面量，
不拼接变量、不读改写；减法用 `GREATEST` 兜住，重复的取消请求不会把计数压成负数。

**刻意不做的事**：点赞时不校验 targetId 对应的视频/评论是否真的存在。视频在 `video_service` 库里，
跨服务校验等于给最热的写路径加一次远程调用；编造的 targetId 只会留下一行没人读的死数据，
因为计数总是和对象一起被查出来的。

### 9.3 评论树：parent_id + root_id 双层扁平化

`interact_comment` 同时存 `parent_id`（直接父级，用来渲染「A 回复 B」）和 `root_id`（所属顶层评论）。
`root_id` 由服务端从父级推导，**接口不接受客户端传**——随便填一个 rootId 就能把回复挂到别人的评论树下：

```
父级是顶层（root_id = 0） → root_id = 父级 id
父级本身是回复            → root_id = 父级的 root_id
```

于是不管回复套多少层，整棵树都是一条 `WHERE root_id = ?` 就能捞出来的扁平结构，不需要递归查询。
`parent_id = root_id = 0` 表示顶层评论。

- **顶层列表**只取 `parent_id = 0 AND status = 1`；热门排序 `like_count DESC, id DESC`——
  必须带一个唯一的兜底排序键，点赞数相同的大批评论在翻页时会重复出现或被跳过。
  默认排序用自增 `id` 而不是 `create_time`：同一秒内可能有多条，id 天然唯一且是主键。
- **预览回复**（每条顶层评论带前 3 条）由 `CommentMapper#previewReplies` 一次查完整页，
  用 `ROW_NUMBER() OVER (PARTITION BY root_id ORDER BY id)` 在库里截断。
  逐条查是 N+1（一页 20 条 = 20 次往返），全捞回来在内存里分组则会被一条几千回复的热门评论打爆。
  注意注解式 `@Select` 是**裸 SQL**，逻辑删除插件不会自动追加 `is_deleted = 0`，条件得自己写。
- **删除**：`is_deleted` 逻辑删除（不是改 status，`status` 只表达审核态）。作者本人或审核者可删；
  审核者身份由控制器算好后当参数传进来——`@PreAuthorize` 表达不了「本人 **或** 有权限」这种或关系，
  而且 Service 层不读权限上下文才好复用、好测。删一条回复要同时回滚根评论的 `reply_count` 和视频的评论数。
- **`listReplies` 会先检查根评论可见性**：根被删或被屏蔽时返回空页。
  否则 `listByVideo` 已经不显示它了，直接调回复接口却还能翻到整棵子树。
- `CommentView` **不带昵称头像**：用户信息在 `system-service` 库里。逐条评论发一次 Feign
  等于一页 20 次远程调用，该由 BFF 拿到一页评论后批量解析 userId 再拼装。

### 9.4 弹幕

只追加的时间线数据，屏蔽靠 `status` 而不是逻辑删除，所以实体同样不继承 `BaseEntity`（表上也没有 `update_time`）。

- `appear_time` 用 `DECIMAL(10,3)` / `BigDecimal`：float 的累积误差会让同一时间点的弹幕排序抖动。
  入库前统一 `setScale(3, HALF_UP)`，多出来的小数位否则会被 MySQL 静默截断。
- 颜色统一转大写，避免同一个颜色在库里存出 `#ffffff` / `#FFFFFF` 两种写法。
  样式字段缺省时补上 DDL 里的列默认值（`#FFFFFF` / 25 / 0），返回给前端的对象才是完整的。
- 拉取查询的过滤列和排序列都是 `idx_video_time(video_id, appear_time)`，不用额外排序。
  时间窗口两端都可省略；起点晚于终点直接拒绝。
- 单次拉取上限 `MAX_PER_LOAD = 3000`：不设的话一个 `limit=1000000` 的请求就能把整张表拉出来。

### 9.5 播放计数：按天分行 + 去重窗口 + 只靠 TTL 过期

`interact_play_count` 的 `uk_video_date(video_id, stat_date)` 是**故意**的写热点规避：
热门视频每秒都在被播放，若全表只有一行，所有播放都挤在同一行上抢行锁。
按天分行把写入摊到不同行，读取时 `SUM` 一遍——读远比写便宜，而且读还能被缓存挡住。

- 累加走 `INSERT ... ON DUPLICATE KEY UPDATE x = GREATEST(x + VALUES(x), 0)`：
  一条 SQL 完成「今天还没行就建、有行就加」，不用先查再决定，也避免计数为负。
- **播放去重**：`SETNX vidora:interact:play-dedupe:{videoId}:{userId}` + 5 分钟 TTL，
  挡住脚本连点和播放器的心跳重播。未登录的播放**不去重**，照实计数——
  没有稳定身份可依据，宁可让匿名播放有点水分，也不能因为拿不到 userId 就把这部分播放全丢掉。
- **Redis 不可用时放行计数**：播放数多算一次的代价，远小于「Redis 挂了导致播放上报接口直接报错」。
- `totals` 上的 `@Cacheable`（`interact:video-totals`，TTL 60 秒）**只靠过期，不做写时失效**：
  播放上报是高频写，每次都 evict 等于把缓存废掉，热门视频会退化成「每播放一次就 SUM 一遍全部日行」。
  计数晚一分钟可见对播放数没有影响。
- 缓存层整体降级见 `RedisCacheConfig#errorHandler`：读写失败只记 warn，不把 Redis 故障放大成每个接口的 500。

### 9.6 接口与权限

网关 `interact-service` 路由放行 `/api/comments/**,/api/actions/**,/api/danmaku/**,/api/play-counts/**`
（`StripPrefix=1`，所以服务内控制器不带 `/api` 前缀）。

| 接口 | 方法 | 权限 |
|------|------|------|
| `/comments/video/{videoId}`、`/comments/replies/{rootId}` | GET | 登录即可 |
| `/comments` | POST | 登录即可 |
| `/comments/{id}` | DELETE | 本人或 `comment:delete` |
| `/comments/{id}/audit` | PUT | `comment:audit` |
| `/actions`、`/actions/counts`、`/actions/favorites` | PUT/GET | 登录即可 |
| `/danmaku/video/{videoId}` | GET | 登录即可 |
| `/danmaku` | POST | 登录即可 |
| `/danmaku/{id}/status` | PUT | `danmaku:manage` |
| `/play-counts/{videoId}` | POST/GET | 登录即可 |

发评论、发弹幕、点赞属于「普通用户的基本操作」，不占权限位；
`SQL/01_user_service.sql` 里只补了两个审核类按钮权限（菜单 id 18 `comment:audit`、19 `danmaku:manage`）并授权 ROLE_ADMIN。
`comment:audit` / `danmaku:manage` 是**接口级**权限，`visible = 0`，不进菜单树。

### 9.7 相关表

`SQL/04_interact_service.sql`，四张表本轮全部落地：

- `interact_action`：见 9.1。`idx_target_action(target_type, target_id, action_type, status)`
  是分组计数的覆盖索引，`COUNT(*) GROUP BY action_type` 不用回表。
- `interact_comment`：见 9.3。`idx_video_id(video_id, status, create_time)`、`idx_root_id(root_id)`。
- `interact_danmaku`：见 9.4。`idx_video_time(video_id, appear_time)`。
- `interact_play_count`：见 9.5。`uk_video_date(video_id, stat_date)`。

### 9.8 测试

`vidora-interact` 下 68 个单元测试，全部脱离 Spring 容器（mock Mapper + 反射装配，见 `MpTestSupport`）：

- `InteractActionServiceImplTest`（15）：翻转语义、计数只在真翻转时移动、并发插入不重复计数、未登录时布尔位保持 null。
- `CommentServiceImplTest`（32）：root_id 推导、跨视频/审核中/不存在的父级、删除权限、可见性、预览回复不做 N+1。
- `DanmakuServiceImplTest`（12）：默认值、时间刻度、窗口校验、拉取上限。
- `PlayCountServiceImplTest`（9）：去重窗口、Redis 故障放行、匿名播放、全零增量的空操作。

mock 的 Mapper **不会执行任何条件**，所以「过滤/排序/钳制有没有写对」这类断言靠把条件构造器的
SQL 片段（`getCustomSqlSegment()` / `getSqlSet()`）抓出来比对；而「状态是否真的翻转」这类
依赖 SQL 谓词结果的行为，则由测试显式设定 `update` 的影响行数来模拟。

## 10. 消息服务（message-service）

三类消息共用一张 `message_record`，靠 `msg_type` 区分；私信额外有一张会话表做列表聚合。
原先这个模块只有一个把 `IService` 直接摊出去的壳控制器——`GET /messages/page` 不带任何
`receiver_id` 过滤，任何登录用户都能翻遍全站私信；`POST /messages` 收一个裸 `MessageRecord`，
收信人可以随便填。这两个洞连同壳控制器一起删掉了，现在所有查询都以 `UserContext.getUserId()`
为收信人，写入路径的 `senderId` 一律由服务端指定。

### 10.1 三类消息与发送资格

| msg_type | 含义 | 谁能发 |
|----------|------|--------|
| 1 SYSTEM | 站内系统通知（审核结果、公告） | 仅 `message:send` 权限，`sender_id` 恒为 0 |
| 2 INTERACT | 互动通知（有人赞了你、评论了你） | 同上 |
| 3 PRIVATE | 用户之间的私信 | 任何登录用户，走 `/messages/private` |

`POST /messages/notify` 显式拒绝 `msg_type = 3`：私信要建会话、要算双方未读，
从通知接口发只会留下一条没有会话行的孤儿私信，在会话列表里永远看不到。
反过来 1/2 两类必须挡住普通用户，否则谁都能给别人伪造一条「你的视频被举报了」。

### 10.2 会话表：(user_id_a, user_id_b) 必须归一化成 (min, max)

`uk_conversation(user_id_a, user_id_b)` 是**有序**唯一键。不归一化的话「1 找 2」和「2 找 1」
各建一行，同一段对话被劈成两半，两边各看各的未读数。归一化统一在
`ConversationServiceImpl.Pair` 里做，Mapper 只负责 SQL。

归一化之后 `unread_count_a/b` 的含义变成「id 较小的那个人」和「id 较大的那个人」的未读数，
跟「谁是发起方」无关——所以 `touchOnSend` 要先判断收信人落在哪一侧，两个增量里恒有一个是 0。

建会话用一条 `INSERT ... ON DUPLICATE KEY UPDATE` 完成「建或改」，不先查再决定：
两个用户同时给对方发消息时，先查后插的两条请求会双双撞唯一键。
「最后一条消息」用 `GREATEST` 取较新者——并发下 id 较小的那条不一定后到，直接赋值会让
会话列表的预览偶尔回退；`COALESCE` 是给 `last_msg_time` 为 NULL 的历史行兜底，
`GREATEST` 碰到 NULL 返回 NULL。

会话列表按 `last_msg_time DESC, id DESC` 排——第二排序键是兜底，同一秒里建的多段会话
只按时间排会在翻页时重复出现或被跳过。返回的 `ConversationView` 里 `peerId` 由服务端算好，
调用方永远不知道自己是 A 还是 B。

### 10.3 未读数：`message_record.is_read` 是唯一真相

会话表上的 `unread_count_a/b` 只是**派生缓存**，红点数字一律从消息表 `COUNT` 出来
（`countUnreadByType` 按 `msg_type` 分组），不从会话表求和。红点上出现两个不同的数字比慢一点更糟。

标记单段会话已读时，会话计数是**同步成实测值**而不是清 0：标记和清零之间可能正好插进来
一条新私信，直接清 0 会让那条消息永远顶着未读却不在红点上。同步必须在 `markRead` 之后、
且和它同一个事务。

「私信全部已读」则可以直接清 0——同一事务里消息表已经全部标完了，实测值必然是 0，
逐段会话去 COUNT 只是把一次批量操作退化成 N 次查询。

会话列表的「最后一条消息」摘要一次 `IN` 查完（不做 N+1），且只查 `status = 1`：
已删除的消息不该还挂在会话卡片上，此时 `lastMsgContent` 为 null，前端回退到只显示时间。

### 10.4 推送设备绑定的两个坑

**vendor 永远不写 NULL。** `uk_user_device(user_id, device_type, vendor)` 里的 vendor 在 DDL 上可空，
而 MySQL 唯一索引把 NULL 当作互不相等——真写 NULL 进去这个唯一键形同虚设，同一台设备每次重绑都多一行。
请求没带 vendor 就按 deviceType 推断（ios→apns / android→fcm / harmony→huawei），推断不出来直接报错。
deviceType、vendor、pushToken 一律 trim + 转小写，否则 `"IOS"` 和 `"ios"` 会被当成两台设备。

**换绑时先失效别人名下的同一条 token，再 upsert 到自己名下。** 设备会被转手、会被换账号登录，
不失效旧绑定的话同一个 token 会同时挂在两个用户下，厂商推送往同一条通道投两个账号的消息——
A 的私信推给正在用这台设备的 B，是实打实的隐私泄露。两条语句的顺序反了，
中间那一瞬间这台设备仍然同时属于两个人。

`upsert` 的返回值**不是行数**：MySQL 对 `ON DUPLICATE KEY UPDATE` 的约定是插入返回 1、
更新返回 2、命中但值没变返回 0，调用方不要拿它判断成功与否。

解绑置 `status = 0` 而不是 DELETE：厂商推送是异步的，投递失败时需要能从「最近失效的绑定」
反查这条 token 属于谁，真删了行日志里的 token 就成了孤儿。

### 10.5 接口与权限

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/messages` | 登录 | 收件箱，`msgType` 可选，只返回 `receiver_id = 我` 且 `status = 1` |
| GET | `/messages/unread` | 登录 | 未读汇总（三类分别计数 + 有未读的会话段数） |
| GET | `/messages/thread/{peerId}` | 登录 | 与某人的私信时间线，双向 |
| POST | `/messages/notify` | `message:send` | 下发系统/互动通知，拒绝 msgType=3 |
| POST | `/messages/private` | 登录 | 发私信，不能发给自己 |
| PUT | `/messages/read` | 登录 | 标记已读；私信可带 `peerId` 只标一段 |
| DELETE | `/messages/{id}` | 登录 | 隐藏（`status = 0`），仅收信人或发信人 |
| GET | `/conversations` | 登录 | 会话列表，带最后一条消息摘要 |
| PUT | `/conversations/{peerId}/read` | 登录 | 标记单段会话已读并重算未读 |
| GET | `/push-devices` | 登录 | 我的有效推送绑定 |
| POST | `/push-devices` | 登录 | 绑定/换绑 |
| DELETE | `/push-devices` | 登录 | 解绑，`deviceType`/`vendor` 可选 |

返回给前端的是 `MessageView` 而不是实体，`status` 这种存储层字段不外泄；
`isRead`（Integer）在视图里映射成 `read`（Boolean）。

`message:send` 的权限行是 `SQL/01_user_service.sql` 里的菜单 id 20，只授给 ROLE_ADMIN。
网关路由 `Path=/api/messages/**,/api/conversations/**,/api/push-devices/**`。

### 10.6 相关表

- `message_record`：`idx_receiver_type(receiver_id, msg_type, is_read)` 服务收件箱与未读统计；
  `idx_sender_receiver(sender_id, receiver_id, msg_type)` 是后补的——私信会话是双向查询
  `(我发给他) OR (他发给我)`，只有前一个索引的话「我发出去的」那一半无索引可走，会话越长越慢。
- `message_conversation`：`uk_conversation(user_id_a, user_id_b)`，见 10.2。
- `message_push_device`：`uk_user_device(user_id, device_type, vendor)` + `idx_push_token(push_token)`，见 10.4。

### 10.7 测试

`vidora-message` 下 60 个单元测试（`MessageServiceImplTest` 26 / `ConversationServiceImplTest` 21 /
`PushDeviceServiceImplTest` 13），mock 手法同 9.8。重点钉住的是归属边界（每个查询都带
`receiver_id = 我`）、会话归一化后未读增量落在哪一侧、以及换绑时「先失效再 upsert」的语句顺序。

## 11. 内容服务（content-service）

原先的 content-service 只有一个 `CategoryController`，五个方法直接转发 `IService`，
其中 `POST /categories` 和 `PUT /categories/{id}` 收的是裸 `Category` 实体——调用方可以自己填
`id`、`createTime`，一次「新增」就变成对任意行的覆写。这两条已经换成 `CategoryRequest`。

现在落地了 SQL 里设计好的五张表：分类、标签、推荐流配置、热搜榜、内容安全审核。
它们都不是视频本身，而是围绕视频的字典与治理数据——前四张读多写少，全部进 Redis；
审核表写多读少，不缓存。

### 11.1 分类树：孤儿提升 + 成环检测

`parent_id` 的完整性数据库层面**没有任何约束**（没有外键，原先连唯一键都没有），全靠服务层守：

- 顶级用 `0` 不用 `NULL`：`idx_parent_id` 的等值比较对 NULL 无效，`WHERE parent_id = 0` 才走得了索引。
  常量是 `Category.ROOT_PARENT_ID`。
- `tree()` 和 `listEnabled()` 是同一份数据的两个缓存视图（key `'enabled'` / `'tree'`，共用 6 小时 TTL）。
  缓存的是整棵树，改一个节点没法只失效那一个，所以写入一律 `allEntries = true`。
- `tree()` 内部**没有**调 `listEnabled()`：同一个 bean 里的自调用不走 Spring 代理，`@Cacheable` 不生效，
  留着只是多一次「看起来命中了其实没有」的误导。两个方法各自查库、各自缓存。
- 父级被禁用或已删除时，把它下面的子节点**提到根上**，而不是让它们凭空消失。
  运营禁用一个一级分类时不会顺手想到底下还挂着 40 个二级分类，那些分类直接从侧边栏消失是事故不是特性——
  而且前端看不见就没人能把它改回来。
- `MAX_TREE_DEPTH = 20`：`assertMovable` 从新父级一路往上走，边走边比是不是自己，
  走满 20 层还没到根就报「超过 20 层或已成环」。这个上限是**必需的**——`parent_id` 没有外键，
  脏数据真能成环，没有边界的循环会挂死一个请求线程。
- 删除只允许删叶子：还有子分类时报错并给出数量。

### 11.2 `utf8mb4_unicode_ci` 下的重名判断

所有表都是 `_ci` 排序规则，`uk_name` / `uk_parent_name` / `uk_keyword_date` 的唯一性判断都**不区分大小写**。
服务层的预检查必须跟它保持一致：`TagServiceImpl.create` 和 `CategoryServiceImpl.requireNameAvailable`
都是 `count(name = ?)` 交给 MySQL 去比，而不是把已有行读进 Java 再 `equalsIgnoreCase`。
后者会在「MySQL 认为重名、Java 认为不重」时放过，然后被唯一键抛成 500。

预检查 + 唯一键是两层：预检查负责给出人能看懂的错误信息，唯一键负责并发下的最后兜底。
`content_category` 原本没有 `(parent_id, name)` 唯一键（已补 `uk_parent_name`），
缺它的话两个管理员同时建同名分类会都成功，选择器里出现两个一模一样的项。
两列都是 `NOT NULL`——MySQL 唯一索引把 NULL 当作互不相同的值，`parent_id` 可空的话这个约束对顶级分类形同虚设。

> 已知缺口：`DuplicateKeyException` 没有在 `GlobalExceptionHandler` 里映射，真撞上并发会落到 500「服务异常」。

### 11.3 `rank` 是 MySQL 8 保留字

`content_hot_search.rank` 建表时写了反引号，DDL 看起来没问题；但实体字段按驼峰映射成裸 `rank` 之后，
MyBatis-Plus 生成的**每一条** SQL 都会在 `rank` 上报语法错。所以：

```java
@TableField("`rank`")
private Integer rank;
```

手写 SQL（`HotSearchMapper.upsert`）里也一律带反引号。建表语句的反引号会把这个坑藏起来——
`CREATE TABLE` 能过不代表 `SELECT` / `UPDATE` 能过。

### 11.4 热搜排名只能整批重算

`rank` 是「某天这一批词按热度排出来的相对位置」，不是词自己的属性。接受外部指定就会出现两个词并列第 3，
或者第 5 名没了。因此：

- `HotSearchRequest` 里**没有** `rank` 字段。
- `upsert` 插入时把 `rank` 写成字面量 0，`ON DUPLICATE KEY UPDATE` 也**不**更新 `rank`——
  刷新一个词的热度不该顺手把它的排名改掉。
- 排名统一由 `POST /hot-searches/rebuild` 重算，排序键 `heat_score DESC, search_count DESC, id ASC`。
  `id ASC` 是必需的第三排序键：热度相同的词没有稳定兜底顺序的话，每次重算都得到一份不同的名次，
  榜单在两次刷新之间反复横跳。
- 重算在 Java 侧做（读出来 → 比对 → 只写有变化的行），没有用
  `UPDATE ... JOIN (SELECT ROW_NUMBER() OVER ...)`：那个写法依赖 MySQL 8 窗口函数，
  本地没有真库能验证，出错的话是一整批名次错乱。
- 补丁对象只填 `id` 和 `rank`：`updateById` 跳过 null 字段，生成的 SET 子句里就只有 `` `rank` `` 一列。
- 返回值是**真正发生变化的行数**，不是扫过的行数——运营点一次重排看到「0」就知道榜单没动过。
- 读取时 `ORDER BY rank ASC, heat_score DESC`：还没重算过的新词 `rank` 全是 0，
  靠热度兜一下，不至于挤在榜首顺序随机。

### 11.5 机审覆盖会作废人工结论

`uk_target(target_type, target_id)`：一个对象一行。`upsertMachineResult` 在
`ON DUPLICATE KEY UPDATE` 里显式写了 `manual_result = NULL`。

人工当初放行的是**那一版**评分。机审复跑给出新分数后，旧的放行结论不该继续生效——
否则一条被人工放行的视频在风险模型升级后仍然是「已放行」，永远进不了复核队列。

- 未复核用 `NULL` 表示，不是 `0`。DDL 原注释写的 `0-未复核` 是错的（已修正）：
  0 在 TINYINT 里既不是放行也不是拦截，会被误读成第三种状态，而 `ManualResult` 只有 PASS 1 / BLOCK 2。
- `pending()` = `manual_result IS NULL AND risk_level >= 2`，排序 `risk_level DESC, id ASC`。
  同一风险等级内先来先审，否则排在队尾的永远轮不到。
- `review()` 允许改判：复核结论本身也可能错。`riskLabel` 不传就保留机审给的那个
  （机审标签往往是 `porn:0.87` 这种，复核后写成人能看懂的结论对复盘更有用）。
- 复核阈值 `MEDIUM`(2) 写在 `RiskLevel.needsManualReview()` 里，不散落在各处 SQL——调策略只改一个地方。

### 11.6 缓存 key 的边界

四个缓存名里有三个的 key 直接来自请求参数（`limit` / `feedType` / `date`），
所以参数必须**先夹住再进服务**，否则外部能用参数值往 Redis 里塞任意多条缓存；
分类那两个用的是字面量 key，天然有界。

| 缓存名 | key | TTL | 边界 |
|--------|-----|-----|------|
| `content:category-list` | `'enabled'` / `'tree'` | 6h | 固定两个 key，外部无法扩展 |
| `content:tag-hot` | `limit` | 1h | `TagController.clampLimit` 夹到 `[1, TagService.MAX_LIMIT=50]`，最多 50 个 key |
| `content:feed-config` | `feedType` | 1h | `FeedType.of` 只认 recommend / hot / follow 三个值 |
| `content:hot-search-board` | `date` | 5min | 一天一个 key；TTL 短是因为榜单当天会被反复重算和下线敏感词 |

两个细节：

- `date` 的默认值在 **controller** 里补，不在服务层。Spring Cache 拿到 null key 会直接抛异常，
  而不是退化成「不缓存」，所以 `HotSearchService.board/rebuild` 收到的必须已经是具体日期。
- `"HOT"` 和 `"hot"` 在 `content:feed-config` 里会占两个内容相同的条目。写入路径的失效是
  `allEntries = true`，两个条目不会各自漂移。

`limit` 没有用 `@Validated` + `@Min/@Max`：Spring 6.1+ 的 controller 方法校验抛的是
`HandlerMethodValidationException`，而 `GlobalExceptionHandler` 只处理 `ConstraintViolationException`，
结果是一个本该 400 的参数错误变成 500。项目里其他 controller 也都没用 `@Validated`，不引入。

### 11.7 JSON 列的校验

`content_security_audit.machine_result` 是 JSON 列，`content_feed_config.config_value` 是 TEXT 但语义上存 JSON。
JSON 列在 MySQL 侧就会拒绝非法值（错误 3140），而那个错误传到调用方只剩一句「服务异常」——
所以 `JsonValues.requireValid` 先用 Jackson 解析一遍，不合法就抛 400 并带上具体位置。
空白值直接放过：那是「把这个配置项的值清掉」，不是「存一个空 JSON」。

### 11.8 接口与权限

网关路由 `Path=/api/categories/**,/api/tags/**,/api/feed-configs/**,/api/hot-searches/**,/api/security-audits/**`。
（`/api/hot-searches/**` 与 search-service 的 `/api/search/**` 不重叠，后者只匹配以 `/api/search/` 开头的路径。）
五个 `content:*:manage` 权限位已加到 `SQL/01_user_service.sql`（菜单 21「内容管理」+ 按钮 22-26）并授权 ROLE_ADMIN。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/categories/list` | 登录 | 启用分类平铺，走缓存 |
| GET | `/categories/tree` | 登录 | 启用分类树，走缓存 |
| GET | `/categories/page` | `content:category:manage` | 可按 parentId / status 过滤 |
| POST | `/categories` | `content:category:manage` | 收 `CategoryRequest`，不接收裸实体 |
| PUT | `/categories/{id}` | `content:category:manage` | 返回重读后的行，不是提交的那份 |
| PUT | `/categories/{id}/status?status=` | `content:category:manage` | 0-禁用 1-启用 |
| DELETE | `/categories/{id}` | `content:category:manage` | 只允许删叶子分类 |
| GET | `/tags/hot?limit=` | 登录 | 标签云，`status=1` 按 `use_count DESC` |
| GET | `/tags/suggest?keyword=&limit=` | 登录 | 上传页联想；空前缀直接返回空列表 |
| GET | `/tags/page` | `content:tag:manage` | |
| POST | `/tags` | `content:tag:manage` | 只有 name，`use_count` 一律 0 |
| PUT | `/tags/{id}/status?status=` | `content:tag:manage` | |
| DELETE | `/tags/{id}` | `content:tag:manage` | `use_count > 0` 时拒绝，请改成禁用 |
| GET | `/feed-configs/configs/{feedType}` | 登录 | `key → value` 扁平视图，给推荐服务读 |
| GET | `/feed-configs/list?feedType=` | `content:feed:manage` | 带 id 与 description，给管理端表格 |
| PUT | `/feed-configs` | `content:feed:manage` | 按 `(feedType, configKey)` 建或改 |
| DELETE | `/feed-configs/{id}` | `content:feed:manage` | |
| GET | `/hot-searches?date=` | 登录 | 某天上线中的词，走缓存；不传日期看今天 |
| POST | `/hot-searches` | `content:hotsearch:manage` | 加词或刷新热度，不接受 rank |
| POST | `/hot-searches/rebuild?date=` | `content:hotsearch:manage` | 返回真正改动的行数 |
| PUT | `/hot-searches/{id}/status?status=` | `content:hotsearch:manage` | 下线敏感词走这里 |
| POST | `/security-audits/machine` | `content:audit:manage` | 内容服务回报机审结果 |
| GET | `/security-audits/target?targetType=&targetId=` | `content:audit:manage` | 没审过时 `data` 为 null，不是 404 |
| GET | `/security-audits/pending` | `content:audit:manage` | 待人工复核队列 |
| GET | `/security-audits/page` | `content:audit:manage` | 可按 targetType / riskLevel / reviewed 过滤 |
| PUT | `/security-audits/{id}/review` | `content:audit:manage` | 允许改判 |

审核表的读接口也要权限，和其他四组不同：`machine_result` 里是哪个模型给了多少分，
对普通用户既没用也不该公开。

`suggest` 空前缀返回空列表而不是全部标签：「列出全部标签」这个需求由 `/tags/hot` 覆盖，
放任空前缀走 `LIKE '%...'` 会白白全表扫一遍。有前缀时用 `likeRight`，`uk_name` 还能用得上。

### 11.9 相关表

- `content_category`：`uk_parent_name(parent_id, name)`（后补，见 11.2）、`idx_parent_id`、`idx_status_sort(status, sort_order)`。
  没有 `is_deleted`——下架走 `status=0`。`video_info.category_id` 在**另一个库**，跨库悬空引用没法用外键挡，
  删分类时也没有回查 video-service，见第 14 节待完成项。
- `content_tag`：`uk_name`、`idx_status_count(status, use_count)`。`use_count` 的写入方是 video-service
  （发布/下架视频时增减），content-service 刻意不暴露任意增减接口。表上没有 `update_time`。
- `content_feed_config`：`uk_feed_key(feed_type, config_key)`。`config_value` 用 TEXT 不用 JSON 列——
  配置值经常是 `"0.35"`、`"true"` 这种裸标量，MySQL 的 JSON 列虽然收得下但读出来带引号，
  调用方还得再解一层。
- `content_hot_search`：`uk_keyword_date(keyword, rank_date)`、`idx_rank_date(rank_date, rank)`。
  按天存快照，同一关键词每天一行，昨天的榜单不会被今天的热度冲掉。表上没有 `update_time`。
- `content_security_audit`：`uk_target(target_type, target_id)`、`idx_risk_level(risk_level)`。
  表上没有 `update_time`，因此**无法回答「这条是什么时候被人工复核的」**，见第 14 节待完成项。

### 11.10 测试

按项目约定（非必要不写单元测试）本模块没有单元测试。风险最高的恰好是最需要真库验证的部分——
三个 mapper 里的 `ON DUPLICATE KEY UPDATE` 手写 SQL（`FeedConfigMapper.upsert`、
`HotSearchMapper.upsert`、`SecurityAuditMapper.upsertMachineResult`）目前只有编译层面的保证，
见第 14 节待完成项。

## 12. 推荐服务（recommend-service）

算法模型在外部训练、外部运行，这个服务**不产出推荐**。它只管三样东西：算法算好的候选（`recommend_result`）、
算法读的参数（`recommend_algo_config`）、召回要用的用户画像（`recommend_user_feature`）。

原先的壳控制器只有一个 `RecommendResultController`，五个方法直接转发 `IService`，其中三处是越权：
`GET /recommends/page` 不带任何过滤条件（任何登录用户都能翻遍全站候选，等于公开「谁在被推什么」）、
`POST /recommends` 收裸实体（调用方自己填 `userId` 就能往任何人的 feed 里塞视频）、
`PUT /recommends/{id}` 能把任意行的 `userId` 和曝光/点击位改成任意值。五条全部替换掉了。

### 12.1 feed 是「取出 + 立刻标记曝光」两步

`GET /recommends/feed` 返回的候选**在返回的同时**就被置成 `is_exposed = 1`：

- 查询条件是 `user_id + scene + is_exposed = 0`，按 `score DESC, id ASC` 排序。
  `id` 是必需的兜底排序键——分数相同的候选没有稳定顺序的话，同一屏刷新两次会给出不同的排列。
- `new Page<>(1, size, false)` 关掉 count 查询：feed 只要这一屏，不需要知道总共有多少候选。
- 标记用**条件更新**（`WHERE ... AND is_exposed = 0`）而不是无条件置位，这样并发刷新时
  影响行数会小于取出的行数，竞争是**可观测**的：`claimed < ids.size()` 就打一条 warn。
- 这个竞争**没有解决**，只是记了下来。项目还没选分布式锁（见第 14 节），
  后果是这个人短时间内可能看到重复的一屏，曝光计数本身仍然是对的。
- `size` 在服务层夹到 `[1, MAX_FEED_SIZE = 50]`。没有上 `@Validated`：Spring 6.1+ 的 controller 方法校验
  会抛 `HandlerMethodValidationException`，而 `GlobalExceptionHandler` 目前没有这个映射，
  参数错误会变成 500（见第 14 节）。

### 12.2 点击蕴含曝光

`reportClick` 同时置 `is_clicked = 1` **和** `is_exposed = 1`。只置点击位的话，
一个客户端跳过 feed 直接上报点击（比如从分享链接进来）就会留下 `is_clicked = 1, is_exposed = 0` 的行——
按「点击数 / 曝光数」算 CTR 时分母少了，比值能大于 1。

更新条件带 `user_id = 当前登录用户`（不带的话猜到一个自增 id 就能替别人记点击，CTR 直接失真）
和 `is_clicked = 0`（重复上报返回 `false`，不报错）。

### 12.3 批量写入：upsert 不动曝光位和点击位

`RecommendResultMapper.batchUpsert` 是
`ON DUPLICATE KEY UPDATE score = VALUES(score), algo_type = VALUES(algo_type)`，
命中的是 `uk_user_video_scene(user_id, video_id, scene)`——算法任务重跑一次是**刷新分数**而不是追加一行。

两个刻意的选择：

- **`is_exposed` / `is_clicked` 既不在 INSERT 列表里，也不在 UPDATE 子句里。**
  它们是一次性事实：重置曝光位会让已经看过的视频重新回到 feed，重置点击位会毁掉 CTR。
  两列都从 INSERT 列表里省掉，让 DDL 的 `DEFAULT 0` 生效。
- **不包 `@Transactional`**，按 `BATCH_CHUNK = 500` 分片提交（单条 INSERT 拼太多行会撞 `max_allowed_packet`）。
  upsert 幂等，中途失败算法任务重跑整批即可；把几十条 INSERT 圈在一个事务里只会长时间占着连接。

返回值是**提交的行数**，不是数据库影响行数——`ON DUPLICATE KEY UPDATE` 返回 1=插入、2=更新、0=值没变，
那个数字当行数读会误导人。

`scene` / `algoType` 在写库前一律过枚举取 `getCode()`：`_ci` 唯一键下 `"HOME"` 和 `"home"` 是同一行，
但库里会留下两种写法，事后按场景统计还得再做一次大小写归一。

请求体上限 `MAX_ITEMS = 5000`，和 SQL 的分片上限 500 是两回事：
前者挡的是「一次 HTTP 请求塞进多少条」，后者挡的是「一条 INSERT 语句拼多少行」。

### 12.4 算法配置的 upsert 不动 `status`

`AlgoConfigMapper.upsert` 的 UPDATE 子句只有 `config_value` 和 `description`，**没有 `status`**。
算法任务每次跑完都可能回报一遍自己的参数，如果 upsert 顺手把 `status` 也写成 1，
运营刚刚禁用的一条配置会在下一次任务跑完时自己复活。启停只能走 `PUT /algo-configs/{id}/status`。

`config_value` 走 `JsonValues.requireValid`（见 11.7）：`0.35`、`true` 这种裸标量本身就是合法 JSON，能通过；
但一个不带引号的词（`fast`）会被拦下来，得写成 `"fast"`。

### 12.5 缓存 key 用枚举，不用字符串

`AlgoConfigService.configsOf(RecommendScene scene, AlgoType algoType)` 的参数是**枚举**，
`@Cacheable(cacheNames = recommend:algo-config, key = "#scene.code + ':' + #algoType.code")`，TTL 1 小时。

字符串转枚举发生在 **controller**，两个原因：

- key 一定是规范形式。收字符串的话 `"HOME:CF"` 和 `"home:cf"` 会各占一个内容相同的条目。
- 非法取值挡在缓存之前——一个拼错的场景名如果先进了 `@Cacheable`，
  会在 Redis 里留下一条永远命中不了、也永远不会被查库覆盖的记录。

缓存的是「某场景某算法的启用配置」这一个组合（最多 3×3 = 9 条），不是整张表：
算法侧每次只关心自己那一路的参数，缓存整表会让任何一次改动都失效掉全部场景。
三个写入方法一律 `allEntries = true`——`status` 的启停会影响哪些项进 Map，而失效粒度只有 cacheName。

值为 `null` 的配置项不进 Map（`Collectors.toMap` 不接受 null 值，而且「没配」和「配了个 null」
对算法侧本来就没区别）。

### 12.6 用户画像是覆盖不是累加

`UserFeatureMapper.batchUpsert` 命中 `uk_user_feature(user_id, feature_type, feature_value)`，
UPDATE 子句是 `weight = VALUES(weight)`。累加的话权重会在几周内全部顶到 `DECIMAL(6,4)` 的列上限，
所有特征都变成同一个数，排序失去意义。

`weight` 的上下界由 DTO 的 `@DecimalMin` / `@DecimalMax` **拒绝**，不在服务层静默夹住——
静默截断会把模型侧的 bug 藏起来。

一次请求只写**一个用户**（`userId` 在 `UserFeatureBatchRequest` 上，条目里不再重复），上限 1000 条。
这样一行坏数据不会连带回滚几百万行。分片同样是 `BATCH_CHUNK = 500`，同样不包事务。

### 12.7 权限：manage 与 purge 分开

`SQL/01_user_service.sql` 里加了菜单 27–29：`推荐管理`（目录）、`recommend:manage`（推荐运维）、
`recommend:purge`（推荐数据清理），都授给 ROLE_ADMIN。

算法任务的服务账号只需要 `recommend:manage`，不该顺手拿到清空候选表、清空画像的能力——
`DELETE /recommends/stale` 和 `DELETE /user-features` 都是不可逆的批量删除，单独一个权限位。

`pruneStale` 拒绝 `days < 1`：`days = 0` 会让 `create_time < now()` 命中全表，
一个手误的参数就清空了整张候选表。删除走 `idx_create_time`。

### 12.8 接口与权限

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/recommends/feed?scene=&size=` | 登录 | 取一屏候选，返回即标记曝光 |
| POST | `/api/recommends/{id}/click` | 登录 | 上报点击（同时置曝光位）；已记过返回 `false` |
| GET | `/api/recommends/history?scene=&current=&size=` | 登录 | 我已被推过的候选，给「不感兴趣」这类入口做展示 |
| POST | `/api/recommends/batch` | `recommend:manage` | 算法任务批量 upsert 候选 |
| GET | `/api/recommends/page?...` | `recommend:manage` | 管理端分页，可按 userId / scene / exposed / clicked 过滤 |
| DELETE | `/api/recommends/stale?days=30` | `recommend:purge` | 清理 N 天前生成的候选，返回删除行数 |
| GET | `/api/algo-configs/configs?scene=&algoType=` | 登录 | key→value 扁平视图，走缓存 |
| GET | `/api/algo-configs/list?scene=` | 登录 | 该场景全部启用项，带 id 与 description |
| GET | `/api/algo-configs/page?...` | `recommend:manage` | 管理端分页 |
| PUT | `/api/algo-configs` | `recommend:manage` | upsert，不动 `status` |
| PUT | `/api/algo-configs/{id}/status?status=` | `recommend:manage` | 启用(1) / 禁用(0)，禁用意味着算法回落默认参数 |
| DELETE | `/api/algo-configs/{id}` | `recommend:manage` | 物理删除 |
| GET | `/api/user-features/top?userId=&featureType=&limit=` | `recommend:manage` | 某用户某类特征里权重最高的前 N 个 |
| POST | `/api/user-features/batch` | `recommend:manage` | 单用户批量 upsert，覆盖权重 |
| DELETE | `/api/user-features?userId=&featureType=` | `recommend:purge` | `featureType` 不传即清空该用户全部画像 |

`configs` 与 `list` 是两个接口而不是一个：前者给算法侧直接取参数（扁平 Map），
后者给管理端表格（要 id 和 description 才改得动）。

`user-features` 连读都要权限，也没有 `/mine`：画像是「这个人喜欢什么」，比推荐结果更敏感，
而且读写双方都是算法任务和管理端，没有终端用户的用法，`userId` 一律是显式参数。

网关路由（`../vidora-gateway/src/main/resources/application.yml` 的 `spring.cloud.gateway.routes`）：
`Path=/api/recommends/**,/api/algo-configs/**,/api/user-features/**` → `lb://recommend-service`，`StripPrefix=1`。

### 12.9 相关表

- `recommend_result`：`uk_user_video_scene(user_id, video_id, scene)`、
  `idx_user_scene_score(user_id, scene, score)`、`idx_create_time`。
  **没有 `update_time`**——能知道一条候选是什么时候生成的，但不知道它是什么时候被曝光、被点击的。
  按天算 CTR 需要这个时间戳，见第 14 节待完成项。
- `recommend_algo_config`：`uk_scene_algo_key(scene, algo_type, config_key)`。没有 `is_deleted`，删除就是物理删。
- `recommend_user_feature`：`uk_user_feature(user_id, feature_type, feature_value)`、`idx_user_id`。
  **没有 `create_time`**，只有 `update_time`（由 DDL 的 `ON UPDATE CURRENT_TIMESTAMP` 维护，upsert 语句不写它）。

### 12.10 测试

按项目约定（非必要不写单元测试）本模块没有单元测试。三个 mapper 里的手写 SQL
（`RecommendResultMapper.batchUpsert`、`AlgoConfigMapper.upsert`、`UserFeatureMapper.batchUpsert`）
目前只有编译层面的保证，见第 14 节待完成项。

## 13. 搜索服务（search-service）

Elasticsearch 还没接，**这个服务目前不做检索**。它管的是检索周围的三样东西：
我的搜索历史（`search_history`）、全站词频统计（`search_keyword_stat`）、建议词字典（`search_suggest`）。
检索接口本身和它的结果数由调用方回报，见 13.2。

原先的壳控制器只有一个 `SearchHistoryController`，五个方法直接转发 `IService`，
**五个全都越权**——这是所有空壳模块里最严重的一个，因为这张表存的是「谁搜过什么」：
`GET /search/page` 不带 `user_id` 过滤（任何登录用户都能翻遍全站搜索历史）、
`GET /search/{id}` 和 `DELETE /search/{id}` 不校验归属、
`POST /search` 收裸 `SearchHistory` 实体（调用方自己填 `userId` 和 `searchCount` 就能伪造任何人的历史）、
`PUT /search/{id}` 能把任意行改成任意用户的任意词。五条全部替换掉了。

三个控制器都挂在 `/search` 下（`/search/history`、`/search/stats`、`/search/suggests`），
所以网关那一条 `Path=/api/search/**` 不用改。

### 13.1 游客的 `user_id = 0` 是个陷阱

DDL 里 `search_history.user_id` 是 `NOT NULL DEFAULT 0`，注释写着「0 为游客」。代码**从不写 0**：
`uk_user_keyword(user_id, keyword)` 会把所有游客挤进同一批 `(0, keyword)` 行里，
那既不是任何一个人的历史，一旦被 `myHistory` 读到就等于把全站游客的搜索词摊给一个人看。

所以 `record` 要求登录（`userId == null` 直接 401）。等网关白名单放开匿名访问之后，
正确的做法是把词频统计那次 upsert 挪到登录校验**之前**——游客的搜索该计入全站热词，
但不该落历史表。目前还没到那一步。

### 13.2 一次搜索写两张表

`POST /search/record` 收 `{keyword, resultCount}`，在一个事务里做两次 upsert：

- `search_keyword_stat`：当天这个词的次数 +1，平均结果数按增量公式更新（见 13.3）。
- `search_history`：这个人对这个词的次数 +1，`last_search_time` 由 DDL 的
  `ON UPDATE CURRENT_TIMESTAMP` 自己往前走，upsert 语句不写它。

两次都是计数器，**重复调用会重复计数**。计数器本来就没有幂等键，客户端重试一次就多算一次；
为它引入一张去重表的代价远大于这点误差，所以不做。事务只保证「不会出现只涨了一边」的半天数据。

`resultCount` 允许不传，按 0 计。它的用途是找出「搜的人多但搜不到东西」的词——
那批词是内容缺口，该去补片源，而不是当成热词推给用户。

### 13.3 平均结果数用增量公式，两个赋值的顺序不能换

`result_count` 存的是**平均**结果数不是总和，但表里没有单次结果数的明细，
所以只能在 upsert 里用增量平均维护：

```sql
ON DUPLICATE KEY UPDATE
    result_count = (result_count * search_count + #{resultCount}) DIV (search_count + 1),
    search_count = search_count + 1
```

MySQL 的 `UPDATE` 子句从左往右求值，**后面的赋值能看到前面已经改过的列**。
这里 `result_count` 必须先算，用的还是旧的 `search_count`；等它算完 `search_count` 才加一。
顺序写反，分母就多加了一次，均值会越来越偏小——而且不会报错，只会慢慢漂。

用 `DIV` 不用 `/`：`/` 得到 DECIMAL 再按列类型舍入，行为随 SQL 模式变；`DIV` 是显式的整数截断。
`result_count * search_count` 的量级在 10^15 以内，离 BIGINT 上限还很远。

### 13.4 关键词归一化：压空白，但不转小写

`normalizeKeyword` 做两件事：trim，以及把连续空白压成一个空格。
压空白不是为了好看——`uk_user_keyword` 和 `uk_keyword_date` 都是精确匹配，
`"科幻  电影"` 和 `"科幻 电影"` 会各占一行，同一个词的热度被劈成两半，词频统计和热词榜都会失真。

**不做大小写归一**。`utf8mb4_unicode_ci` 下 `uk_keyword` 本来就把 `Java` 和 `java` 当同一行，
所以先写进去的那种拼法会被保留，后来的只加计数。全部转小写能保证一致，
但联想框里出现 `iphone` 而不是 `iPhone` 是用户能直接看见的质量问题，两害相权选了保留原样。
这和 11.2 是同一类问题：`_ci` 排序规则让「相同」的定义和 Java 的字符串比较不一样。

### 13.5 缓存只覆盖「未输入时的热门词」

联想是整条搜索链路里请求量最大的接口——每敲一个字符就是一次。但只有
`SearchSuggestService.top(limit)` 进了缓存（`search:suggest-top`，key = `limit`，TTL 10 分钟），
带前缀的 `suggest(prefix, limit)` **刻意不缓存**：前缀是用户输入的任意字符串，
拿它当缓存 key 等于把 Redis 的条目数交给了外部。这和 11.6 是同一条边界。

不缓存也扛得住：`likeRight` 生成 `keyword LIKE 'xx%'`，能走 `uk_keyword` 的前缀范围扫描。
`limit` 在 controller 里就夹到 `[1, MAX_LIMIT = 20]`，理由同 11.6——它同时是缓存 key。

两个视图的分支放在 **controller** 而不是服务层：`top` 带 `@Cacheable`，
同一个 bean 里的自调用不走 Spring 代理，塞进一个方法里缓存根本不会生效（同 11.1）。

### 13.6 自动挖掘：`source = 2` 的写入方

`search_suggest.source` 分「1-人工 / 2-自动挖掘」，`POST /search/suggests/mine` 是后者的写入方：
从某一天的 `search_keyword_stat` 里取搜索次数达标、且还没被收录的词，批量插成建议词。

- 排除已收录的词用 `LEFT JOIN search_suggest ... WHERE g.id IS NULL`，
  不是先查两张表再在 Java 里做差集——那样要在应用侧 holding 住一整天的统计行。
  两张表同库、同 `utf8mb4_unicode_ci`，连接比较和 `uk_keyword` 的唯一性判定用的是同一套规则。
- 插入用 `INSERT IGNORE`：「查候选」和「写候选」之间隔着一次网络往返，
  并发的两次挖掘、或者期间运营手工加了同一个词，都会让普通批量 INSERT 整批失败。
  代价是它会把别的错误（比如数据截断）也降级成警告，这里能接受——关键词来自本库统计表、
  长度天然在 200 以内，另两个字段都是整数。
- **运营手工触发，不是定时任务**：挖出来的词没人审过就会直接进联想框，
  得有人先看过门槛和候选再决定放不放。
- 新词权重一律是 `0`，不用它的搜索次数。次数可能上万，直接当权重会让一次挖掘
  把运营手工排好的联想框整个顶掉。0 意味着这些词能被前缀匹配到，但在 `top` 里排在所有人工词之后。
  相互之间的热度顺序不会丢：候选按 `search_count DESC` 取出、插入后 id 递增，
  而 `top` 的兜底排序键正是 `id ASC`，同权重下还原了热度序。

### 13.7 接口与权限

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/search/record` | 登录 | 回报一次搜索，写历史 + 词频 |
| GET | `/api/search/history?current=&size=` | 登录 | 我的历史，按最后搜索时间倒序 |
| DELETE | `/api/search/history/{id}` | 登录 | 删我的一条；不是我的返回 false 而不是 404 |
| DELETE | `/api/search/history` | 登录 | 清空我的历史，返回删除行数 |
| GET | `/api/search/stats/hot?date=&limit=` | `search:stat:view` | 某天的热词 |
| GET | `/api/search/stats/page?...` | `search:stat:view` | 管理端分页 |
| GET | `/api/search/suggests?prefix=&limit=` | 登录 | 联想；空前缀走缓存返回热门词 |
| GET | `/api/search/suggests/page?...` | `search:suggest:manage` | 管理端分页，可按 status / source 过滤 |
| POST | `/api/search/suggests` | `search:suggest:manage` | 新增，重名报错 |
| PUT | `/api/search/suggests/{id}` | `search:suggest:manage` | 改词 / 权重 / 来源，不动 status |
| PUT | `/api/search/suggests/{id}/status?status=` | `search:suggest:manage` | 启用(1) / 禁用(0) |
| DELETE | `/api/search/suggests/{id}` | `search:suggest:manage` | 物理删除 |
| POST | `/api/search/suggests/mine?date=&minCount=&limit=` | `search:suggest:manage` | 从词频里挖词，返回真正新增的条数 |

统计的两个接口连读都要权限，和标签、分类那些字典不一样：
这张表是「全站用户在搜什么」的原始词频，能反推出用户群体的兴趣，
甚至能看出某部片子正在被找但站内没有。它给运营和分析看，不给普通用户看。

`PUT /search/suggests/{id}` 刻意不动 `status`，理由和 12.4 一样：
把一个已经被运营禁用的词改回启用，不该是「改权重」的副作用。

`date` 参数的默认值（今天）在服务层补，不在 controller 补——和 11.6 的热搜榜不同，
那张表的 `rankDate` 同时是缓存 key，null key 会让 Spring Cache 直接抛异常；这两张表不缓存，null 传进去没有副作用。

网关路由不变：`Path=/api/search/**` → `lb://search-service`，`StripPrefix=1`。

### 13.8 相关表

- `search_history`：`uk_user_keyword(user_id, keyword)`、`idx_last_search_time`。
  `myHistory` 的过滤走 `uk_user_keyword` 的最左列，但排序键 `last_search_time` 不在这个索引里，
  所以还有一次 filesort——一个人的历史行数是「搜过多少个不同的词」，量级很小，
  不值得为它再加一个 `(user_id, last_search_time)` 索引。
- `search_keyword_stat`：`uk_keyword_date(keyword, stat_date)`、`idx_stat_date_count(stat_date, search_count)`。
  按天分行而不是一个词一行：热词的生命周期就是一天，合成一列就再也拆不回来了，
  周榜月榜在查询侧按 `stat_date` 聚合。表上没有 `update_time`。
- `search_suggest`：`uk_keyword`、`idx_status_weight(status, weight)`。
  表上没有 `update_time` 也没有 `is_deleted`——下架走 `status = 0`，删除是物理删，
  所以改过一个词的权重之后无法知道是什么时候改的。

### 13.9 测试

按项目约定（非必要不写单元测试）本模块没有单元测试。四个手写 SQL
（`SearchHistoryMapper.upsertSearch`、`SearchKeywordStatMapper.upsertStat`、
`SearchKeywordStatMapper.mineCandidates`、`SearchSuggestMapper.batchInsertIgnore`）
目前只有编译层面的保证，见第 14 节待完成项。其中 `upsertStat` 的增量平均
是最该拿真库验的一处——赋值顺序写反不会报错，只会让均值慢慢漂。

## 14. 待完善

**已完成**

- [x] 网关鉴权过滤器（JWT 校验，`GatewayAuthFilter`，白名单 `/api/auth/**`）
- [x] Spring Security 授权 + RBAC 菜单控制（用户-角色-菜单，接口级 `@PreAuthorize`）
- [x] 菜单/角色管理页所需权限：已在 `SQL/01_user_service.sql` 补 `menu:add/edit/delete/assign`、`role:assign`、`video:transcode` 按钮权限行并授权 ROLE_ADMIN（原缺口已修复）
- [x] RBAC 实体去重：`User/Role/Menu/UserRole/RoleMenu` 及其 Mapper 下沉到 `vidora-common-user`，auth 与 system 共用（原先两份逐字节相同的副本）
- [x] Redis 缓存落地：`vidora-common-redis` 提供 JSON 序列化的 `RedisCacheManager` + 按缓存名分 TTL；已覆盖菜单树、角色菜单、
      分类列表与分类树、热门标签、推荐流配置、热搜榜、推荐算法配置（按 `scene:algoType` 组合）、
      热门搜索建议词、播放地址、互动计数，写入路径带精确失效；
      TTL 表用 `Map.ofEntries` 而不是 `Map.of`（后者最多 10 个键值对，加第 11 个缓存名会直接编译失败）；
      Redis 不可用时 `CacheErrorHandler` 降级为直接查库而不是把接口打成 500
- [x] `BizException` 不再一律返回 HTTP 200：按业务码映射状态码（400/401/403/404/500），网关与监控可据此识别错误
- [x] 密钥与凭据外部化：`jwt.secret`、数据库/Redis 口令、MinIO AK/SK 全部改为 `${ENV:dev默认值}`，不再硬编码
- [x] `PasswordEncoder` 提取为 Bean（原先在业务代码里 `new BCryptPasswordEncoder()`）
- [x] RocketMQ 转码链路（生产者 + 消费者 + 重投重试 + 未启用时回落本地线程池），见 6.4 / 8.2
- [x] `deploy/docker-compose.yml` 构建路径与实际目录对齐，MySQL 挂载 `SQL/` 自动建库建表，补齐 Sentinel 控制台与 RocketMQ namesrv/broker
- [x] 9 个启动类的 `scanBasePackages` 修正为 `org.tiglor`（包重构后遗留，原先扫不到任何 bean）
- [x] 根 POM 开启 `maven-compiler-plugin` 的 `<parameters>true</parameters>`：Spring Framework 7 已移除局部变量表兜底，缺它则 `@Cacheable(key="#userId")` / `@PreAuthorize` 的 SpEL 参数名无法解析
- [x] 根 POM 钉定 `maven-surefire-plugin` 3.6.0：Maven 3.9.6 默认绑定 2.12.4，跑不了 JUnit 5，测试会被**静默跳过**
- [x] MinIO 分片上传 + MD5 秒传（断点续传 / 服务端 compose 合并 / 归属校验 / 幂等重传），见 8.4
- [x] interact-service 业务逻辑落地：评论楼中楼（parent_id + root_id 扁平化）、点赞/收藏/分享、弹幕、播放计数与汇总，见第 9 节
- [x] message-service 业务逻辑落地：三类消息与发送资格、私信会话（(min,max) 归一化 + 未读红点）、推送设备绑定；
      同时删掉了原先那个不带 `receiver_id` 过滤、任何登录用户都能翻遍全站私信的壳控制器，见第 10 节
- [x] content-service 业务逻辑落地：分类树（孤儿提升 + 成环检测）、标签字典、推荐流配置、热搜榜（按天快照 + 整批重排）、
      内容安全审核台（机审覆盖作废人工结论）；字典类四张表进 Redis，写入靠服务层校验 + 唯一键兜底，
      同时把原先收裸 `Category` 实体的两条写入接口换成 `CategoryRequest`，见第 11 节
- [x] recommend-service 业务逻辑落地：推荐流（取出即条件标记曝光、点击蕴含曝光）、算法任务批量 upsert、
      算法配置（进 Redis，upsert 不动 `status`）、用户画像（覆盖而非累加）；
      同时删掉了原先那个不带 `user_id` 过滤、任何登录用户都能翻遍全站候选、还能收裸实体改任意行的壳控制器，见第 12 节
- [x] search-service 业务逻辑落地：我的搜索历史、全站词频统计（按天 + 增量平均结果数）、建议词字典与自动挖掘；
      热门搜索建议词进 Redis。同时删掉了原先那个**五个方法全都越权**的壳控制器——
      任何登录用户都能翻遍、伪造、改写、删除全站的搜索历史，这是所有空壳模块里最严重的一个，见第 13 节。
      注意**检索本身仍未接 ES**，`POST /search/record` 目前是给调用方回报关键词与结果数用的

**待完成**

- [ ] Nacos 配置中心（**主动回退，不是漏做**）：原先接过 `spring.config.import: optional:nacos:...` 并把配置按关注点
      拆成 `application-{concern}.yml`，现已合并回每服务一个 `application.yml`、`spring-cloud-starter-alibaba-nacos-config`
      依赖也已从 9 个 pom 移除，Nacos 当前**只做服务注册与发现**。重新启用的步骤见 6.2；
      合并时保留的 `# Datasource` / `# Redis` / `# MyBatis-Plus` 等分段注释就是为拆回去留的切割线
- [ ] RocketMQ 跨服务事件（转码完成通知、计数更新、弹幕入库）
- [ ] 分布式锁选型（点赞/计数防超卖）——Redisson 已移除，需另选方案（Redis `SET NX PX` + Redisson 替代 / 数据库乐观锁）。
      recommend 的 feed 已经撞上了这个缺口：取出候选和标记曝光是两步，并发刷新时同一屏可能被下发两次，
      目前只在 `claimed < ids.size()` 时打一条 warn（见 12.1）
- [ ] Elasticsearch 全文检索（search-service 的检索能力）：目前这个服务只管历史、词频和建议词，
      **不做检索**，`POST /search/record` 是给调用方回报关键词与结果数用的临时入口。
      ES 接上之后这次回报应当挪进检索接口内部，对外的上报入口撤掉——
      否则任何人都能不调检索就直接往词频统计里灌数
- [ ] Sentinel 限流规则（流控/熔断）
- [ ] BFF 聚合层：评论/弹幕的用户昵称头像需要「拿到一页数据后批量解析 userId」，逐条 Feign 是一页 20 次远程调用（见 9.3）
- [ ] **公开读接口没有放行**：网关白名单只有 `/api/auth/**`，各服务 SecurityConfig 一律 `anyRequest().authenticated()`，
      于是未登录连视频列表、评论、弹幕、播放数都读不到——对一个视频平台这不成立。
      interact 侧已经按 `userId == null` 写好了分支（`counts` 的 liked/favorited 留 null、匿名播放不去重照实计数），
      放行后即可用；需要同时改网关白名单和各服务的 `requestMatchers`，并确认 `HeaderAuthenticationFilter`
      在没有 `X-User-Id` 时能放过请求而不是直接 401。
      content 侧的 `/categories/list`、`/categories/tree`、`/tags/hot`、`/tags/suggest`、`/hot-searches`、
      `/feed-configs/configs/*` 全是匿名可见的字典数据，而且本来就整份进 Redis（见 11.6），放行成本最低，可以一起放。
      search 侧的 `/search/suggests`（联想框）同理——没登录的人也要能搜索，
      而它的热门词视图本来就整份进 Redis（见 13.5）。注意放行之后 `POST /search/record`
      仍然要保持登录，否则词频统计就成了任何人可灌的开放计数器
- [ ] 单元测试：video 34 个（MQ 回落路径 5、本地存储分片往返 9、分片上传业务规则 20）、interact 68 个、message 60 个，其余 6 个模块仍为零。
      content、recommend、search 是按「非必要不写单元测试」的约定**主动**留空的（见 11.10 / 12.10 / 13.9），不是漏写
- [ ] 分片上传的**端到端**验证：现有测试都是 mock/本地磁盘层面的，真 MinIO 的 `composeObject`、并发传分片、秒传跨用户复用尚未跑通
- [ ] interact / message / content / recommend / search 的 SQL **尚未在真 MySQL 上跑过**：`previewReplies` 的窗口函数、`accumulate` 与
      `touchOnSend` 的 `ON DUPLICATE KEY UPDATE ... GREATEST`、`markRead` / `unbind` 的 `<script>` 动态 SQL、
      content 三条手写 upsert（`FeedConfigMapper.upsert`、`HotSearchMapper.upsert`、
      `SecurityAuditMapper.upsertMachineResult`）、
      recommend 三条手写 upsert（`RecommendResultMapper.batchUpsert`、`AlgoConfigMapper.upsert`、
      `UserFeatureMapper.batchUpsert`——其中两条 batch 是 `@Insert` 文本块里的 `<script>` + `<foreach>`，
      拼出来的多行 VALUES 一次都没执行过）、
      search 四条手写 SQL（`SearchHistoryMapper.upsertSearch`、`SearchKeywordStatMapper.upsertStat`、
      `SearchKeywordStatMapper.mineCandidates` 的 `LEFT JOIN ... IS NULL`、
      `SearchSuggestMapper.batchInsertIgnore`）、以及各条件更新的 SQL 片段目前只有字符串层面或编译层面的保证。
      本地 mysql:3306 / redis:6379 都没起，需要连真库过一遍——尤其要验 `rank` 反引号（见 11.3）、
      新加的 `uk_parent_name` 会不会让存量数据插不进去、
      以及 `upsertStat` 的增量平均（见 13.3）：**赋值顺序写反不会报错，只会让均值慢慢漂**，
      必须用「连打三次不同 resultCount、手算期望值」的方式对一遍
- [ ] 废弃分片会话的清理任务：一次上传尝试一行会话，用户中途放弃 / 分片参数变更作废都会留下永远合并不了的行，
      连同 `.parts/` 下的分片一起占着存储。需要按 `status != 2 且 update_time` 超期定时清理（数据库行 + 对象一起删）
- [ ] **跨库悬空引用**：`video_info.category_id` 指向 `content_service.content_category`，两个库之间没有外键也挡不住，
      删分类时 content-service 也没有回查 video-service。结果是删掉一个还在用的分类后，
      那些视频的分类字段指向一个不存在的 id，前端渲染成空白。需要「先查引用数再决定删/禁用」，
      或者干脆禁止物理删除、只允许 `status=0`
- [ ] `content_security_audit` 表上没有 `update_time`，因此无法回答「这条是什么时候被人工复核的」。
      审核台是合规相关的功能，缺复核时间戳意味着追溯不了责任。加列要同时改 DDL、实体和 `review()`
- [ ] `GlobalExceptionHandler` 缺两个映射：`DuplicateKeyException`（唯一键并发兜底被触发时落到 500「服务异常」，
      本该是 409 或 400 带具体字段）和 `HandlerMethodValidationException`（Spring 6.1+ 的 controller 方法校验异常，
      目前项目里没有 controller 用 `@Validated` 所以还没暴露，一旦有人加上就会把参数错误变成 500）
- [ ] `content_tag.use_count` 目前**没有任何写入方**：video-service 发布/下架视频时应当增减对应标签的计数，
      这条链路还没接。在接上之前 `/tags/hot` 的排序键全是 0，标签云等于按 id 顺序输出
- [ ] **推荐算法本身没有实现**：recommend-service 只负责存、取、启停，没有任何召回和排序逻辑。
      `feed` 读的就是 `recommend_result` 表，表是空的就返回空列表——需要外部算法任务先调 `POST /recommends/batch`
      把候选写进来。冷启动兜底（新用户 / 候选耗尽时回落到热度榜）也还没有，目前只会给一个空屏
- [ ] `recommend_result` 表上没有 `update_time`：能知道候选是什么时候生成的，但不知道它是什么时候被曝光、被点击的，
      因此**按天算 CTR 算不出来**（只能算全量累计）。加列要同时改 DDL、实体和 `feed()` / `reportClick()`
- [ ] recommend 的两个清理接口（`DELETE /recommends/stale`、`DELETE /user-features`）目前**只能手动调**，
      没有定时任务。候选表已曝光的行永远回不到 feed 里却一直占着表和索引，没人调就只增不减
- [ ] `likeRight` **不转义 `%` 和 `_`**：MyBatis-Plus 的 `likeRight` 直接把入参拼成 `LIKE 'xx%'`，
      用户输入一个 `%` 就变成 `LIKE '%%'`，前缀范围扫描退化成全表扫。
      影响 content 的 `TagService.suggest` 和 search 的 `SearchSuggestService.suggest` 两处。
      不是数据泄露（返回的都是启用状态的公开字典行），但这是一个几乎零成本的把接口打慢的办法。
      修法是转义后配 `ESCAPE '\\'`，两处要一起改，否则同一个前缀在两个服务里行为不一致
- [ ] **热搜榜没有从词频统计取数**：`content_hot_search`（运营发布的榜单）和
      `search_keyword_stat`（真实搜索词频）分属两个库，目前完全没有链路，
      榜单 100% 靠人工 `POST /hot-searches` 录入或 `POST /hot-searches/rebuild` 重排已有行。
      也就是说首页热搜和用户实际在搜什么无关。要接上得走 Feign 或者一条 MQ 事件，
      顺带要决定「多久同步一次」和「运营手工插的词会不会被同步冲掉」
- [ ] `search_suggest` 表上没有 `update_time`：改过一个词的权重、或者把它禁用之后，
      无法知道是什么时候、由谁改的。联想框是全站入口，运营改动没有痕迹不好追责。
      `search_keyword_stat` 同样没有，但那张表的 `stat_date` 已经说明了归属，影响小一些
- [ ] **词频表和历史表都没有归档/清理**：`search_keyword_stat` 是「一个词一天一行」，
      只增不减，长期会是全项目增长最快的表；`search_history` 也只有用户自己手动清。
      需要按 `stat_date` 做冷数据归档（比如 90 天前的按周/月聚合后删明细），
      以及一个「N 个月没再搜过的词」的历史清理任务。目前两者都没有任何入口，
      连手动清都做不到

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
| 注册/配置中心 | Nacos client | **3.2.4** | SCA 默认 3.1.1，已显式覆盖 |
| ORM | MyBatis-Plus | **3.5.14** + `mybatis-plus-jsqlparser` + `mybatis-spring 4.0.0` | 逻辑删除 + 自动填充 + 分页 |
| 数据库 | MySQL | 8.0 | 每服务独立 schema |
| 缓存 | Redis | 7 | spring-boot-starter-data-redis |
| 认证 | JWT | 0.12.6 | BCrypt 加密密码 |
| 接口文档 | SpringDoc OpenAPI | **3.1.0** | Boot 4 对应 springdoc 3.x |
| 消息 | RocketMQ | 2.3.3（starter） | 异步解耦 |
| 锁 | Redisson | 3.51.0 | 分布式锁 |
| 前端 | — | 独立包（前端负责人维护） | 经网关 `/api/*` 调用本后端 |

## 2. 微服务拆分与代码组织

| 服务 | 端口 | 核心实体（表） | 已实现能力 |
|------|------|----------------|------------|
| gateway-service | 8080 | — | 网关路由（/api/* → lb:// 各服务）、JWT 鉴权过滤器、Redis、Sentinel |
| auth-service | 8101 | User(sys_user) + RBAC 只读投影 | 注册、登录、JWT 签发 |
| system-service | 8108 | User/Role/Menu(sys_*) | 用户、角色、菜单与权限管理 |
| video-service | 8102 | VideoInfo(video_info) | 视频列表/详情/上传(自动异步转码)/下载/播放地址(多清晰度 HLS) |
| content-service | 8103 | Category(content_category) | 分类 CRUD |
| interact-service | 8104 | Comment(interact_comment) | 评论 CRUD |
| message-service | 8105 | MessageRecord(message_record) | 消息 CRUD |
| search-service | 8106 | SearchHistory(search_history) | 搜索历史 CRUD |
| recommend-service | 8107 | RecommendResult(recommend_result) | 推荐结果 CRUD |

每个微服务统一分层：

```
com.video.platform.{service}/
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
- 位置：`gateway/.../filter/GatewayAuthFilter.java`，实现 `GlobalFilter` + `Ordered`（order=-1，早于路由）。
- 白名单：路径以 `/api/auth/**` 开头直接放行（登录/注册无需 Token）。
- 其余请求必须携带 `Authorization: Bearer <token>`，校验失败返回 **401**（前端按 401 跳登录）。
- 校验通过后将 `userId` 以 `X-User-Id` 请求头透传给下游微服务，业务服务可直接取用。
- 复用 `common/core` 的 `JwtUtil`（网关以「排除 spring-boot-starter-web」方式引入公共核心，避免与 WebFlux 冲突）。

### 6.2 Nacos 配置中心接入
- 每个服务（含网关）在 `application.yml` 增加：
  ```yaml
  spring:
    config:
      import:
        - optional:nacos:${spring.application.name}.yml
  ```
- `optional:` 前缀保证**本地无 Nacos 也能正常启动**（仅告警，不影响本地开发）。
- 配置 dataId 约定为 `<服务名>.yml`，放 public 命名空间；生产可改 `spring.cloud.nacos.config.namespace` 指定命名空间。

### 6.2.1 微服务之间如何调用

- **客户端访问后端**：统一进入 `gateway-service`，由 Spring Cloud Gateway 按 `/api/*` 路径路由到目标服务。
- **服务发现**：使用 Nacos Discovery；网关里的 `lb://auth-service`、`lb://system-service` 等逻辑服务名由 Spring Cloud LoadBalancer 解析为实例地址。
- **当前代码状态**：已在 `video-service` 集成 Spring Cloud OpenFeign，并提供 `SystemUserClient` 调用 `system-service` 的基础客户端；其他服务按实际调用关系逐步增加客户端，不为没有调用需求的服务强行引入依赖。
- **同步调用约定**：使用 Spring Cloud OpenFeign + Nacos，例如 `@FeignClient(name = "system-service")`，禁止写死 IP/端口。Feign 统一配置连接超时、读取超时、日志级别和后续降级策略。
- **身份透传**：`video-service` 的 Feign `RequestInterceptor` 会透传 `X-User-Id`、`X-User-Roles`、`X-User-Permissions`，下游服务继续复用 `HeaderAuthenticationFilter` 做接口授权。
- **异步调用推荐**：转码完成、消息通知、推荐刷新等不要求立即返回的场景使用事件消息（RocketMQ/Kafka），避免服务之间形成同步调用链。当前 RocketMQ 仍是后续演进项。
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

### 6.4 RocketMQ 版本说明（starter 版本号 ≠ 引擎版本号）

- `org.apache.rocketmq:rocketmq-spring-boot-starter` 是 **Spring 集成库**，版本号与 RocketMQ 引擎**相互独立**：

  | starter（集成库） | 内部实际依赖的 rocketmq-client（引擎） |
  |---|---|
  | 2.3.3 | **5.3.1** |
  | 2.3.4（最新） | **5.3.2** |

  所以看到 `2.3.x` 不代表 RocketMQ 版本落后——它带的引擎就是 **RocketMQ 5.3.x**。
- **当前处置**：RocketMQ 功能尚未实现（无 `@RocketMQMessageListener` / `RocketMQTemplate` 代码），
  且 `rocketmq-spring` 2.3.x 是**按 Spring Framework 5.3.27 编译**的、尚未适配 Boot 4，
  其 `RocketMQAutoConfiguration` 又被 Boot 4 的 `AutoConfiguration.imports` 加载。
  为避免引入未使用且未适配 Boot 4 的依赖，**已暂时从 7 个业务服务 POM 中移除**。
  父 POM 仍保留 `rocketmq.version=2.3.4`，实现 MQ 时在服务 POM 加回一行即可：

  ```xml
  <dependency>
      <groupId>org.apache.rocketmq</groupId>
      <artifactId>rocketmq-spring-boot-starter</artifactId>
  </dependency>
  ```
- 补充：其所有 bean 均带 `@ConditionalOnProperty`（`rocketmq.name-server` 等），
  未配置 `rocketmq.*` 时处于休眠态、不会主动连接 MQ——即便加回也不会在启动阶段报错。

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

认证代码位于 `auth`，系统管理代码位于 `modules/system`。认证服务当前对
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

> 注：`common/core` 只引入 `spring-security-core`（纯 API，无自动配置），
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
- `StorageService`（接口：`upload` / `downloadUrl` / `publicUrl` / `delete` / `fetchToTempFile` / `uploadDir(默认方法)`）。
- 两种实现通过 `@ConditionalOnProperty(name="storage.type")` 自动切换，**切换只改 `application.yml`，业务代码零改动**：
  - `MinioStorageService`（默认，`storage.type=minio`）：MinIO SDK 8.5.17 上传对象；`downloadUrl` 返回**限时预签名 URL**（默认 3600s）；`publicUrl` 返回 `endpoint/bucket/object`（供 HLS 分片 / CDN 引用，无签名）。
  - `LocalStorageService`（`storage.type=local`）：写本地目录（`storage.local-dir`）；`publicUrl` 经 `storage.local-public-base` 拼接（需配套静态资源服务 / Nginx 映射）。
- `uploadDir(...)`：把 ffmpeg 生成的 HLS 切片目录**整体回传对象存储**（保留相对路径），是"对象存储 + CDN"分发的标准做法。
- `video_info.storage_path` 统一存对象名，便于两种存储互换。

### 8.2 转码架构：异步解耦 + 多清晰度 HLS（参考 Jellyfin / 大厂）
- **状态机**：`video_transcode_task` 表记录每次转码任务，状态 `0 待处理 → 1 处理中 → 2 成功 / 3 失败`，带 `progress`（0-100）、`retry_count`、`error_msg`。
- **异步执行**：`TranscodeService.submit(id)` 仅建任务并投递；真正的转码在 `transcodeExecutor` 线程池执行（`@Async`，见 `AsyncConfig`）。前端通过 `GET /api/videos/{id}/transcode-task` **轮询**状态/进度，符合"提交任务 + 轮询结果"的异步范式。
- **失败重试**：单次转码异常按 `transcode.max-retry`（默认 2）自动重试；最终失败置 `FAILED` 并记录原因。
- **生产演进**：当前用 `@Async` 线程池作为单体/原型可运行方案；上生产应改为**消息队列（RocketMQ/Kafka）投递任务 + 独立转码集群 / 云 MPS（阿里云 MPS / 腾讯云 MPS）** 消费执行——接口与状态机不变，仅把"线程池"替换为"MQ + Worker"，实现弹性扩缩容与削峰。

### 8.3 FFmpeg 多媒体处理
- `FfmpegService` 通过 `ProcessBuilder` 调用**本机** `ffmpeg` / `ffprobe` 命令行（不走数百 MB 的 JNI 依赖）：
  - `probe()`：ffprobe 探测时长 / 分辨率 / 编码（JSON 输出解析）。
  - `transcodeToAdaptiveHls()`：**一步产出多清晰度自适应 HLS**——单命令 `-filter_complex split+scale` + `-var_stream_map` + `-master_pl_name master.m3u8`，生成 ABR 阶梯（默认 360p/480p/720p/1080p）+ 主播放列表；支持 `none`（libx264 软编）/ `cuda`（h264_nvenc）/ `qsv`（h264_qsv）三档**硬件加速矩阵**（参考 Jellyfin），切片支持 `fmp4`（CMAF，现代默认）/ `ts`。VBV 码率约束（maxrate≈1.1×目标、bufsize≈2×maxrate）+ Closed-GOP（keyint=48）保证 HLS 带宽估算准确、自适应切档平滑。
  - `thumbnail()`：抽取封面（取第 1 秒）。
- `FfmpegProperties`：`ffmpeg-path` / `ffprobe-path` / `hls-time` / `work-dir`。
- `TranscodeProperties`：`enabled` / `hwaccel` / `segment-type` / `hls-time` / `master-name` / 线程池大小 / `max-retry` / `renditions`（清晰度阶梯，默认 4 档）。

### 8.4 上传 / 转码接口（video-service）
| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/videos/upload` | POST(multipart) | `video:upload` | 落存储 + ffprobe 探测元信息写 `video_info`；若 `transcode.enabled=true` 自动提交异步转码任务 |
| `/api/videos/{id}/transcode` | POST | `video:transcode` | 手动提交转码任务，返回任务 ID（幂等：已有进行中任务则直接返回） |
| `/api/videos/{id}/transcode-task` | GET | 登录即可 | 查询最新转码任务状态/进度（前端轮询） |
| `/api/videos/{id}/download` | GET | 登录即可 | 返回下载 / 预签名 URL |
| `/api/videos/{id}/play-url` | GET | 登录即可 | 已转码返回 `master.m3u8` 公共地址，否则返回源文件地址 |
| `/api/videos/{id}` | GET | 登录即可 | 视频详情（含 `hlsUrl` / `coverUrl` / `status`） |

> 播放流程：上传 → 轮询 `transcode-task` 至 `status=2` → 用 `play-url`（即 `master.m3u8`）交给 HLS.js / 原生 `<video>` 播放，播放器按带宽自动切换清晰度。

### 8.5 部署要点：FFmpeg 是**服务端 CLI 依赖**（非 Java 依赖）
- **本机（开发机）当前未安装 ffmpeg/ffprobe**（`where ffmpeg` 验证找不到）。本地只跑上传/列表（不触发探测与转码）不影响编译与启动；一旦触发转码/探测会抛"未安装"异常。
- **生产服务器**：凡运行 video-service 且启用转码的节点**必须预装 ffmpeg + ffprobe**；或按 8.2 演进把转码拆到**专用转码集群 / 云 MPS**，业务节点不装 ffmpeg。
- 二进制缺失时 `FfmpegService` 抛出明确错误（含"请确认已安装并配置路径"），便于定位。

### 8.6 相关表
- `SQL/02_video_service.sql`：
  - `video_info`：`storage_path` / `hls_url` / `cover_url` / `status`（3=已发布）。
  - `video_transcode_task`：**单次转码任务 = 一次多清晰度 HLS 转码**（含全部档位），字段 `video_id` / `video_key` / `status` / `progress` / `source_path` / `hls_path`(master objectName) / `renditions`(JSON) / `error_msg` / `retry_count` / `finished_at`。
  - 另有 `video_multipart_upload`（分片/秒传）、`video_audit_record`（审核），供后续完善。

## 9. 待完善

- [x] Nacos 配置中心接入（`spring.config.import: optional:nacos:${spring.application.name}.yml`，public 命名空间）
- [x] 网关鉴权过滤器（JWT 校验，`GatewayAuthFilter`，白名单 `/api/auth/**`）
- [x] Spring Security 授权 + RBAC 菜单控制（用户-角色-菜单，接口级 `@PreAuthorize`）
- [x] 菜单/角色管理页所需权限：已在 `SQL/01_user_service.sql` 补 `menu:add/edit/delete/assign`、`role:assign`、`video:transcode` 按钮权限行并授权 ROLE_ADMIN（原缺口已修复）
- [ ] RocketMQ 生产者/消费者（转码触发、计数更新、弹幕入库）
- [ ] Redisson 分布式锁（点赞/计数防超卖）
- [ ] MinIO 分片上传 + MD5 秒传
- [ ] Elasticsearch 全文检索（search-service 落地）
- [ ] Sentinel 限流规则（流控/熔断）
- [ ] BFF 聚合层
- [ ] 单元测试

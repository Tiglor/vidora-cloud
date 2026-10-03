# Vidora Cloud - 云端服务工程（Server）

> ⚠️ **本项目是「后端独立包」。前端是两个独立包**，通过网关 REST API 与后端协作：
> - **移动端**：`../vidora-mobile`（由前端负责人维护，本仓库不含代码）
> - **Web 端**：`../vidora-web`（Vue3 + Vite + Element Plus，参考 B 站/腾讯视频/爱奇艺）
> 本仓库负责提供 JSON API 与 Docker 部署编排（每个微服务一一对应一份 Dockerfile）。

基于《视频播放类APP分布式微服务技术架构方案（第三版）》与《项目架构体系》落地。

## 技术栈

| 层面 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 后端运行时 | JDK | **25 LTS**（25.0.4.1） | BellSoft JDK 25 |
| 后端框架 | Spring Boot | **4.0.7** | Spring Framework 7.0 |
| 微服务体系 | Spring Cloud Alibaba | **2025.1.0.0** | 底层 Spring Cloud 2025.1.3（Oakwood），对应 Boot 4.0.x |
| 注册/配置中心 | Nacos client | **3.2.4** | SCA 默认 3.1.1，已在父 POM 显式覆盖（全家桶 5 构件钉死） |
| ORM | MyBatis-Plus | **3.5.14** + `mybatis-plus-jsqlparser` + `mybatis-spring 4.0.0` | Boot 4 需 mybatis-spring 4.x |
| 接口文档 | SpringDoc OpenAPI | **3.1.0** | Boot 4 对应 springdoc 3.x |
| 对象存储 | MinIO / 本地磁盘 | SDK **8.5.17** | 视频文件上传/存储，可切换 |
| 多媒体处理 | FFmpeg（ffmpeg + ffprobe） | 本机 CLI 依赖 | 仅 `vidora-modules/vidora-video` 转码/探测时需要，见下文「FFmpeg 前置依赖」 |
| 数据库 | MySQL | 8.0 | 每服务独立 schema |
| 缓存 | Redis | 7 | |
| 消息队列 | RocketMQ | 引擎 **5.3.2** / 集成库 2.3.4（暂未启用） | starter 版本号 ≠ 引擎版本号，详见 docs 6.4 |
| 分布式锁 | Redisson | 3.51.0 | |
| 服务间同步调用 | Spring Cloud OpenFeign | 由 Spring Cloud 2025.1 BOM 管理 | 结合 Nacos 服务发现与 LoadBalancer |

## 目录结构

```
vidora-cloud/（云端服务）
├── pom.xml                          # 根工程与统一依赖管理
├── vidora-auth/                     # 平台级登录/注册/JWT 身份认证 8101
├── vidora-common/
│   └── common-core/                 # 统一响应/异常/BaseEntity/JWT/安全头透传
├── vidora-gateway/                  # 网关 8080（路由 + JWT 鉴权 + 权限头透传）
└── vidora-modules/                  # 业务模块
    ├── vidora-video/                    # 视频/存储/转码 8102
    ├── vidora-content/                  # 内容分类 8103
    ├── vidora-interact/                 # 评论互动 8104
    ├── vidora-message/                  # 消息 8105
    ├── vidora-search/                   # 搜索 8106
    ├── vidora-recommend/                # 推荐 8107
    └── vidora-system/                   # 用户/角色/菜单/RBAC 管理 8108
├── SQL/                           # 建库建表脚本（7 个，按服务拆分）
├── deploy/                        # 部署编排：docker-compose.yml + nginx 反代 + .env + 部署文档
└── docs/                          # 文档（ARCHITECTURE.md）
```

> 每个可运行模块目录（vidora-gateway、vidora-auth、vidora-system、vidora-video、vidora-content、vidora-interact、vidora-message、vidora-search、vidora-recommend）下各有一份**一一对应**的 `Dockerfile`；`vidora-common/common-core` 是公共库，不单独打包。

## 编译

> 本机实测环境：Maven `D:\apache-maven-3.9.6\bin\mvn.cmd`，**必须在 PowerShell 下调用**（Git Bash 的 `mvn` 在本机不可用）；JDK 25 依赖已缓存于 `F:\repository`，可用 `-o` 离线构建。

**关键坑：JDK 25 的目录是嵌套的。**
BellSoft 解压后得到两层目录：

```
D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\   ← 外层（不是 JDK 根）
└── jdk-25.0.4.1\                                        ← 真正的 JDK 根，JAVA_HOME 必须指到这里
```

`JAVA_HOME` 必须指向**内层** `jdk-25.0.4.1`，若只指到外层目录，Maven 会报 `无效的目标发行版: 25`。

### 完整编译（11 个模块）

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' clean compile -o
```

### 仅编译某个服务（含其依赖）

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' -pl vidora-modules/vidora-video -am clean compile -o
```

## FFmpeg 前置依赖（仅 vidora-video 转码需要）

`vidora-modules/vidora-video` 的转码与媒体探测通过 `ProcessBuilder` 调用**本机** `ffmpeg` / `ffprobe` 命令行。

- **开发机当前未安装** ffmpeg/ffprobe（`where ffmpeg` 验证找不到）。只跑上传/列表/下载（不触发探测与转码）不影响编译与启动；一旦调用上传（会触发 ffprobe 探测）或转码，会抛出明确报错（含"请确认已安装并配置路径"提示）。
- **生产环境**：凡是运行 `vidora-modules/vidora-video` 且启用转码的节点，**必须预装 ffmpeg + ffprobe**（Linux `apt/yum install ffmpeg` 或官方静态构建；Windows 下载 ffmpeg.org 构建，并在 `application.yml` 的 `ffmpeg.ffmpeg-path` 配绝对路径）。
- 不想每个业务 Pod 都装 CLI 的方案：把转码抽成**专用转码集群 / Worker**，或改用**云转码**（阿里云 MPS / 腾讯云 MPS），`vidora-modules/vidora-video` 仅"提交任务 + 轮询结果"。详见 `docs/ARCHITECTURE.md` 第 8 节。

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
mysql -u root -p < SQL/01_user_service.sql
mysql -u root -p < SQL/02_video_service.sql
mysql -u root -p < SQL/03_content_service.sql
mysql -u root -p < SQL/04_interact_service.sql
mysql -u root -p < SQL/05_message_service.sql
mysql -u root -p < SQL/06_search_service.sql
mysql -u root -p < SQL/07_recommend_service.sql
```

> `01_user_service.sql` 已内置 RBAC 初始数据：管理员 `ROLE_ADMIN` 拥有 `menu:add/edit/delete/assign`、`role:assign`、`video:transcode` 等按钮权限。

### 2. 启动后端

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' clean install -DskipTests -o
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f 'pom.xml' -pl vidora-gateway,vidora-auth,vidora-modules/vidora-system,vidora-modules/vidora-video spring-boot:run -o
```

Nacos **不是强依赖**：各服务 `spring.config.import` 使用了 `optional:nacos:`，
本地没起 Nacos 也能正常启动（仅告警）。需要服务发现和动态配置时再启动 Nacos 3.2.4。

### 3. 前端对接

前端独立包通过网关 `http://127.0.0.1:8080` 调用，路径带 `/api` 前缀：

| 能力 | 接口 | 权限 | 说明 |
|------|------|------|------|
| 登录 | POST /api/auth/login | 公开 | |
| 注册 | POST /api/auth/register | 公开 | |
| 视频列表 | GET /api/videos/page | 登录 | |
| 我的投稿 | GET /api/videos/mine | 登录 | 当前用户自己的投稿（含待转码状态） |
| 视频详情 | GET /api/videos/{id} | 登录 | |
| 投稿用户 | GET /api/videos/{id}/owner | 登录 | 视频服务通过 OpenFeign 调用 system-service |
| 视频上传 | POST /api/videos/upload | `video:upload` | 落存储 + 探测元信息；`transcode.enabled=true` 时自动提交异步转码 |
| 提交转码任务 | POST /api/videos/{id}/transcode | `video:transcode` | 手动触发，返回任务 ID |
| 查询转码任务 | GET /api/videos/{id}/transcode-task | 登录 | 轮询状态(0待处理/1处理中/2成功/3失败)与进度 |
| 播放地址 | GET /api/videos/{id}/play-url | 登录 | 转码成功后返回 `master.m3u8` 公共地址，否则返回源文件地址 |
| 下载地址 | GET /api/videos/{id}/download | 登录 | MinIO 为限时预签名 URL |
| 评论列表 | GET /api/comments/video/{videoId} | 登录 | 按视频分页查询顶层评论 |
| 发布评论 | POST /api/comments | 登录 | 服务端从 JWT 透传身份，不信任客户端 userId |

- 除 `/api/auth/**` 外，其余接口需在请求头携带 `Authorization: Bearer <token>`，否则网关返回 **401**
- 网关校验通过后会把 `userId / 角色 / 权限` 以 `X-User-Id / X-User-Roles / X-User-Permissions` 请求头透传给下游服务

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
npm run dev      # 默认 http://localhost:5173，Vite 代理 /api → 网关 8080
npm run build    # 产出 dist/，可交给 nginx 托管
```

> 已验证：`npm run build` 通过（1693 模块，BUILD_EXIT=0）。Vite 代理在 `vite.config.js`，将 `/api` 转发到 `VITE_API_BASE`（开发默认 `http://127.0.0.1:8080`）。登录态用 Pinia + localStorage 存 token，请求拦截器注入 `Authorization: Bearer <token>`，响应 401 自动跳登录。

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
> ⚠️ `vidora-modules/vidora-video` 镜像若要启用转码，需基于 `jrottenberg/ffmpeg` 等多阶段镜像预装 ffmpeg/ffprobe（当前 Dockerfile 用 `eclipse-temurin:25-jdk` 基础镜像，未含 ffmpeg；生产转码建议改用"独立转码集群/云 MPS"，与 `docs/ARCHITECTURE.md` 第 8 节一致）。

## 文档

- 架构落地与演进：`docs/ARCHITECTURE.md`（含第 8 节「视频存储与 FFmpeg 多媒体处理 / 异步多清晰度 HLS 流水线」、第 9 节「待完善」）

## 后续待完善

- [x] 视频存储抽象（MinIO / 本地可切换）
- [x] FFmpeg 探测 / 多清晰度 HLS 转码 / 抽封面
- [x] 异步转码流水线 + 任务状态机（video-service）
- [x] 网关 JWT 鉴权 + 权限头透传
- [x] Spring Security 授权 + RBAC 菜单控制（接口级 `@PreAuthorize`）
- [ ] 转码异步化升级为 RocketMQ/Kafka + 独立转码集群（当前为 `@Async` 线程池原型）
- [ ] MinIO 分片上传 + MD5 秒传（`video_multipart_upload` 表已留）
- [ ] Redisson 分布式锁（点赞/计数防超卖）
- [ ] RocketMQ 生产者/消费者（转码触发、计数更新、弹幕入库）
- [ ] Elasticsearch 全文检索（search-service 落地）
- [ ] Sentinel 限流规则（流控/熔断）
- [ ] BFF 聚合层
- [ ] 单元测试

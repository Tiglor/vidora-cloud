# 部署指南（Docker Compose）

本目录提供视频播放平台后端 **9 个微服务与基础设施的一站式编排**，与根工程下各模块目录**一一对应**。

```
vidora-cloud/
├── vidora-gateway/Dockerfile                                  ←→ gateway-service   :8080
├── vidora-auth/Dockerfile                                     ←→ auth-service      :8101
├── vidora-modules/
│   ├── vidora-video/Dockerfile                                ←→ video-service     :8102
│   ├── vidora-content/Dockerfile                              ←→ content-service   :8103
│   ├── vidora-interact/Dockerfile                             ←→ interact-service  :8104
│   ├── vidora-message/Dockerfile                              ←→ message-service   :8105
│   ├── vidora-search/Dockerfile                               ←→ search-service    :8106
│   ├── vidora-recommend/Dockerfile                            ←→ recommend-service :8107
│   └── vidora-system/Dockerfile                               ←→ system-service    :8108
├── SQL/                                                       # 7 个建库建表脚本
└── deploy/
    ├── .env.example         # 版本 / 密码 / JWT 密钥等变量模板（复制为 .env 后修改）
    ├── docker-compose.yml   # 编排（MySQL / Redis / Nacos / MinIO / Sentinel / RocketMQ + 9 服务）
    ├── rocketmq/broker.conf # Broker 容器网络配置
    └── nginx/nginx.conf     # 可选：/api 反代到网关
```

## 一、前置准备：本地 Maven 打包

各 Dockerfile 采用「本地 `mvn package` 产出 `target/*.jar`，再 COPY 进镜像」的离线友好方式（依赖已缓存于 `F:\repository`）：

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml `
  -pl vidora-gateway,vidora-auth,vidora-modules/vidora-system,vidora-modules/vidora-video,vidora-modules/vidora-content,vidora-modules/vidora-interact,vidora-modules/vidora-message,vidora-modules/vidora-search,vidora-modules/vidora-recommend `
  -am package -DskipTests -o
```

> 基础镜像默认 `eclipse-temurin:25-jdk`；若离线环境拉不到，可改用 `bellsoft/liberica-runtime-container:jdk-25`（改各 Dockerfile 的 FROM）。

## 二、初始化数据库

`SQL/` 下只有一个文件 `vidora_cloud.sql`：建 **一个业务库 `vidora_cloud`**，内部按业务功能分 8 节（用户与权限 / 审计日志 / 视频与转码 / 内容运营 / 互动 / 站内消息 / 搜索 / 推荐），33 张表 + 全部种子数据。

| 使用方 | 管的表（`vidora_cloud` 库内的节） |
|--------|--------|
| system-service | `sys_*` / `user_*`（第 1 节）+ 第 2 节两张 `sys_*_log`；auth-service **不连库**，经 Dubbo 向它取 |
| video-service | `video_*`（第 3 节） |
| content-service | `content_*`（第 4 节） |
| interact-service | `interact_*`（第 5 节） |
| message-service | `message_*`（第 6 节） |
| search-service | `search_*`（第 7 节） |
| recommend-service | `recommend_*`（第 8 节） |

除 gateway 与 auth 外的 7 个服务连的是**同一个库**：库级隔离没有了，边界靠表名前缀守（一个服务的 Mapper 只碰自己那一节），跨域取数据走 `vidora-api` / Dubbo。

compose 已把 `../SQL` 挂到 MySQL 的 `/docker-entrypoint-initdb.d`，**只在首次启动（数据卷为空时）自动执行**。注意脚本是普通 DDL（建库建表不带 `IF NOT EXISTS`），所以它只在空库上跑得通：已有 `vidora_cloud` 库时重跑会直接报错停下（`ERROR 1007/1050`），这是刻意的 —— 静默跳过会让人以为结构改动生效了，其实库一个字没变。

要重建库（改了 `vidora_cloud.sql` 之后）：

```bash
# 方式一：删卷重建（会连数据一起没，动手前确认没有要留的）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down -v
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d

# 方式二：只重建业务库，保留卷
docker exec -i vidora-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e 'DROP DATABASE IF EXISTS vidora_cloud;'
docker exec -i vidora-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < SQL/vidora_cloud.sql
```

不方便重建的，手工执行本次变更等价的 `ALTER`（交付说明里会给出）。

## 三、构建镜像并启动

```bash
cp deploy/.env.example deploy/.env      # 然后修改其中所有 change-me
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

- 网关入口：`http://<宿主机>:8080`（对外统一 `/api`）
- Nacos 控制台：`http://<宿主机>:8848/nacos`（默认 nacos/nacos）
- MinIO 控制台：`http://<宿主机>:9001`（默认 minioadmin/minioadmin）
- Sentinel 控制台：`http://<宿主机>:8858`（默认 sentinel/sentinel）
- 各微服务端口按上图映射

## 四、配置注入说明

`docker-compose.yml` 已通过环境变量把基础设施地址注入各服务（Spring Boot 宽松绑定，`SPRING_DATASOURCE_URL` → `spring.datasource.url`）：

| 变量 | 作用 |
|------|------|
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Nacos 注册中心地址（当前只用服务发现，未接配置中心） |
| `DUBBO_REGISTRY_ADDRESS` | Dubbo 注册中心（仅 auth-service / system-service）。**必须带 `?namespace=dubbo`**：这个变量整体覆盖 `application.yml` 里已带 namespace 的默认值，漏了就会把 Dubbo 的 20880 注册进 `public`，与 Spring Cloud 的 HTTP 实例同名，网关负载均衡打到 20880 的请求全部 500 |
| `SPRING_DATASOURCE_URL` | 各服务自己的库（见上表），主机名 `mysql` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | MySQL 账号 |
| `SPRING_DATA_REDIS_HOST` / `PORT` / `PASSWORD` | Redis 连接（主机名 `redis`） |
| `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD` | Sentinel 控制台地址 |
| `JWT_SECRET` | 签名密钥，**gateway 与 auth 必须相同**，长度 >= 32 字节 |
| `STORAGE_*` | MinIO 存储（video-service 上传/转码产物） |
| `FFMPEG_*` | ffmpeg/ffprobe 可执行路径（video-service 转码） |
| `ROCKETMQ_ENABLED` | 是否启用 MQ 投递转码任务；**应用侧默认 `false`**，容器里必须显式置 `true`，否则转码退回本地线程池、进程重启会丢在途任务 |
| `ROCKETMQ_NAME_SERVER` | 消息队列 NameServer 地址 |

> Nacos 当前**只用于服务注册与发现**，没有接配置中心：每个服务的配置都在自己那一个 `application.yml` 里。
> Nacos 未就绪时服务**启动即失败**，不是「先起来、注册不上」：`spring.cloud.nacos.discovery.fail-fast`
> 默认 `true`，注册失败会中断启动（`Failed to start bean 'webServerStartStop'`）。
> 排查时注意 Nacos 2.x 除 8848 外还要通 **9848 / 9849**（gRPC）——这两个端口是客户端按
> 「主端口 +1000 / +1001」自己算出来的，服务端不会告知，所以「服务跑宿主机、Nacos 跑容器」时
> 必须在 compose 里一并发布，且宿主机端口号要与容器内一致。只通 8848 的表现很有迷惑性：
> `/nacos/v1/console/health/readiness` 照样返回 `OK`，服务却在注册时报
> `ErrCode:-401, ErrMsg:Client not connected, current status:STARTING`。
> 重新启用配置中心的步骤见 `.code/ARCHITECTURE.md` 6.2。
>
> 各服务 `application.yml` 里的凭据与地址都写成 `${环境变量:开发默认值}` 形式
> （`jwt.secret`、数据库/Redis 口令、MinIO AK/SK、`rocketmq.*`），
> 所以上表这些变量既能覆盖容器内配置，裸机/IDE 直接跑时也能用同一套变量名注入，无需改文件。

## 五、视频转码的 FFmpeg 依赖

`video-service` 转码依赖 `ffmpeg` + `ffprobe` 命令行：

- 该服务的 Dockerfile 已在构建期 `apt-get install ffmpeg`，容器内可直接转码；构建此镜像时需要能访问 apt 源。
- 若在离线环境构建，可改为运行时挂载宿主机二进制：
  ```yaml
  volumes:
    - /usr/bin/ffmpeg:/usr/bin/ffmpeg:ro
    - /usr/bin/ffprobe:/usr/bin/ffprobe:ro
  ```
- 更彻底的做法是把转码抽成独立转码集群 / 改用云 MPS，video-service 仅提交任务 + 轮询结果（见 `.code/ARCHITECTURE.md` 第 8 节）。
- `transcode.enabled=false` 时可关闭上传自动转码（仅存源文件，`play-url` 返回源文件地址）。

## 六、前端 Web 端

Web 前端位于独立包 `../vidora-web/`（Vue 3）：

```bash
cd ..\..\vidora-web
npm install
npm run dev          # 开发：5173 代理到网关 8080
npm run build        # 产物 dist/，可由 nginx 托管并反代 /api
```

生产建议：nginx 托管 `dist/` 静态文件，并把 `/api` 反代到网关（参考 `deploy/nginx/nginx.conf`）。

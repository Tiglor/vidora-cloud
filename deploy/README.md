# 部署指南（Docker Compose）

本目录提供视频播放平台后端 **9 个微服务与基础设施的一站式编排**，与根工程下各模块目录**一一对应**。

```
vidora-cloud/
├── auth/Dockerfile                                            ←→ vidora-auth      :8101
├── gateway/Dockerfile                                         ←→ vidora-gateway   :8080
└── modules/
    ├── system/Dockerfile                                      ←→ vidora-system    :8108
    ├── video/Dockerfile                                       ←→ vidora-video     :8102
    ├── content/Dockerfile                                     ←→ vidora-content   :8103
    ├── interact/Dockerfile                                    ←→ vidora-interact  :8104
    ├── message/Dockerfile                                     ←→ vidora-message   :8105
    ├── search/Dockerfile                                      ←→ vidora-search    :8106
    └── recommend/Dockerfile                                   ←→ vidora-recommend :8107
└── deploy/
    ├── .env                 # 版本 / 密码 / JVM 参数等变量
    ├── docker-compose.yml   # 编排（MySQL / Redis / Nacos / MinIO + 9 服务）
    └── nginx/nginx.conf     # 可选：/api 反代到网关
```

## 一、前置准备：本地 Maven 打包

各 Dockerfile 采用「本地 `mvn package` 产出 `target/*.jar`，再 COPY 进镜像」的离线友好方式（依赖已缓存于 `F:\repository`）：

```powershell
$env:JAVA_HOME = 'D:\Java\otherJDK\bellsoft-jdk25.0.4.1+1-windows-amd64\jdk-25.0.4.1'
& 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml `
  -pl gateway,auth,modules/system,modules/video,modules/content,modules/interact,modules/message,modules/search,modules/recommend `
  -am package -DskipTests -o
```

> 基础镜像默认 `eclipse-temurin:25-jdk`；若离线环境拉不到，可改用 `bellsoft/liberica-runtime-container:jdk-25`（改各 Dockerfile 的 FROM）。

## 二、初始化数据库

MySQL 容器起来后，逐条导入建表脚本（每个服务独立 schema 建于 `video_platform` 库内）：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/01_user_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/02_video_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/03_content_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/04_interact_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/05_message_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/06_search_service.sql
mysql -h 127.0.0.1 -P 3306 -u root -p video_platform < SQL/07_recommend_service.sql
```

## 三、构建镜像并启动

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

- 网关入口：`http://<宿主机>:8080`（对外统一 `/api`）
- Nacos 控制台：`http://<宿主机>:8848/nacos`（默认 nacos/nacos）
- MinIO 控制台：`http://<宿主机>:9001`（默认 minioadmin/minioadmin）
- 各微服务端口按上图映射

## 四、配置注入说明

`docker-compose.yml` 已通过环境变量把基础设施地址注入各服务：

| 变量 | 作用 |
|------|------|
| `SPRING_CLOUD_NACOS_DISCOVERY/CONFIG_SERVER-ADDR` | Nacos 注册/配置中心地址 |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | MySQL 连接（主机名 `mysql`） |
| `SPRING_DATA_REDIS_HOST/PORT/PASSWORD` | Redis 连接（主机名 `redis`） |
| `STORAGE_*` | MinIO 存储（video-service 启用转码/上传） |
| `FFMPEG_*` | ffmpeg/ffprobe 可执行路径（video-service 转码） |

> 若 `application.yml` 里用的是别的属性名（如 `spring.redis.host`），请按实际改为对应的环境变量形式；服务采用 `optional:nacos:` 导入，Nacos 未就绪也能先以本地配置启动。

## 五、视频转码的 FFmpeg 依赖

`video-service` 转码依赖 `ffmpeg` + `ffprobe` 命令行：

- 当前基础镜像（eclipse-temurin）**不含 ffmpeg**，直接转码会失败。两种解决：
  1. 改用自带 ffmpeg 的基础镜像（如 `jrottenberg/ffmpeg` 叠加，或自行制作 `eclipse-temurin:25-jdk + ffmpeg` 镜像）；
  2. 把转码抽成独立转码集群 / 改用云 MPS，video-service 仅提交任务 + 轮询结果（见 `docs/ARCHITECTURE.md` 第 8 节）。
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

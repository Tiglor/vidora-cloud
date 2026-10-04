# 视频播放平台 - video-service 镜像（端口 8102，含 FFmpeg 转码）
# 构建步骤（在 vidora-cloud 根目录执行，依赖离线缓存于 F:\repository）：
#   1) 本地 Maven 打包
#      & 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml -pl vidora-modules/vidora-video -am package -DskipTests -o
#   2) 构建镜像（构建上下文为该服务目录）
#      docker build -t vidora/video-service:1.0.0 vidora-modules/vidora-video
FROM eclipse-temurin:25-jdk
# 若离线环境拉不到 eclipse-temurin:25-jdk，可改用 bellsoft/liberica-runtime-container:jdk-25

# 转码依赖 ffmpeg + ffprobe 命令行，构建期装入镜像（此步需要能访问 apt 源）
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8102
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

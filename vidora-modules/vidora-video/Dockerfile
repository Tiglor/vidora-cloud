# 视频播放平台 - video-service 镜像（端口 8102，含 FFmpeg 转码）
# 构建步骤（在 vidora-cloud 根目录执行，依赖离线缓存于 F:\repository）：
#   1) 本地 Maven 打包
#      & 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml -pl modules/video -am package -DskipTests -o
#   2) 构建镜像
#      docker build -t vidora/video-service:1.0.0 modules/video
# 注意：video-service 转码依赖宿主机/镜像内 ffmpeg+ffprobe。
#       若启用转码，请改用含 ffmpeg 的基础镜像，或在运行时挂载宿主机 ffmpeg 二进制。
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8102
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

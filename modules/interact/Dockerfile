# 视频播放平台 - interact-service 镜像（端口 8104）
# 构建步骤（在 vidora-cloud 根目录执行，依赖离线缓存于 F:\repository）：
#   1) 本地 Maven 打包
#      & 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml -pl modules/interact -am package -DskipTests -o
#   2) 构建镜像
#      docker build -t vidora/interact-service:1.0.0 modules/interact
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8104
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

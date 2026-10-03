# 视频播放平台 - gateway-service 镜像（端口 8080）
# 构建步骤（在 vidora-cloud 根目录执行，依赖离线缓存于 F:\repository）：
#   1) 本地 Maven 打包
#      & 'D:\apache-maven-3.9.6\bin\mvn.cmd' -f pom.xml -pl gateway -am package -DskipTests -o
#   2) 构建镜像（构建上下文为该服务目录）
#      docker build -t vidora/gateway-service:1.0.0 gateway
FROM eclipse-temurin:25-jdk
WORKDIR /app
# 若离线环境拉不到 eclipse-temurin:25-jdk，可改用 bellsoft/liberica-runtime-container:jdk-25
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

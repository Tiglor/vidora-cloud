# 视频播放平台 - auth-service 镜像（端口 8101）
# 在 vidora-cloud 根目录先执行 Maven 打包，再构建镜像：
#   docker build -t vidora/auth-service:1.0.0 vidora-auth
FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8101
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# 构建工具和运行时都由镜像提供，可通过 Compose build args 切换同版本镜像来源。
ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
ARG JAVA_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21.0.12.1_1-jre

# 构建阶段执行测试和打包；Maven 依赖缓存可复用于后续构建，不进入最终镜像。
FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress verify

# 运行阶段只复制应用 JAR，避免将源码、构建工具及本地配置带入运行镜像。
FROM ${JAVA_IMAGE} AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/rental-info-sharing.jar app.jar
# 使用固定的非 root 用户运行，减少应用进程在容器内的权限。
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

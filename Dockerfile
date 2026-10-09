ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
ARG JAVA_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21.0.12.1_1-jre

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress verify

FROM ${JAVA_IMAGE} AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/rental-info-sharing.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

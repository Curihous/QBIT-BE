# Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Gradle 설정 파일들 먼저 복사 (캐시 최적화)
COPY gradlew ./
COPY gradle gradle/
COPY settings.gradle .
COPY gradle.properties .
COPY build.gradle .
COPY qbit-api-app/build.gradle qbit-api-app/
COPY qbit-websocket-app/build.gradle qbit-websocket-app/
COPY qbit-domain/build.gradle qbit-domain/
COPY qbit-common/build.gradle qbit-common/
COPY qbit-infra/build.gradle qbit-infra/
COPY qbit-client/qbit-stock-client/build.gradle qbit-client/qbit-stock-client/
COPY qbit-client/qbit-notification-client/build.gradle qbit-client/qbit-notification-client/

RUN chmod +x gradlew

# 의존성 다운로드
RUN ./gradlew :qbit-api-app:dependencies --no-daemon || true

# 소스 코드 복사
COPY qbit-api-app/src qbit-api-app/src/
COPY qbit-websocket-app/src qbit-websocket-app/src/
COPY qbit-domain/src qbit-domain/src/
COPY qbit-common/src qbit-common/src/
COPY qbit-infra/src qbit-infra/src/
COPY qbit-client/qbit-stock-client/src qbit-client/qbit-stock-client/src/
COPY qbit-client/qbit-notification-client/src qbit-client/qbit-notification-client/src/

# JAR 빌드
RUN ./gradlew :qbit-api-app:bootJar -x test --no-daemon

# Extract
FROM eclipse-temurin:17-jre-jammy AS extract
WORKDIR /workspace
COPY --from=build /workspace/qbit-api-app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# Run
FROM eclipse-temurin:17-jre-jammy AS run

# 보안을 위한 non-root 사용자 생성
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# 레이어별 복사 (Spring Boot 레이어드 JAR 최적화)
COPY --from=extract --chown=spring:spring /workspace/extracted/dependencies ./dependencies
COPY --from=extract --chown=spring:spring /workspace/extracted/snapshot-dependencies ./snapshot-dependencies
COPY --from=extract --chown=spring:spring /workspace/extracted/spring-boot-loader ./spring-boot-loader
COPY --from=extract --chown=spring:spring /workspace/extracted/application ./application

# non-root 사용자로 실행
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

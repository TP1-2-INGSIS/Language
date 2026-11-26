# ==========================================
# 1. Build Stage (Gradle + JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties build.gradle.kts settings.gradle.kts ./

# CNC (PrintScript engine) is downloaded from GitHub Packages. Credentials come from BuildKit
# secrets (docker build --secret id=gpr_user,env=GITHUB_ACTOR --secret id=gpr_token,env=GPR_TOKEN),
# so they never end up in an image layer.
RUN --mount=type=secret,id=gpr_user --mount=type=secret,id=gpr_token \
    chmod +x gradlew && \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_user 2>/dev/null)" GPR_TOKEN="$(cat /run/secrets/gpr_token 2>/dev/null)" \
    ./gradlew dependencies --no-daemon

COPY src src
RUN --mount=type=secret,id=gpr_user --mount=type=secret,id=gpr_token \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_user 2>/dev/null)" GPR_TOKEN="$(cat /run/secrets/gpr_token 2>/dev/null)" \
    ./gradlew bootJar --no-daemon

# ==========================================
# 2. Runtime Stage (JRE 21 + New Relic APM)
# ==========================================
FROM ghcr.io/tp1-2-ingsis/monitoring-config:latest
WORKDIR /deployments

RUN groupadd --system --gid 185 app && useradd --system --uid 185 --gid app app

COPY --from=builder --chown=app:app /app/build/libs/app.jar /deployments/app.jar

USER app:app
EXPOSE 8080

ENV NEW_RELIC_APP_NAME=language

ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

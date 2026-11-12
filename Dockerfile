# ==========================================
# 1. Build Stage (Gradle + JDK 21)
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew build -x test -x ktlintCheck -x jacocoTestReport -x jacocoTestCoverageVerification --no-daemon

# ==========================================
# 2. Runtime Stage (JRE 21 liviano)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /deployments

RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

COPY --from=builder --chown=quarkus:quarkus /app/build/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=quarkus:quarkus /app/build/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=quarkus:quarkus /app/build/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=quarkus:quarkus /app/build/quarkus-app/quarkus/ /deployments/quarkus/

USER quarkus:quarkus
EXPOSE 8080

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", "-jar", "/deployments/quarkus-run.jar"]

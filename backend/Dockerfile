# syntax=docker/dockerfile:1
#
# Multi-stage build for the BooKI backend. Build context is backend/.
#   docker build -t booki-backend backend/
# The image is a plain executable Spring Boot jar on a JRE — it runs anywhere
# an OCI container runs (Cloud Run, Fly, Render, ECS, k8s). See docs/deployment.md.

# ---- build: compile and package with the JDK + Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies on their own layer, so a source-only change doesn't refetch them.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- run: just the jar on a JRE, as a non-root user ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app
RUN groupadd --system booki && useradd --system --gid booki booki

# Only the executable boot jar (jar { enabled = false } drops the -plain jar).
COPY --from=build /src/build/libs/*.jar app.jar

USER booki
EXPOSE 8080

# MaxRAMPercentage lets the heap track the container memory limit (e.g. 75% of
# Cloud Run's 512Mi). Override JAVA_OPTS at deploy time if needed.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

# syntax=docker/dockerfile:1
# Backend image: build with the Maven wrapper on JDK 25, run on a JRE only.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts may have CRLF line endings, which break the wrapper script.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /src/app.jar

FROM eclipse-temurin:25-jre
# curl is only used by the container health check.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data/attachments && chown -R app:app /data
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app
ENV ATTACHMENTS_DIR=/data/attachments \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

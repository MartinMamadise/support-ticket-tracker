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
# No packages are installed (no apt-get), so the build does not need Ubuntu's package servers.
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data/attachments && chown -R app:app /data
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app
ENV ATTACHMENTS_DIR=/data/attachments \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
# Health check without curl: bash opens a TCP connection to the health endpoint and looks for "UP".
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

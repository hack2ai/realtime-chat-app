FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress --strict-checksums dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress --strict-checksums -DskipTests package

FROM eclipse-temurin:21.0.12_8-jre
ARG VCS_REF=unknown
ARG VERSION=dev
LABEL org.opencontainers.image.title="Real-Time Chat Application" \
      org.opencontainers.image.description="Secure Java 21 TCP chat server with MySQL persistence and optional TLS" \
      org.opencontainers.image.source="https://github.com/hack2ai/realtime-chat-app" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.version="$VERSION"
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
WORKDIR /app
COPY --from=build /workspace/target/chatapp-server.jar /app/chatapp-server.jar
RUN useradd --user-group --no-create-home --uid 10001 --shell /usr/sbin/nologin chatapp \
    && mkdir -p /app/data/attachments \
    && chown chatapp:chatapp /app/data/attachments \
    && chmod 0755 /app /app/data \
    && chmod 0755 /app/data/attachments \
    && chown root:root /app/chatapp-server.jar \
    && chmod 0444 /app/chatapp-server.jar \
    && test "$(id -u chatapp):$(id -g chatapp)" = "10001:10001" \
    && test "$(stat -c '%u:%g:%a' /app/chatapp-server.jar)" = "0:0:444"
USER chatapp
STOPSIGNAL SIGTERM
EXPOSE 5050
VOLUME ["/app/data/attachments"]
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=10s CMD java -cp /app/chatapp-server.jar com.chatapp.server.ServerHealthCheck
ENTRYPOINT ["java", "-jar", "/app/chatapp-server.jar"]

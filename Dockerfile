FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre
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
RUN useradd --system --create-home --uid 10001 chatapp \
    && mkdir -p /app/data/attachments \
    && chown chatapp:chatapp /app/data/attachments \
    && chmod 0755 /app /app/data \
    && chmod 0755 /app/data/attachments \
    && chown root:root /app/chatapp-server.jar \
    && chmod 0444 /app/chatapp-server.jar \
    && test "$(stat -c '%u:%g:%a' /app/chatapp-server.jar)" = "0:0:444"
USER chatapp
STOPSIGNAL SIGTERM
EXPOSE 5050
VOLUME ["/app/data/attachments"]
HEALTHCHECK --interval=30s --timeout=3s --retries=3 --start-period=20s CMD-SHELL test -f /tmp/chatapp.ready
ENTRYPOINT ["java", "-jar", "/app/chatapp-server.jar"]

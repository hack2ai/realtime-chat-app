FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre
ARG VCS_REF=unknown
ARG VERSION=dev
LABEL org.opencontainers.image.title="Real-Time Chat Application" \
      org.opencontainers.image.description="Secure Java 21 TCP chat server with MySQL persistence and optional TLS" \
      org.opencontainers.image.source="https://github.com/hack2ai/realtime-chat-app" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.version="$VERSION"
WORKDIR /app
COPY --from=build /workspace/target/chatapp-server.jar /app/chatapp-server.jar
RUN mkdir -p /app/data/attachments && useradd --system --create-home --uid 10001 chatapp && chown -R chatapp:chatapp /app
USER chatapp
STOPSIGNAL SIGTERM
EXPOSE 5050
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=10s CMD test -f /tmp/chatapp.ready
ENTRYPOINT ["java", "-jar", "/app/chatapp-server.jar"]

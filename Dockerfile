FROM maven:3.9.11-eclipse-temurin-21 AS build
RUN useradd --create-home --uid 10000 builder && mkdir -p /workspace && chown builder:builder /workspace
WORKDIR /workspace
COPY --chown=builder:builder pom.xml .
COPY --chown=builder:builder src ./src
USER builder
ENV MAVEN_CONFIG=/home/builder/.m2
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG APP_VERSION=unknown
ARG VCS_REF=unknown
LABEL org.opencontainers.image.source="https://github.com/hack2ai/realtime-chat-app" \
      org.opencontainers.image.description="Secure Java 21 real-time chat server" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="$APP_VERSION" \
      org.opencontainers.image.revision="$VCS_REF"
COPY --from=build /workspace/target/chatapp-server.jar /app/chatapp-server.jar
RUN useradd --system --create-home --uid 10001 chatapp \
    && mkdir -p /app/data/attachments \
    && chown -R chatapp:chatapp /app/data/attachments \
    && chmod 0700 /app/data/attachments \
    && chmod 0555 /app/chatapp-server.jar
USER chatapp
EXPOSE 5050
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 CMD ["java", "-cp", "/app/chatapp-server.jar", "com.chatapp.server.ServerHealthCheck"]
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/chatapp-server.jar"]

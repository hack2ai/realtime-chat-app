FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/chatapp-server.jar /app/chatapp-server.jar
RUN mkdir -p /app/data/attachments && useradd --system --create-home --uid 10001 chatapp && chown -R chatapp:chatapp /app
USER chatapp
EXPOSE 5050
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 CMD test -f /tmp/chatapp.ready || exit 1
ENTRYPOINT ["java", "-jar", "/app/chatapp-server.jar"]

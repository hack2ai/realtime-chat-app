FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/chatapp-server.jar /app/chatapp-server.jar
RUN mkdir -p /app/data/attachments && useradd --system --create-home --uid 10001 chatapp && chown -R chatapp:chatapp /app
USER chatapp
STOPSIGNAL SIGTERM
EXPOSE 5050
ENTRYPOINT ["java", "-jar", "/app/chatapp-server.jar"]

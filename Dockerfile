FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY frontend ./frontend
COPY scripts/docker-package.sh ./
RUN sh ./docker-package.sh

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bookkeeper && adduser -S bookkeeper -G bookkeeper
WORKDIR /app
COPY --from=build /workspace/target/kayak-bookkeeper-1.0.0.jar /app/app.jar
USER bookkeeper
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Resolve dependencies in a separate layer so source-only changes rebuild faster.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S chainstore && adduser -S chainstore -G chainstore

WORKDIR /app
COPY --from=build --chown=chainstore:chainstore /workspace/target/*.jar app.jar

USER chainstore

EXPOSE 4313

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:4313/swagger-ui/index.html || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

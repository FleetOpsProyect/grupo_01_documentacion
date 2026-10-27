# syntax=docker/dockerfile:1

# ---------- Etapa de construcción ----------
FROM eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24 AS builder
WORKDIR /workspace

# Primero el wrapper y los descriptores, para reutilizar la caché de dependencias
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src/main ./src/main
RUN ./gradlew bootJar --no-daemon -q

# ---------- Etapa de ejecución ----------
FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24 AS runtime
WORKDIR /app

RUN addgroup -S fleetops && adduser -S -G fleetops -H -s /sbin/nologin fleetops

# Solo el artefacto ejecutable; nada del código fuente
COPY --from=builder --chown=fleetops:fleetops /workspace/build/libs/app.jar ./app.jar

USER fleetops

# El puerto lo define SERVER_PORT (variable de entorno) y la JVM se ajusta con JAVA_TOOL_OPTIONS
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

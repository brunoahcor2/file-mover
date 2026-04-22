# ─── Build Stage ─────────────────────────────────────────────────────────────
FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
# Download deps em camada separada para cache eficiente
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ─── Runtime Stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Usuário não-root para segurança
RUN groupadd --system filemover && useradd --system --gid filemover filemover

WORKDIR /app

# Criar diretórios de dados e logs
RUN mkdir -p /data/source /data/destination /data/error /logs \
    && chown -R filemover:filemover /app /data /logs

COPY --from=builder /app/target/file-mover-*.jar app.jar

# JVM flags otimizados para container
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=prod"

USER filemover

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

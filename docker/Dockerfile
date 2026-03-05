# ============================================
# Multi-stage Dockerfile for EZ Key Services
# ============================================
# Stage 1: Build all modules with Maven
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy all POMs first (for dependency caching)
# Note: We copy ezkey-tests/pom.xml so Maven can resolve it, but we don't build it
COPY pom.xml ./pom.xml
COPY checkstyle-config/pom.xml ./checkstyle-config/pom.xml
COPY ezkey-core/pom.xml ./ezkey-core/pom.xml
COPY ezkey-migration/pom.xml ./ezkey-migration/pom.xml
COPY ezkey-admin-api/pom.xml ./ezkey-admin-api/pom.xml
COPY ezkey-auth-api/pom.xml ./ezkey-auth-api/pom.xml
COPY ezkey-m2m-api/pom.xml ./ezkey-m2m-api/pom.xml
COPY ezkey-crypto-api/pom.xml ./ezkey-crypto-api/pom.xml
COPY ezkey-sdk/java/pom.xml ./ezkey-sdk/java/pom.xml
COPY ezkey-demo-device/pom.xml ./ezkey-demo-device/pom.xml
COPY ezkey-demo-device/openapi-spec.json ./ezkey-demo-device/openapi-spec.json
COPY ezkey-demo-app-acme/pom.xml ./ezkey-demo-app-acme/pom.xml
COPY ezkey-tests/pom.xml ./ezkey-tests/pom.xml

# Download all dependencies with shared Maven cache (shared across all parallel builds)
# Using BuildKit cache mount - cache persists between builds and is shared across parallel builds
# This step downloads dependencies and caches them for subsequent builds
# Using -pl to explicitly list modules (excludes ezkey-tests which uses Karate/TestContainers)
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    echo "📦 Downloading Maven dependencies (using BuildKit cache)..." && \
    mvn dependency:go-offline -Pno-checkstyle -B \
        -pl checkstyle-config,ezkey-core,ezkey-migration,ezkey-admin-api,ezkey-auth-api,ezkey-m2m-api,ezkey-crypto-api,ezkey-sdk/java,ezkey-demo-app-acme,ezkey-demo-device \
        -am || true && \
    echo "✅ Dependencies cached in BuildKit cache"

# Copy all source code
COPY checkstyle-config/src ./checkstyle-config/src
COPY ezkey-core/src ./ezkey-core/src
COPY ezkey-migration/src ./ezkey-migration/src
COPY ezkey-admin-api/src ./ezkey-admin-api/src
COPY ezkey-admin-api/config ./ezkey-admin-api/config
COPY ezkey-auth-api/src ./ezkey-auth-api/src
COPY ezkey-auth-api/config ./ezkey-auth-api/config
COPY ezkey-m2m-api/src ./ezkey-m2m-api/src
COPY ezkey-m2m-api/config ./ezkey-m2m-api/config
COPY ezkey-crypto-api/src ./ezkey-crypto-api/src
COPY ezkey-crypto-api/config ./ezkey-crypto-api/config
COPY ezkey-sdk/java/src ./ezkey-sdk/java/src
COPY ezkey-demo-device/src ./ezkey-demo-device/src
COPY ezkey-demo-app-acme/src ./ezkey-demo-app-acme/src
COPY ezkey-demo-app-acme/config ./ezkey-demo-app-acme/config

# Build all modules in one go (this creates all JARs)
# Using BuildKit cache mount - cache persists between builds and is shared across parallel builds
# Dependencies from previous step are reused from cache, significantly speeding up builds
# Using -pl to explicitly list modules to build (excludes ezkey-tests which uses Karate/TestContainers)
# -am (also-make) ensures dependencies are built, -amd (also-make-dependents) not needed here
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    echo "🔨 Building all modules (using cached dependencies)..." && \
    mvn clean package -DskipTests -Pno-checkstyle -B \
        -pl checkstyle-config,ezkey-core,ezkey-migration,ezkey-admin-api,ezkey-auth-api,ezkey-m2m-api,ezkey-crypto-api,ezkey-sdk/java,ezkey-demo-app-acme,ezkey-demo-device \
        -am && \
    echo "✅ Build completed successfully"

# ============================================
# Stage 2: Runtime image for migration service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS migration
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /build/ezkey-migration/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 3: Runtime image for admin-api service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS admin-api
# Create non-root user and pre-create all required directories with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /etc/ezkey/secrets /etc/ezkey/keysets /var/lib/ezkey/bootstrap && \
    chmod 700 /etc/ezkey/secrets && \
    chmod 755 /etc/ezkey/keysets /var/lib/ezkey/bootstrap && \
    chown -R spring:spring /app /etc/ezkey /var/lib/ezkey
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-admin-api/target/*.jar app.jar
COPY --chown=spring:spring --from=build /build/ezkey-admin-api/config ./config
USER spring:spring
EXPOSE 9080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 4: Runtime image for auth-api service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS auth-api
# Create non-root user and pre-create all required directories with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /etc/ezkey/secrets /etc/ezkey/keysets && \
    chmod 700 /etc/ezkey/secrets && \
    chmod 755 /etc/ezkey/keysets && \
    chown -R spring:spring /app /etc/ezkey
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-auth-api/target/*.jar app.jar
COPY --chown=spring:spring --from=build /build/ezkey-auth-api/config ./config
USER spring:spring
EXPOSE 8080
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 5: Runtime image for m2m-api service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS m2m-api
# Create non-root user and pre-create all required directories with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /etc/ezkey/secrets /etc/ezkey/keysets && \
    chmod 700 /etc/ezkey/secrets && \
    chmod 755 /etc/ezkey/keysets && \
    chown -R spring:spring /app /etc/ezkey
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-m2m-api/target/*.jar app.jar
COPY --chown=spring:spring --from=build /build/ezkey-m2m-api/config ./config
USER spring:spring
EXPOSE 7080
EXPOSE 7081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -f http://localhost:7081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 6: Runtime image for crypto-api service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS crypto-api
# Create non-root user and pre-create all required directories with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /etc/ezkey/secrets /etc/ezkey/keysets && \
    chmod 700 /etc/ezkey/secrets && \
    chmod 755 /etc/ezkey/keysets && \
    chown -R spring:spring /app /etc/ezkey
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-crypto-api/target/*.jar app.jar
COPY --chown=spring:spring --from=build /build/ezkey-crypto-api/config ./config
USER spring:spring
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 6: Runtime image for demo-device service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS demo-device
# Create non-root user and pre-create data directory with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /app/data/enrollments && \
    chown -R spring:spring /app
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-demo-device/target/*.jar app.jar
USER spring:spring
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 7: Runtime image for demo-app-acme service
# ============================================
FROM eclipse-temurin:25-jre-alpine AS demo-app-acme
# Create non-root user and pre-create all required directories with correct ownership at build time
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /app/config /app/data && \
    chown -R spring:spring /app
WORKDIR /app
COPY --chown=spring:spring --from=build /build/ezkey-demo-app-acme/target/*.jar app.jar
# Copy config template
COPY --chown=spring:spring ezkey-demo-app-acme/config/application.properties.example /app/config-template.properties
# Create entrypoint script (runs as spring: no chown/chmod needed)
RUN echo '#!/bin/sh' > /app/entrypoint.sh && \
    echo '# Create application.properties from template if it does not exist' >> /app/entrypoint.sh && \
    echo '# Note: If volume persists, existing file will NOT be overwritten' >> /app/entrypoint.sh && \
    echo '# To reset: Delete /app/config/application.properties before starting container' >> /app/entrypoint.sh && \
    echo 'if [ ! -f /app/config/application.properties ]; then' >> /app/entrypoint.sh && \
    echo '  echo "Creating /app/config/application.properties from template..."' >> /app/entrypoint.sh && \
    echo '  cp /app/config-template.properties /app/config/application.properties' >> /app/entrypoint.sh && \
    echo '  chmod 644 /app/config/application.properties' >> /app/entrypoint.sh && \
    echo 'else' >> /app/entrypoint.sh && \
    echo '  echo "Config file exists, skipping template copy (volume persistence)"' >> /app/entrypoint.sh && \
    echo 'fi' >> /app/entrypoint.sh && \
    echo 'echo "SPRING_CONFIG_ADDITIONAL_LOCATION=$SPRING_CONFIG_ADDITIONAL_LOCATION"' >> /app/entrypoint.sh && \
    echo 'echo "Working directory: $(pwd)"' >> /app/entrypoint.sh && \
    echo 'echo "Config file exists: $([ -f /app/config/application.properties ] && echo yes || echo no)"' >> /app/entrypoint.sh && \
    echo 'exec java -jar /app/app.jar' >> /app/entrypoint.sh && \
    chown spring:spring /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh
USER spring:spring
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["/app/entrypoint.sh"]


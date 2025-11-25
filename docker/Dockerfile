# ============================================
# Multi-stage Dockerfile for EZ Key Services
# ============================================
# Stage 1: Build all modules with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy all POMs first (for dependency caching)
# Note: We copy ezkey-tests/pom.xml so Maven can resolve it, but we don't build it
COPY pom.xml ./pom.xml
COPY checkstyle-config/pom.xml ./checkstyle-config/pom.xml
COPY ezkey-core/pom.xml ./ezkey-core/pom.xml
COPY ezkey-migration/pom.xml ./ezkey-migration/pom.xml
COPY ezkey-admin-api/pom.xml ./ezkey-admin-api/pom.xml
COPY ezkey-auth-api/pom.xml ./ezkey-auth-api/pom.xml
COPY ezkey-crypto-api/pom.xml ./ezkey-crypto-api/pom.xml
COPY ezkey-demo-device/pom.xml ./ezkey-demo-device/pom.xml
COPY ezkey-demo-device/openapi-spec.json ./ezkey-demo-device/openapi-spec.json
COPY ezkey-demo-app-acme/pom.xml ./ezkey-demo-app-acme/pom.xml
COPY ezkey-demo-app-acme/openapi-spec.json ./ezkey-demo-app-acme/openapi-spec.json
COPY ezkey-tests/pom.xml ./ezkey-tests/pom.xml

# Download all dependencies with shared Maven cache (shared across all parallel builds)
# Using BuildKit cache mount - cache persists between builds and is shared across parallel builds
# This step downloads dependencies and caches them for subsequent builds
# Using -pl to explicitly list modules (excludes ezkey-tests which uses Karate/TestContainers)
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    echo "📦 Downloading Maven dependencies (using BuildKit cache)..." && \
    mvn dependency:go-offline -Pno-checkstyle -B \
        -pl checkstyle-config,ezkey-core,ezkey-migration,ezkey-admin-api,ezkey-auth-api,ezkey-crypto-api,ezkey-demo-app-acme,ezkey-demo-device \
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
COPY ezkey-crypto-api/src ./ezkey-crypto-api/src
COPY ezkey-crypto-api/config ./ezkey-crypto-api/config
COPY ezkey-demo-device/src ./ezkey-demo-device/src
COPY ezkey-demo-app-acme/src ./ezkey-demo-app-acme/src

# Build all modules in one go (this creates all JARs)
# Using BuildKit cache mount - cache persists between builds and is shared across parallel builds
# Dependencies from previous step are reused from cache, significantly speeding up builds
# Using -pl to explicitly list modules to build (excludes ezkey-tests which uses Karate/TestContainers)
# -am (also-make) ensures dependencies are built, -amd (also-make-dependents) not needed here
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    echo "🔨 Building all modules (using cached dependencies)..." && \
    mvn clean package -DskipTests -Pno-checkstyle -B \
        -pl checkstyle-config,ezkey-core,ezkey-migration,ezkey-admin-api,ezkey-auth-api,ezkey-crypto-api,ezkey-demo-app-acme,ezkey-demo-device \
        -am && \
    echo "✅ Build completed successfully"

# ============================================
# Stage 2: Runtime image for migration service
# ============================================
FROM eclipse-temurin:21-jre-alpine AS migration
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /build/ezkey-migration/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 3: Runtime image for admin-api service
# ============================================
FROM eclipse-temurin:21-jre-alpine AS admin-api
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /build/ezkey-admin-api/target/*.jar app.jar
COPY --from=build /build/ezkey-admin-api/config ./config
EXPOSE 9080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 4: Runtime image for auth-api service
# ============================================
FROM eclipse-temurin:21-jre-alpine AS auth-api
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /build/ezkey-auth-api/target/*.jar app.jar
COPY --from=build /build/ezkey-auth-api/config ./config
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 5: Runtime image for crypto-api service
# ============================================
FROM eclipse-temurin:21-jre-alpine AS crypto-api
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /build/ezkey-crypto-api/target/*.jar app.jar
COPY --from=build /build/ezkey-crypto-api/config ./config
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Stage 6: Runtime image for demo-device service
# ============================================
FROM eclipse-temurin:21-jre-alpine AS demo-device
RUN apk add --no-cache curl su-exec && \
    addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /build/ezkey-demo-device/target/*.jar app.jar
# Create entrypoint script that ensures directory exists with correct permissions
RUN echo '#!/bin/sh' > /app/entrypoint.sh && \
    echo 'mkdir -p /app/data/enrollments' >> /app/entrypoint.sh && \
    echo 'chown -R spring:spring /app/data 2>/dev/null || true' >> /app/entrypoint.sh && \
    echo 'exec su-exec spring:spring java -jar app.jar' >> /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["/app/entrypoint.sh"]


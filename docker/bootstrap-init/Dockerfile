# Bootstrap Init Container
# Performs automatic enrollment bind+verify and seeds demo-device for Docker demos

FROM alpine:3.19

# Install curl and jq, create non-root user matching demo-device spring user (UID 100/GID 101)
# Pre-create shared volume directories with correct ownership at build time
RUN apk add --no-cache curl jq && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app /bootstrap /app/data/enrollments && \
    chmod 755 /app /bootstrap /app/data /app/data/enrollments && \
    chown -R spring:spring /app /bootstrap

WORKDIR /app

# Copy bootstrap init script
COPY --chown=spring:spring docker/bootstrap-init/bootstrap-init.sh /app/bootstrap-init.sh
RUN chmod +x /app/bootstrap-init.sh

USER spring:spring

ENTRYPOINT ["/app/bootstrap-init.sh"]


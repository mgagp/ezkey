# Bootstrap Init Container
# Performs automatic enrollment bind+verify and seeds demo-device for Docker demos
# Base is tag@sha256 of the multi-arch index so Dependabot can bump tag and digest.

FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf

# Install curl and jq, create non-root user matching demo-device spring user (UID 100/GID 101)
# Pre-create shared volume directories with correct ownership at build time
RUN apk add --no-cache curl jq && \
    addgroup -S -g 101 spring && adduser -S -u 100 -G spring spring && \
    mkdir -p /app /bootstrap /app/data/enrollments && \
    chmod 755 /app /bootstrap /app/data /app/data/enrollments && \
    chown -R spring:spring /app /bootstrap

WORKDIR /app

# Copy bootstrap init script
COPY --chown=spring:spring docker/bootstrap-init/bootstrap-init.sh /app/bootstrap-init.sh
RUN chmod +x /app/bootstrap-init.sh

USER spring:spring

ENTRYPOINT ["/app/bootstrap-init.sh"]


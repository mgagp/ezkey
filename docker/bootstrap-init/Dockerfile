# Bootstrap Init Container
# Performs automatic enrollment bind+verify and seeds demo-device for Docker demos

FROM alpine:3.19

# Install curl and jq for API calls and JSON parsing
RUN apk add --no-cache curl jq

WORKDIR /app

# Copy bootstrap init script
COPY docker/bootstrap-init/bootstrap-init.sh /app/bootstrap-init.sh
RUN chmod +x /app/bootstrap-init.sh

ENTRYPOINT ["/app/bootstrap-init.sh"]


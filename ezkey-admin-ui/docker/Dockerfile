# syntax=docker/dockerfile:1
# Stage 1: Build the React/Vite SPA
FROM node:22-alpine AS builder

WORKDIR /app

# Non-interactive Docker builds: skip npm self-update and funding notices (noise only).
ENV NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false

# Copy dependency manifests first to leverage layer cache
COPY package*.json ./

# Install dependencies using BuildKit npm cache mount for fast subsequent builds
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# OpenAPI spec is required for Orval codegen (canonical: specs/admin-api; committed copy here).
COPY openapi-spec.json ./
# Copy remaining source. src/generated/ is gitignored; Orval creates it at build time.
COPY . .

# BUILD_MODE: test (default) = demo mode on for QA/demos; production = optimized, no demo.
ARG BUILD_MODE=test
# Short git SHA for Public alpha chrome (pass from host: git rev-parse --short=7 HEAD).
ARG VITE_GIT_SHA=unknown
ENV VITE_GIT_SHA=$VITE_GIT_SHA
# Generate API client (Orval) then build. Must run before build — app imports from src/generated/admin-api.
RUN npm run generate:api && \
    if [ "$BUILD_MODE" = "production" ]; then npm run build; else npm run build:test; fi

# Stage 2: Serve the built SPA with Caddy (non-root, Apache 2.0 licensed)
FROM caddy:2-alpine AS runtime

# Copy built assets and Caddyfile
# Use numeric UID 1000 — avoids "no matching entries in passwd" on some Alpine Caddy variants
COPY --from=builder --chown=1000:1000 /app/dist /usr/share/caddy
COPY --chown=1000:1000 docker/Caddyfile /etc/caddy/Caddyfile

# Run as non-privileged user (numeric UID — no dependency on a named user in /etc/passwd)
USER 1000:1000

EXPOSE 8080

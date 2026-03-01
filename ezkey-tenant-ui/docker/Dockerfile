# syntax=docker/dockerfile:1
# Stage 1: Build the React/Vite SPA
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency manifests first to leverage layer cache
COPY package*.json ./

# Install dependencies using BuildKit npm cache mount for fast subsequent builds
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# Copy source and build
COPY . .
RUN npm run build

# Stage 2: Serve the built SPA with Caddy (non-root, Apache 2.0 licensed)
FROM caddy:2-alpine AS runtime

# Copy built assets and Caddyfile
# Use numeric UID 1000 — avoids "no matching entries in passwd" on some Alpine Caddy variants
COPY --from=builder --chown=1000:1000 /app/dist /usr/share/caddy
COPY --chown=1000:1000 docker/Caddyfile /etc/caddy/Caddyfile

# Run as non-privileged user (numeric UID — no dependency on a named user in /etc/passwd)
USER 1000:1000

EXPOSE 8080

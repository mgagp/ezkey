# syntax=docker/dockerfile:1
# Demo Login — one image for the clean start and the community instance.
# Build context: the repository root (the app uses the TypeScript SDK from ezkey-sdk/javascript).
#   docker build -f ezkey-demo-login/Dockerfile -t ezkey-demo-login .
# External bases use tag@sha256 of the multi-arch index so Dependabot bumps tag and digest together.

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
ENV NPM_CONFIG_UPDATE_NOTIFIER=false NPM_CONFIG_FUND=false
WORKDIR /src
# The SDK ships its compiled dist/; the app links it with "file:../ezkey-sdk/javascript".
COPY ezkey-sdk/javascript/package.json ezkey-sdk/javascript/
COPY ezkey-sdk/javascript/dist ezkey-sdk/javascript/dist
COPY ezkey-demo-login/package.json ezkey-demo-login/package-lock.json ezkey-demo-login/
WORKDIR /src/ezkey-demo-login
RUN --mount=type=cache,target=/root/.npm npm ci
COPY ezkey-demo-login/ ./
RUN npm run build

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS runtime
ENV NODE_ENV=production \
    DEMO_LOGIN_PORT=8082 \
    DEMO_LOGIN_DB_PATH=/app/data/demo-login.db \
    DEMO_LOGIN_STATIC_DIR=/app/web
WORKDIR /app
# The bundles have no runtime dependency outside Node itself (node:sqlite is built in).
COPY --from=build /src/ezkey-demo-login/dist/server ./server
COPY --from=build /src/ezkey-demo-login/dist/web ./web
RUN mkdir -p /app/data && chown -R node:node /app/data
USER node
VOLUME ["/app/data"]
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8082/healthz || exit 1
CMD ["node", "--disable-warning=ExperimentalWarning", "server/main.js"]

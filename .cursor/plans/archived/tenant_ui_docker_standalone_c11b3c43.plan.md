---
name: Tenant UI Docker Standalone
overview: Create a self-contained Docker Compose + bash script inside `ezkey-tenant-ui/` that builds and serves the SPA from source via Caddy, proxying `/api/*` to the already-running admin-api on `localhost:9080`. The main `docker-compose.yml` stays untouched. Runtime container runs as a non-privileged user.
todos:
  - id: dockerfile
    content: "Create ezkey-tenant-ui/docker/Dockerfile (multistage: node:22 builder with npm cache mount → caddy:2-alpine runtime, non-root USER caddy)"
    status: completed
  - id: caddyfile
    content: Create ezkey-tenant-ui/docker/Caddyfile (listen :8080, file_server for SPA, reverse_proxy /api/* to host.docker.internal:9080, gzip)
    status: completed
  - id: compose-file
    content: Create ezkey-tenant-ui/docker-compose.tenant-ui.yml (single tenant-ui service, port 3000:8080, extra_hosts for Linux compat)
    status: completed
  - id: start-sh
    content: "Create ezkey-tenant-ui/start.sh (bash script: DOCKER_BUILDKIT=1 docker compose up --build, pass-through args)"
    status: completed
isProject: false
---

# Tenant UI -- Standalone Docker Solution

## Decision: Do NOT touch the main `docker-compose.yml`

The main stack (`docker-compose.yml`) already has 11 services, builds exclusively Java with BuildKit Maven cache mounts, and is used daily for every dev cycle. Adding a Node build stage there would:

- Slow down every `clean start` with npm install + Vite build
- Rebuild the UI even when working on backend-only changes
- Add a service that is not required for functional tests to pass

The separate standalone approach is the right call.

---

## Proposed architecture

```
ezkey-tenant-ui/
  start.sh                         # NEW -- bash entry point
  docker-compose.tenant-ui.yml     # NEW -- single-service compose
  docker/
    Dockerfile                     # NEW -- multistage: node build -> Caddy serve
    Caddyfile                      # NEW -- serves SPA + proxies /api/* to host admin-api
```

### Why this location?

Keeping everything inside `ezkey-tenant-ui/` makes the sub-project fully self-contained. A future developer only needs to `cd ezkey-tenant-ui && ./start.sh` -- no knowledge of the outer repo structure required. This directly supports the "5-minute developer experience" goal.

---

## Web server choice: Caddy (replacing nginx)

nginx was dropped due to legitimate governance concerns: F5's acquisition of NGINX Inc. and the subsequent departure of the original authors (Maxim Dounin created `freenginx` in response). The situation introduces uncertainty for a long-term open source dependency.

**Caddy** (`caddy:2-alpine`) is the pragmatic replacement:

- Apache 2.0 license, maintained independently (ZeroSSL / Ardan Labs ecosystem)
- No corporate acquisition risk
- Caddyfile configuration is dramatically simpler than nginx.conf
- `caddy:2-alpine` image is comparable in size (~45 MB)
- Ships with a built-in non-root `caddy` user (uid/gid 82 on Alpine) -- directly addresses the security requirement

---

## Dockerfile design (multistage, BuildKit-friendly)

**Stage 1 -- builder** (`node:22-alpine`):

- Uses `--mount=type=cache,target=/root/.npm` -- same BuildKit cache principle as the Maven cache mount
- `npm ci` (clean install from lockfile, reproducible)
- `npm run build` (Vite production build)
- `VITE_API_BASE_URL` left empty so the SPA uses relative URLs (Caddy handles the proxy)
- Runs as `root` -- acceptable for a build-only stage (no running process, no attack surface)

**Stage 2 -- runtime** (`caddy:2-alpine`):

- Copies `dist/` from builder with `--chown=caddy:caddy`
- Copies `Caddyfile` with `--chown=caddy:caddy`
- Sets `USER caddy` -- container process runs as non-privileged user (uid 82)
- Caddy listens on `:8080` (above 1024, so no `CAP_NET_BIND_SERVICE` needed)
- No Node.js in the final image

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci
COPY . .
RUN npm run build

FROM caddy:2-alpine AS runtime
COPY --from=builder --chown=caddy:caddy /app/dist /usr/share/caddy
COPY --chown=caddy:caddy docker/Caddyfile /etc/caddy/Caddyfile
USER caddy
EXPOSE 8080
```

### Caddyfile key behaviors

```
:8080 {
    root * /usr/share/caddy
    encode gzip

    handle /api/* {
        reverse_proxy host.docker.internal:9080
    }

    handle {
        try_files {path} /index.html
        file_server
    }
}
```

- `handle /api/*` matches first -- proxies to the admin-api running on the host, exactly as `VITE_API_BASE_URL` empty + Vite dev proxy does today
- `handle` fallback: `try_files` + `file_server` handles SPA client-side routing (React Router)
- `encode gzip` for static assets

---

## docker-compose.tenant-ui.yml

```yaml
name: ezkey-tenant-ui
services:
  tenant-ui:
    build:
      context: .
      dockerfile: docker/Dockerfile
    ports:
      - "3000:8080"
    extra_hosts:
      - "host.docker.internal:host-gateway"   # Linux compatibility
    restart: unless-stopped
```

- Port `3000:8080` -- port 3000 on the host (same mental model as `npm run dev`), maps to Caddy's non-privileged port 8080 inside the container
- `extra_hosts: host.docker.internal:host-gateway` makes `host.docker.internal` work on Linux (already native on Windows Docker Desktop and macOS)

---

## start.sh

```bash
#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
DOCKER_BUILDKIT=1 docker compose -f docker-compose.tenant-ui.yml up --build "$@"
```

- `--build` ensures source changes are always picked up
- Passes-through any extra flags (`-d`, `--no-cache`, etc.) via `"$@"`
- `DOCKER_BUILDKIT=1` ensures npm cache mount is honoured

---

## Blind spots and considerations

**HMR / active UI development**: The Docker approach produces a production build -- no hot-module-replacement. `npm run dev` remains the right tool when actively writing UI code. Docker is for "I want to use/demo the UI" mode. This two-mode split is intentional and is the standard practice.

**CORS**: Not a concern here. The browser talks only to Caddy (`localhost:3000`); Caddy proxies to `localhost:9080` server-side. The admin-api never sees a cross-origin request.

**BuildKit npm cache**: The `--mount=type=cache,target=/root/.npm` cache mount means `npm ci` is fast on subsequent builds. First build downloads everything; later builds are near-instant for deps.

`**host.docker.internal` on Linux**: Handled by `extra_hosts: host.docker.internal:host-gateway` in the compose file. Works transparently on all platforms.

**Non-root user**: The runtime container runs as `caddy` (uid 82). The builder stage stays as root -- this is safe and conventional for build-only stages. All files in the runtime image are `chown`ed to `caddy` at image build time.

**Worktree vs. main repo**: Currently the tenant-ui lives in `ezkey-worktree1`. When it eventually merges to `ezkey`, the Docker artifacts move with it -- no path changes needed since the compose context is `"."` (the tenant-ui directory itself).

**Future: add a `--prod` mode**: The `VITE_API_BASE_URL` build arg can be overridden at build time (`docker compose build --build-arg VITE_API_BASE_URL=https://prod.example.com`) for production builds without touching the Dockerfile.

---

## Files to create

- `[ezkey-tenant-ui/docker/Dockerfile](ezkey-tenant-ui/docker/Dockerfile)` -- multistage node build + Caddy runtime (non-root)
- `[ezkey-tenant-ui/docker/Caddyfile](ezkey-tenant-ui/docker/Caddyfile)` -- SPA routing + API proxy + gzip
- `[ezkey-tenant-ui/docker-compose.tenant-ui.yml](ezkey-tenant-ui/docker-compose.tenant-ui.yml)` -- single service, port 3000:8080
- `[ezkey-tenant-ui/start.sh](ezkey-tenant-ui/start.sh)` -- bash entry point


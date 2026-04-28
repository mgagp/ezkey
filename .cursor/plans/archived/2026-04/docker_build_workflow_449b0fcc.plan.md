---
name: docker build workflow
status: completed
archived: "2026-04"
overview: Add a Docker-only developer/QA build workflow that mirrors the intent of `scripts/build.sh` while preserving Docker image build performance and documenting the one unavoidable cache boundary around `spotless:apply`. **Plan execution completed** — delivered `scripts/build-docker.sh`, Docker `build-validation` target, cache/docs cleanup, and documentation updates.
todos:
  - id: add-validation-target
    content: Add a Dockerfile validation target that runs the strict Maven build sequence with BuildKit cache.
    status: completed
  - id: add-build-docker-script
    content: Create a Bash Docker-only build wrapper with default Spotless apply, --check-only, --no-cache, and --diagnose-only modes.
    status: completed
  - id: clarify-cache-model
    content: Update scripts and docs so BuildKit cache and named Maven cache volume are not confused.
    status: completed
  - id: document-workflow
    content: Document the new Docker-only workflow in development and Docker docs.
    status: completed
  - id: validate-workflow
    content: Run the Docker-only diagnostics, validation, formatter, and cache smoke checks after implementation.
    status: completed
isProject: false
---

# Docker-Only Build Workflow Plan

## Goal

Provide a Docker-only command for developers, agents, and QA workstations that can validate the Java reactor without requiring a host JDK or Maven installation. The workflow should be close to [`scripts/build.sh`](scripts/build.sh): diagnostics, formatting, Checkstyle, clean install, and tests excluding `ezkey-tests`.

## Key Finding

The current Docker image build already uses the right optimization pattern in [`docker/Dockerfile`](docker/Dockerfile): a Maven build stage with BuildKit cache mounts on `/root/.m2` using `id=maven-cache`. However, the Docker scripts also create a named Docker volume called `maven-cache` in [`docker/start.sh`](docker/start.sh), [`docker/manage.sh`](docker/manage.sh), and [`docker/docker-compose.yml`](docker/docker-compose.yml). That volume is not the same storage as the BuildKit cache mount and is not currently wired into the Dockerfile build.

This matters because `spotless:apply` must write back to the Git checkout. That requires a bind-mounted container such as `docker run -v "$repo:/workspace" ... mvn spotless:apply`. A normal container cannot directly mount Docker BuildKit's internal cache. So the exact combination of all three properties is not cleanly available:

- write formatted source back to the checkout,
- use only Docker,
- reuse the same internal BuildKit Maven cache used by `docker build`.

## Recommended Design

Add [`scripts/build-docker.sh`](scripts/build-docker.sh) as the user-facing equivalent to [`scripts/build.sh`](scripts/build.sh), because the existing local workflow lives under `scripts/`. It should be Bash-first and runnable from Git Bash on Windows.

Add a dedicated Docker validation target in [`docker/Dockerfile`](docker/Dockerfile), for example `build-validation`, that runs the strict non-mutating Maven sequence with the existing BuildKit cache mount:

```bash
mvn spotless:check
mvn checkstyle:check
mvn clean
mvn install -DskipTests
mvn test -pl '!ezkey-tests'
```

Use `spotless:check` in that target because a Docker build stage works from copied source and cannot safely apply formatting back to the checkout.

To mimic [`scripts/build.sh`](scripts/build.sh), let `scripts/build-docker.sh` run the mutating formatter step by default before validation. It should run a one-shot Maven container with the repo bind-mounted and a named Maven cache volume, then execute `mvn spotless:apply` inside `/workspace`. After that, it should run the BuildKit-backed validation target. The only tradeoff is that this formatter container has a separate Maven cache from the BuildKit cache.

Offer a non-mutating mode such as `--check-only` for CI-style diagnostics or for users who want Docker to report formatting drift without modifying the checkout.

## Proposed Command Shape

- `./scripts/build-docker.sh` first runs `spotless:apply` against the checkout using a bind-mounted Maven container, then runs the BuildKit-backed validation target.
- `./scripts/build-docker.sh --check-only` skips the mutating formatter step and runs only the BuildKit-backed validation target with `spotless:check`.
- `./scripts/build-docker.sh --no-cache` passes through to Docker build for rare clean rebuilds.
- `./scripts/build-docker.sh --diagnose-only` checks Docker, Buildx/BuildKit, Docker Compose, repo path, and effective image/tag assumptions.

## Implementation Steps

1. Extend [`docker/Dockerfile`](docker/Dockerfile) with a `build-validation` stage based on `maven:3.9-eclipse-temurin-25`.
   - Reuse the POM-first copy pattern already present in the `build` stage.
   - Use `RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared` for every Maven command.
   - Do not use `-Pno-checkstyle` in the validation target.
   - Include `ezkey-tests` in the reactor only as needed for `mvn test -pl '!ezkey-tests'` to match `scripts/build.sh`.

2. Add [`scripts/build-docker.sh`](scripts/build-docker.sh).
   - Keep it a thin orchestrator: resolve repo root, check Docker availability, set `DOCKER_BUILDKIT=1`, and call `docker build -f docker/Dockerfile --target build-validation .`.
   - By default, run `maven:3.9-eclipse-temurin-25` with the checkout mounted at `/workspace` and a named cache volume mounted at `/root/.m2`, then run `mvn spotless:apply` inside `/workspace`.
   - For `--check-only`, skip the bind-mounted `spotless:apply` step and rely on the validation target's `spotless:check`.
   - Use container user mapping where practical on Linux/macOS to avoid root-owned formatted files; on Windows/Git Bash, document that Docker Desktop bind mounts generally preserve usable host ownership.

3. Clean up the Maven cache messaging.
   - Either remove the misleading `docker volume create maven-cache` logic from image-build scripts, or rename/document it clearly as only used by the new `--apply` formatter container.
   - Update [`docker/check-cache.sh`](docker/check-cache.sh), which already correctly says BuildKit cache is not visible as a Docker volume.

4. Update documentation.
   - Add a short Docker-only build section to [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).
   - Add a short note to [`docker/README.md`](docker/README.md) explaining the two cache types: BuildKit cache for image/validation builds, named Maven cache volume for bind-mounted formatter runs.

5. Validation after implementation.
   - Run `./scripts/build-docker.sh --diagnose-only`.
   - Run `./scripts/build-docker.sh --check-only` and confirm it fails on formatting differences via `spotless:check`.
   - Run `./scripts/build-docker.sh` on a controlled formatting change and confirm it updates the checkout, then passes the validation stage.
   - Run `./docker/start.sh --debug-cache` or a normal Docker build afterward to confirm existing service image builds still use the same BuildKit cache id.

## Main Risk

A perfectly unified cache for both host-mutating `spotless:apply` and BuildKit image builds is not realistically available with standard Docker primitives. The pragmatic compromise is to keep BuildKit as the authoritative cache for image and validation builds, and use a named Maven cache only for the default bind-mounted formatter path.

## Extra Opportunity

Once this exists, QA can use the same Docker-only build command before clean-start testing. It also gives agents a safer default on machines where Java/Maven setup is inconsistent, while preserving the stricter host `scripts/build.sh` path as the reference until the Docker workflow has proven itself.

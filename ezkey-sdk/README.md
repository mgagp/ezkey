# Ezkey SDK

Client libraries for Ezkey APIs. Language folders are independent; do not assume they share one
generated Admin+Auth surface.

## Java (canonical Integration API client)

Handwritten, **zero compile-scope dependency** client for the **Integration API** (API-key / M2M)
on port **7080**: authentication-attempt create, wait, and cancel only.

It is **not** a generated Admin or Auth wrapper, and it is **not** a device client (bind / verify /
pending / respond). Details: [`java/README.md`](java/). Living demo: `ezkey-demo-app-acme`.

## Other language folders

| Language | Directory | Notes |
|----------|-----------|--------|
| JavaScript/TypeScript | [`javascript/`](javascript/) | Generated Admin + Auth OpenAPI clients (separate from the Java Integration API client) |
| Python | [`python/`](python/) | Experimental / incomplete vs the Java Integration API client |
| .NET | [`dotnet/`](dotnet/) | Experimental / incomplete vs the Java Integration API client |

## API surfaces (do not mix)

| API | Port | Who | Auth |
|-----|------|-----|------|
| Integration API | 7080 | Applications (M2M) | HTTP Basic API key |
| Admin API | 9080 | Operators | Bearer / cookie; API-key auth attempts **off** by default |
| Auth API | 8080 | Devices | Enrollment / device proof tokens |

## Documentation

- [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) — canonical endpoint semantics
- [`docs/LOCAL_STACK_PORTS.md`](../docs/LOCAL_STACK_PORTS.md) — local ports
- OpenAPI (generated artifacts, do not hand-edit): [`specs/`](../specs/)

There is no `ezkey-docs/` tree in this repository.

## License

MIT, same as the main Ezkey project.

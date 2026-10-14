# Ezkey Demo Device — Configuration

Demo-only browser app (port 8083) that simulates a mobile device against Auth API. Properties bind
from JAR `src/main/resources/application.properties` (and optional `application-docker.properties`
when the `docker` profile is active).

## Session cookie

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `server.servlet.session.cookie.name` | `EZKEY_DEVICE_SESSION` | recommandé | Distinct from Acme / APIs on localhost so browsers do not share one `JSESSIONID` across ports (#799). Lives in the JAR (not `config/`); API modules use external `config/` instead. |

Redeploying the demo device drops any in-memory sessions that still used the previous cookie name.

## Auth API URL

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.auth.api.url` | `http://localhost:8080` (IDE) / `http://auth-api:8080` (Docker) | requis | Base URL for bind / verify / pending / respond. |

# Ezkey Java SDK

Minimal **zero compile-scope dependency** client for the Ezkey **Integration API** (API-key / M2M).
It covers authentication-attempt **create**, **wait**, and **cancel** only. It is not a generated
Admin or Auth API wrapper, and it is not a device client (bind / verify / pending / respond).

- Java **17+** (`java.net.http.HttpClient`, records)
- HTTP Basic (`integrationKey:secretKey`)
- Default base URL: `http://localhost:7080`
- JSON via an internal helper — no Jackson (or other) compile dependency
- Logging via `System.Logger`

Living demo: `ezkey-demo-app-acme` (reactor module `ezkey-sdk/java`).

Admin API (`9080`) remains the operator / bearer-token surface. API-key auth attempts on Admin API
are disabled by default (`ezkey.admin.auth.api-key-auth-attempts-enabled=false`).

## Build

From the repository root (preferred, reactor):

```bash
./scripts/build.sh
```

Or the module after a parent install:

```bash
mvn -f ezkey-sdk/java/pom.xml package
```

Artifact: `org.ezkey:ezkey-sdk` (parent POM version).

## Usage

```java
import java.time.Duration;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;

EzkeyClient client = new EzkeyClient("ezkey_ikey_xxx", "ezkey_skey_xxx");

EzkeyClient client = EzkeyClient.builder()
    .integrationKey("ezkey_ikey_xxx")
    .secretKey("ezkey_skey_xxx")
    .baseUrl("https://integration-api.example.com:7080")
    .connectTimeout(Duration.ofSeconds(15))
    .readTimeout(Duration.ofSeconds(60))
    .build();

EzkeyClient client = EzkeyClient.fromEnvironment();
// EZKEY_INTEGRATION_KEY, EZKEY_SECRET_KEY, optional EZKEY_BASE_URL
```

```java
try {
  var created = client.createAuthAttempt(enrollmentId, false);
  var waited = client.waitForAuthAttempt(created.authAttemptId(), 30, 2);
  client.cancelAuthAttempt(created.authAttemptId());
} catch (EzkeyException e) {
  if (e.isClientError()) {
    // 4xx
  } else if (e.isServerError()) {
    // 5xx
  }
}
```

`createAuthAttemptByUserIdentifier` and optional `AuthAttemptContext` are also on `EzkeyClient`.

## Maven

Use the reactor artifact (same version as the parent), not a `system` JAR:

```xml
<dependency>
  <groupId>org.ezkey</groupId>
  <artifactId>ezkey-sdk</artifactId>
  <version>${ezkey.version}</version>
</dependency>
```

## License

MIT License — see `LICENSE` in the project root.

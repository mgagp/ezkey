/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyExceptionReadTimeoutTest
 * Description: Read-timeout classification is by exception type/cause only; connect timeouts excluded.
 */

package org.ezkey.sdk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EzkeyExceptionReadTimeoutTest {

  @Test
  void readTimeoutByCauseTypeOnly() {
    assertTrue(
        new EzkeyException("any", new HttpTimeoutException("request timed out")).isReadTimeout());
    assertTrue(
        new EzkeyException("any", new SocketTimeoutException("Read timed out")).isReadTimeout());
    assertFalse(
        new EzkeyException(
                "Unexpected HTTP status 500 from"
                    + " http://host/api/v1/auth-attempts/1/wait?timeout=20",
                500,
                "{\"detail\":\"proxy timeout\"}")
            .isReadTimeout());
    assertFalse(new EzkeyException("timeout in message only", 502, "timeout").isReadTimeout());
  }

  @Test
  void connectTimeoutIsNotReadTimeout() {
    HttpConnectTimeoutException connect = new HttpConnectTimeoutException("connection timed out");
    assertFalse(new EzkeyException("any", connect).isReadTimeout());
    assertFalse(EzkeyException.isReadTimeoutThrowable(connect));
    assertFalse(EzkeyException.isReadTimeoutThrowable(new IOExceptionWrapper("wrap", connect)));
  }

  @Test
  void httpErrorMessagesOmitWaitQueryString() throws Exception {
    Path source = resolveEzkeyClientSource();
    String content = Files.readString(source, StandardCharsets.UTF_8);
    assertTrue(content.contains("request.uri().getPath()"));
    assertFalse(
        content.contains(
            "default -> \"Unexpected HTTP status \" + status + \" from \" + request.uri();"));
    int connectCatch = content.indexOf("catch (HttpConnectTimeoutException e)");
    int readCatch = content.indexOf("catch (HttpTimeoutException e)");
    assertTrue(connectCatch >= 0, "dedicated HttpConnectTimeoutException catch required");
    assertTrue(readCatch >= 0, "HttpTimeoutException catch required");
    assertTrue(
        connectCatch < readCatch,
        "HttpConnectTimeoutException catch must precede HttpTimeoutException");
  }

  /** Minimal wrapper so cause-chain detection is exercised without a public IOException ctor. */
  private static final class IOExceptionWrapper extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private IOExceptionWrapper(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static Path resolveEzkeyClientSource() {
    Path fromModule =
        Path.of("src/main/java/org/ezkey/sdk/EzkeyClient.java").toAbsolutePath().normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRepo =
        Path.of("ezkey-sdk/java/src/main/java/org/ezkey/sdk/EzkeyClient.java")
            .toAbsolutePath()
            .normalize();
    assertTrue(Files.exists(fromRepo), "EzkeyClient.java not found");
    return fromRepo;
  }
}

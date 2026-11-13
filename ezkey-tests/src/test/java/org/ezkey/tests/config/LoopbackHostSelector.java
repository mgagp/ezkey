/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: LoopbackHostSelector
 * Description: Pick a reachable loopback host when IPv4 localhost is intercepted
 */

package org.ezkey.tests.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses a reachable loopback host for Docker-published ports.
 *
 * <p>On Windows, a more specific {@code 127.0.0.1} listener can coexist with Docker's {@code
 * 0.0.0.0} bind. Java {@code HttpClient} resolves {@code localhost} to IPv4 first, so health probes
 * hang while IPv6 {@code [::1]} still reaches the stack. Git Bash {@code curl localhost} often
 * prefers IPv6 and looks healthy.
 *
 * @since 2026
 */
final class LoopbackHostSelector {

  private static final Logger log = LoggerFactory.getLogger(LoopbackHostSelector.class);

  private static final Object LOCK = new Object();

  private static volatile boolean resolved;

  private static volatile String rewriteToHost;

  private LoopbackHostSelector() {
    // Prevent instantiation.
  }

  /**
   * Probes IPv4 then IPv6 loopback for {@code probeUrl}'s port. If IPv4 is a blackhole and IPv6
   * returns HTTP 200, later {@link #rewriteLoopback(String)} calls rewrite {@code localhost} /
   * {@code 127.0.0.1} to {@code ::1}.
   *
   * @param probeUrl absolute URL used only for host/port/path (typically Admin Actuator health)
   */
  static void resolveAgainst(String probeUrl) {
    synchronized (LOCK) {
      if (resolved) {
        return;
      }
      URI uri = URI.create(probeUrl);
      int port = uri.getPort();
      if (port <= 0) {
        return;
      }

      String ipv4Url = replaceHost(probeUrl, "127.0.0.1");
      if (isHttpOk(ipv4Url, Duration.ofMillis(800))) {
        resolved = true;
        return;
      }
      String ipv6Url = replaceHost(probeUrl, "::1");
      if (isHttpOk(ipv6Url, Duration.ofSeconds(2))) {
        rewriteToHost = "::1";
        log.warn(
            "IPv4 loopback did not respond at {} (timeout or blackhole). Using {} so tests"
                + " reach the Docker stack. Another process is likely bound to 127.0.0.1 on"
                + " this port.",
            ipv4Url,
            ipv6Url);
        resolved = true;
      }
    }
  }

  /**
   * Rewrites loopback URLs onto the host selected by {@link #resolveAgainst(String)}, if any.
   *
   * @param url service or health URL
   * @return rewritten URL, or {@code url} unchanged
   */
  static String rewriteLoopback(String url) {
    String host = rewriteToHost;
    if (host == null || url == null || url.isBlank()) {
      return url;
    }
    return replaceHost(url, host);
  }

  /**
   * Replaces the host of a loopback HTTP URL. Non-loopback hosts are left unchanged.
   *
   * @param url absolute URL
   * @param newHost host without brackets ({@code 127.0.0.1} or {@code ::1})
   * @return URL with {@code newHost}, or {@code url} if the host is not loopback
   */
  static String replaceHost(String url, String newHost) {
    URI uri = URI.create(url);
    if (!isRewritableLoopback(uri.getHost())) {
      return url;
    }
    try {
      return new URI(
              uri.getScheme(),
              uri.getUserInfo(),
              newHost,
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment())
          .toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Cannot rewrite host on URL: " + url, e);
    }
  }

  /** Clears cached resolution. For unit tests only. */
  static void resetForTests() {
    synchronized (LOCK) {
      resolved = false;
      rewriteToHost = null;
    }
  }

  private static boolean isRewritableLoopback(String host) {
    if (host == null) {
      return false;
    }
    return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
  }

  private static boolean isHttpOk(String url, Duration requestTimeout) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create(url)).timeout(requestTimeout).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response != null && response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }
}

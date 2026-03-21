/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: DockerCliLocator
 * Description: Resolves the Docker CLI executable for subprocess use (Windows PATH gaps)
 */
package org.ezkey.tests.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates the Docker CLI so {@link ProcessBuilder} can spawn it even when {@code docker} is not on
 * the JVM's {@code PATH} (common on Windows when tests run from Maven/IDE while Git Bash has a
 * different environment).
 *
 * <p>Override with environment variable {@code EZKEY_DOCKER_CLI} (absolute path to {@code docker}
 * or {@code docker.exe}).
 */
public final class DockerCliLocator {

  private static final Logger log = LoggerFactory.getLogger(DockerCliLocator.class);

  /** Absolute path to the Docker CLI; takes precedence over search. */
  public static final String ENV_DOCKER_CLI = "EZKEY_DOCKER_CLI";

  private static volatile String cached;

  private DockerCliLocator() {}

  /**
   * @return path to the Docker executable (may be {@code "docker"} on Unix if not found elsewhere)
   */
  public static String dockerExecutable() {
    if (cached != null) {
      return cached;
    }
    synchronized (DockerCliLocator.class) {
      if (cached != null) {
        return cached;
      }
      cached = resolve();
      log.debug("Docker CLI resolved to: {}", cached);
      return cached;
    }
  }

  /** Builds a {@link ProcessBuilder} with the resolved Docker executable as argv0. */
  public static ProcessBuilder processBuilder(String... args) {
    List<String> cmd = new ArrayList<>();
    cmd.add(dockerExecutable());
    Collections.addAll(cmd, args);
    return new ProcessBuilder(cmd);
  }

  private static String resolve() {
    String env = System.getenv(ENV_DOCKER_CLI);
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      if (Files.isRegularFile(p)) {
        return p.toAbsolutePath().toString();
      }
    }

    String os = System.getProperty("os.name", "").toLowerCase();
    boolean windows = os.contains("win");
    String pathEnv = System.getenv("PATH");

    if (windows) {
      String pf = System.getenv("ProgramFiles");
      String pf86 = System.getenv("ProgramFiles(x86)");
      String[] candidates =
          new String[] {
            "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe",
            pf != null ? pf + "\\Docker\\Docker\\resources\\bin\\docker.exe" : null,
            pf86 != null ? pf86 + "\\Docker\\Docker\\resources\\bin\\docker.exe" : null,
          };
      for (String c : candidates) {
        if (c == null) {
          continue;
        }
        Path p = Path.of(c);
        if (Files.isRegularFile(p)) {
          return p.toAbsolutePath().toString();
        }
      }
      if (pathEnv != null) {
        for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
          if (dir.isBlank()) {
            continue;
          }
          for (String name : new String[] {"docker.exe", "docker.cmd"}) {
            try {
              Path p = Path.of(dir, name);
              if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().toString();
              }
            } catch (Exception ignored) {
              // continue
            }
          }
        }
      }
    } else if (pathEnv != null) {
      for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
        if (dir.isBlank()) {
          continue;
        }
        Path p = Path.of(dir, "docker");
        if (Files.isRegularFile(p)) {
          return p.toAbsolutePath().toString();
        }
      }
    }

    return "docker";
  }
}

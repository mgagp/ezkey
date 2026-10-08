/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DemoAppAcmeEntrypointScriptTest
 * Description: Entrypoint creates config at 0600 and always re-chmods existing volumes.
 */

package org.ezkey.demo.acme.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class DemoAppAcmeEntrypointScriptTest {

  @TempDir Path tempDir;

  @Test
  void entrypointSourceUsesChmod600OnCreateAndUnconditionally() throws IOException {
    Path script = resolveEntrypoint();
    String content = Files.readString(script, StandardCharsets.UTF_8);
    assertThat(content).contains("chmod 600 /app/config/application.properties");
    assertThat(content)
        .contains("chmod 600 /app/config/application.properties 2>/dev/null || true");
    assertThat(content).doesNotContain("chmod 644");

    Path dockerfile = resolveDockerfile();
    String dockerContent = Files.readString(dockerfile, StandardCharsets.UTF_8);
    assertThat(dockerContent).contains("demo-app-acme-entrypoint.sh");
    assertThat(dockerContent).doesNotContain("chmod 644 /app/config/application.properties");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void entrypointTightensExisting0644ConfigWithoutRoot() throws Exception {
    Path appRoot = tempDir.resolve("app");
    Path configDir = appRoot.resolve("config");
    Files.createDirectories(configDir);
    Path configFile = configDir.resolve("application.properties");
    Files.writeString(configFile, "ezkey.integration-key=\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        configFile,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ));

    Path javaWrapper = appRoot.resolve("java");
    Files.writeString(javaWrapper, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        javaWrapper,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    Files.writeString(appRoot.resolve("app.jar"), "noop", StandardCharsets.UTF_8);

    Path script = resolveEntrypoint();
    Path localScript = appRoot.resolve("entrypoint.sh");
    String adapted =
        Files.readString(script, StandardCharsets.UTF_8)
            .replace("/app/config/application.properties", configFile.toString())
            .replace(
                "/app/config-template.properties",
                appRoot.resolve("template.properties").toString())
            .replace("/app/app.jar", appRoot.resolve("app.jar").toString());
    Files.writeString(localScript, adapted, StandardCharsets.UTF_8);

    ProcessBuilder builder = new ProcessBuilder("sh", localScript.toString());
    builder.directory(appRoot.toFile());
    builder.environment().put("PATH", appRoot + ":" + System.getenv("PATH"));
    builder.redirectErrorStream(true);
    Process process = builder.start();
    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
    assertThat(finished).isTrue();
    assertThat(process.exitValue()).isZero();

    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
    assertThat(perms)
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  }

  private static Path resolveEntrypoint() {
    Path fromModule =
        Path.of("..", "docker", "demo-app-acme-entrypoint.sh").toAbsolutePath().normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRepoRoot =
        Path.of("docker", "demo-app-acme-entrypoint.sh").toAbsolutePath().normalize();
    assertThat(fromRepoRoot).exists();
    return fromRepoRoot;
  }

  private static Path resolveDockerfile() {
    Path fromModule = Path.of("..", "docker", "Dockerfile").toAbsolutePath().normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRepoRoot = Path.of("docker", "Dockerfile").toAbsolutePath().normalize();
    assertThat(fromRepoRoot).exists();
    return fromRepoRoot;
  }
}

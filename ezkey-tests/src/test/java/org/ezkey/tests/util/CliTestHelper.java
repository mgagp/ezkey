/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: CliTestHelper
 * Description: Executes Ezkey CLI commands via Docker for functional tests.
 */

// This file is UTF-8 without BOM.

package org.ezkey.tests.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Helper for executing Ezkey CLI commands inside the CLI Docker container.
 *
 * <p>Primary execution path is Docker-only, which avoids host Python dependencies and ensures
 * consistent execution across environments.
 *
 * @since 2025
 */
public class CliTestHelper {

  private static final Logger log = LoggerFactory.getLogger(CliTestHelper.class);

  private static final String DEFAULT_CONTAINER_NAME = "ezkey-cli-test";
  private static final String CONTAINER_NAME_ENV = "EZKEY_CLI_CONTAINER_NAME";
  private static final String CONFIG_PATH = "/root/.ezkey/ezkey.json";
  private static final String WORK_DIR = "/work";
  private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

  private final DockerStackConfig dockerStackConfig;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String containerName;

  /**
   * Creates a new CLI test helper.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public CliTestHelper(DockerStackConfig dockerStackConfig) {
    this.dockerStackConfig = dockerStackConfig;
    this.containerName = System.getenv().getOrDefault(CONTAINER_NAME_ENV, DEFAULT_CONTAINER_NAME);
  }

  /**
   * Writes the default CLI configuration into the CLI container.
   *
   * <p>Configuration uses Docker service URLs and disables pretty print for easy assertions.
   *
   * @throws IllegalStateException if the config cannot be written
   */
  public void configureDefault() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("adminUrl", dockerStackConfig.getAdminApiUrl());
    config.put("authUrl", dockerStackConfig.getAuthApiUrl());
    config.put("cryptoUrl", dockerStackConfig.getCryptoApiUrl());
    config.put("prettyPrint", false);
    writeConfig(config);
  }

  /**
   * Executes an Ezkey CLI command inside the Docker container.
   *
   * @param args CLI arguments
   * @return execution result with exit code and output
   */
  public CliExecutionResult execute(String... args) {
    ensureContainerRunning();

    List<String> command = new ArrayList<>();
    command.add("docker");
    command.add("exec");
    command.add("-w");
    command.add(WORK_DIR);
    command.add(containerName);
    command.add("ezkey");
    command.addAll(List.of(args));

    log.info("Executing CLI command: {}", String.join(" ", args));
    CliExecutionResult result = runCommand(command);

    log.debug("CLI command exit code: {}", result.exitCode());
    if (!result.stdout().isEmpty()) {
      log.debug("CLI stdout:\n{}", result.stdout());
    }
    if (!result.stderr().isEmpty()) {
      log.debug("CLI stderr:\n{}", result.stderr());
    }

    return result;
  }

  private void writeConfig(Map<String, Object> config) {
    ensureContainerRunning();

    try {
      Path tempFile = Files.createTempFile("ezkey-cli-config", ".json");
      objectMapper.writeValue(tempFile.toFile(), config);
      copyToContainer(tempFile, CONFIG_PATH);
      Files.deleteIfExists(tempFile);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write CLI config", e);
    }
  }

  private void copyToContainer(Path source, String targetPath) {
    List<String> command =
        List.of("docker", "cp", source.toString(), containerName + ":" + targetPath);
    CliExecutionResult result = runCommand(command);
    if (!result.isSuccess()) {
      throw new IllegalStateException("Failed to copy CLI config to container: " + result.stderr());
    }
  }

  private void ensureContainerRunning() {
    List<String> command = List.of("docker", "inspect", "-f", "{{.State.Running}}", containerName);
    CliExecutionResult result = runCommand(command);
    if (!result.isSuccess() || !result.stdout().trim().equalsIgnoreCase("true")) {
      throw new IllegalStateException(
          "CLI container is not running. Start the Docker stack before running CLI tests. "
              + "If needed, set EZKEY_CLI_CONTAINER_NAME to match the container name.");
    }
  }

  private CliExecutionResult runCommand(List<String> command) {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(false);

    try {
      Process process = builder.start();
      boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CliExecutionResult(124, "", "Command timed out");
      }

      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

      return new CliExecutionResult(process.exitValue(), stdout, stderr);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("CLI command interrupted", e);
      return new CliExecutionResult(125, "", e.getMessage());
    } catch (IOException e) {
      log.error("CLI command failed", e);
      return new CliExecutionResult(125, "", e.getMessage());
    }
  }
}

/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BootstrapCredentialsFileExporterTest
 * Description: Credentials file includes recovery codes and is written with 0600 (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.BootstrapCredentialsOutputMode;
import org.ezkey.admin.config.BootstrapExportProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Verifies the bootstrap credentials file channel for recovery codes (issue #750).
 *
 * @since 2026
 */
class BootstrapCredentialsFileExporterTest {

  @TempDir Path tempDir;

  @Test
  void exportWritesRecoveryCodesWithOwnerOnlyPermissions() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);

    exporter.exportIfEnabled(
        enrollment, "proof.tokenValue", "admin.docker", List.of("AAAA-BBBB-CCCC-DDDD"));

    assertThat(file).exists();
    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.get("enrollmentId").asInt()).isEqualTo(7);
    assertThat(json.get("enrollmentProofToken").asString()).isEqualTo("proof.tokenValue");
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("AAAA-BBBB-CCCC-DDDD");

    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
      assertThat(perms)
          .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX CI hosts: content assertions above still apply
    }
  }

  @Test
  void recoveryPrimaryOmitsEnrollmentSecretsButKeepsRecoveryCodes() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(3);
    enrollment.setEnrollmentChallenge(999999);

    exporter.exportIfEnabled(
        enrollment, "must-not-appear", "admin.docker", List.of("ZZZZ-YYYY-XXXX-WWWW"));

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.has("enrollmentChallengeCode")).isFalse();
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("ZZZZ-YYYY-XXXX-WWWW");
    assertThat(exporter.recoveryCodesLogPointer()).contains("0600");
  }

  @Test
  void removeBindSecretsKeepsRecoveryCodes() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    exporter.exportIfEnabled(
        enrollment, "proof.tokenValue", "admin.docker", List.of("AAAA-BBBB-CCCC-DDDD"));

    assertThat(exporter.removeBindSecretsKeepRecoveryCodes(7)).isTrue();

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.has("enrollmentChallengeCode")).isFalse();
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("AAAA-BBBB-CCCC-DDDD");
    assertThat(json.get("enrollmentId").asInt()).isEqualTo(7);

    assertThat(exporter.removeBindSecretsKeepRecoveryCodes(7)).isFalse();
  }

  @Test
  void exportWithRecoveryCodesFailsClosedWhenTargetPathIsDirectory() throws Exception {
    // Reliable under root CI (setWritable(false) is ignored for root).
    Path asDirectory = tempDir.resolve("bootstrap-credentials.json");
    Files.createDirectories(asDirectory);

    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(asDirectory.toString());
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    String secretCode = "FAIL-CLOSED-CODE-9999-8888-7777-6666";

    assertThatThrownBy(
            () ->
                exporter.exportIfEnabled(
                    enrollment, "proof.token", "admin.docker", List.of(secretCode)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("could not be persisted")
        .hasMessageContaining("refusing to complete bootstrap")
        .hasMessageNotContaining(secretCode);

    // Without recovery codes, export failure stays soft (no throw).
    assertThatCode(
            () -> exporter.exportIfEnabled(enrollment, "proof.token", "admin.docker", List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void allowNonPosixDefaultsFalseAndAbsentPropertyFailsClosedOnNonPosixFilesystem() {
    // Guardrail: without allow-non-posix-permissions (Java default false), bootstrap export
    // fails closed on a non-POSIX filesystem — same as Docker/prod overlays that omit the key.
    assertThat(new BootstrapExportProperties().isAllowNonPosixPermissions()).isFalse();

    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    // Do not call setAllowNonPosixPermissions — property absent / default false.
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);
    exporter.setPosixCreateSupportedForTests(false);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    String secretCode = "POSIX-FAIL-CODE-1111-2222";

    assertThat(exporter.logNonPosixAllowedStartupWarnIfActive()).isFalse();

    assertThatThrownBy(
            () ->
                exporter.exportIfEnabled(
                    enrollment, "proof.token", "admin.docker", List.of(secretCode)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UnsupportedOperationException")
        .hasMessageContaining("refusing to complete bootstrap")
        .hasMessageNotContaining(secretCode)
        .satisfies(ex -> assertThat(ex.getCause()).isNull());
    assertThat(file).doesNotExist();
  }

  @Test
  void allowNonPosixTrueEmitsStartupWarnInAdditionToWriteWarn() {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    exportProperties.setAllowNonPosixPermissions(true);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), new AdminMfaProperties());

    Logger exporterLogger =
        (Logger) LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);
    Level previous = exporterLogger.getLevel();
    exporterLogger.setLevel(Level.WARN);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    exporterLogger.addAppender(appender);
    try {
      assertThat(exporter.logNonPosixAllowedStartupWarnIfActive()).isTrue();
      String joined =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.WARN)
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(joined).contains("allow-non-posix-permissions=true");
      assertThat(joined).contains(file.toString());
      assertThat(joined).contains("windows/NTFS fallback");
    } finally {
      exporterLogger.detachAppender(appender);
      exporterLogger.setLevel(previous);
    }
  }

  @Test
  void allowNonPosixTrueInfoDoesNotClaim0600() {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    exportProperties.setAllowNonPosixPermissions(true);
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);
    exporter.setPosixCreateSupportedForTests(false);

    Logger exporterLogger =
        (Logger) LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);
    Level previous = exporterLogger.getLevel();
    exporterLogger.setLevel(Level.INFO);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    exporterLogger.addAppender(appender);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    try {
      exporter.exportIfEnabled(
          enrollment, "proof.token", "admin.docker", List.of("INFO-MODE-CODE-7777"));
      String joined =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.INFO)
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(joined).contains("non-POSIX fallback");
      assertThat(joined).doesNotContain(", 0600)");
    } finally {
      exporterLogger.detachAppender(appender);
      exporterLogger.setLevel(previous);
    }
  }

  @Test
  void allowNonPosixTrueWritesWithWarnLikeWindowsProfile() {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    exportProperties.setAllowNonPosixPermissions(true);
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);
    exporter.setPosixCreateSupportedForTests(false);

    Logger exporterLogger =
        (Logger) LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);
    Level previous = exporterLogger.getLevel();
    exporterLogger.setLevel(Level.WARN);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    exporterLogger.addAppender(appender);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    String secretCode = "WINDOWS-FALLBACK-CODE-5555-6666";

    try {
      assertThatCode(
              () ->
                  exporter.exportIfEnabled(
                      enrollment, "proof.token", "admin.docker", List.of(secretCode)))
          .doesNotThrowAnyException();
      assertThat(file).exists();
      String joined =
          appender.list.stream()
              .filter(e -> e.getLevel() == Level.WARN)
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(joined).contains("POSIX permissions unavailable");
      assertThat(joined).contains("UnsupportedOperationException");
      assertThat(joined).doesNotContain(secretCode);
      for (ILoggingEvent event : appender.list) {
        assertThat(event.getThrowableProxy()).isNull();
      }
    } finally {
      exporterLogger.detachAppender(appender);
      exporterLogger.setLevel(previous);
    }
  }

  @Test
  void exportWithRecoveryCodesFailsClosedOnJacksonRuntime() {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);
    exporter.replaceAtomicWriterForTests(
        (path, json) -> {
          throw new IllegalArgumentException("jackson-like unchecked (test seam)");
        });

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    String secretCode = "JACKSON-FAIL-CODE-3333-4444";

    assertThatThrownBy(
            () ->
                exporter.exportIfEnabled(
                    enrollment, "proof.token", "admin.docker", List.of(secretCode)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("IllegalArgumentException")
        .hasMessageNotContaining(secretCode)
        .satisfies(ex -> assertThat(ex.getCause()).isNull());
  }

  @Test
  void corruptCredentialsFileIsHandledWithoutLoggingContents() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    String secretMarker = "LEAK-ME-RECOVERY-9999-8888-7777-6666";
    Files.writeString(
        file,
        "{ \"enrollmentId\": 7, \"notAnObject\": true, \"recoveryCodes\": [\""
            + secretMarker
            + "\"] ",
        StandardCharsets.UTF_8);

    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());
    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Logger exporterLogger =
        (Logger) LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);
    Level previous = exporterLogger.getLevel();
    exporterLogger.setLevel(Level.ERROR);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    exporterLogger.addAppender(appender);
    try {
      assertThat(exporter.removeBindSecretsKeepRecoveryCodes(7)).isFalse();
      assertThat(exporter.removeBindSecretsKeepRecoveryCodes(7)).isFalse();

      String joined =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);

      assertThat(joined).doesNotContain(secretMarker);
      assertThat(joined).doesNotContain("recoveryCodes");
      assertThat(joined).contains("Failed to redact bind secrets");
      long errorCount = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).count();
      assertThat(errorCount).isEqualTo(1);
      assertThat(joined).containsPattern("\\([A-Za-z0-9_.]+\\)");
      for (ILoggingEvent event : appender.list) {
        assertThat(event.getThrowableProxy()).isNull();
      }
    } finally {
      exporterLogger.detachAppender(appender);
      exporterLogger.setLevel(previous);
    }
  }

  @Test
  void wrongShapeJsonArrayIsHandledWithoutLoggingContents() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    String secretMarker = "ARRAY-SECRET-TOKEN.should.not.appear";
    Files.writeString(
        file, "[\"" + secretMarker + "\", {\"enrollmentId\": 7}]", StandardCharsets.UTF_8);

    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setPath(file.toString());
    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), new AdminMfaProperties());

    Logger exporterLogger =
        (Logger) LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);
    Level previous = exporterLogger.getLevel();
    exporterLogger.setLevel(Level.ERROR);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    exporterLogger.addAppender(appender);
    try {
      assertThat(exporter.removeBindSecretsKeepRecoveryCodes(7)).isFalse();
      String joined =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(joined).doesNotContain(secretMarker);
      assertThat(joined).contains("ClassCastException");
    } finally {
      exporterLogger.detachAppender(appender);
      exporterLogger.setLevel(previous);
    }
  }
}

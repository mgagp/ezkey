package org.ezkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Ezkey encryption using Tink.
 *
 * <p>Backed by application configuration prefix: ezkey.encryption
 *
 * @since 2025
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.encryption")
public class TinkProperties {

  /** Enable/disable encryption at rest. If disabled, encryption will be skipped. */
  private boolean enabled = true;

  /** Path to the master key file (Base64-encoded 256-bit key). */
  private String masterKeyFile;

  /** Path to the encrypted keyset file used by Tink. */
  private String keysetFile;

  /** Selected algorithm for AEAD (e.g., AES256_GCM or CHACHA20_POLY1305). */
  private String algorithm = "AES256_GCM";

  private final Rotation rotation = new Rotation();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getMasterKeyFile() {
    return masterKeyFile;
  }

  public void setMasterKeyFile(String masterKeyFile) {
    this.masterKeyFile = masterKeyFile;
  }

  public String getKeysetFile() {
    return keysetFile;
  }

  public void setKeysetFile(String keysetFile) {
    this.keysetFile = keysetFile;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public Rotation getRotation() {
    return rotation;
  }

  /** Nested rotation configuration. */
  public static class Rotation {
    /** Enable scheduled key rotation. */
    private boolean enabled = true;

    /** Cron schedule for rotation check (default: daily at 2 AM). */
    private String schedule = "0 0 2 * * ?";

    /** Maximum primary key age in days before rotation. */
    private int maxKeyAgeDays = 90;

    /** Backup keyset prior to rotation for rollback. */
    private boolean backupBeforeRotation = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSchedule() {
      return schedule;
    }

    public void setSchedule(String schedule) {
      this.schedule = schedule;
    }

    public int getMaxKeyAgeDays() {
      return maxKeyAgeDays;
    }

    public void setMaxKeyAgeDays(int maxKeyAgeDays) {
      this.maxKeyAgeDays = maxKeyAgeDays;
    }

    public boolean isBackupBeforeRotation() {
      return backupBeforeRotation;
    }

    public void setBackupBeforeRotation(boolean backupBeforeRotation) {
      this.backupBeforeRotation = backupBeforeRotation;
    }
  }
}

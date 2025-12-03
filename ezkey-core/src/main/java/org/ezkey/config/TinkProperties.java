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

  private final Reencryption reencryption = new Reencryption();

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

  public Reencryption getReencryption() {
    return reencryption;
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

    /** Backup retention period in days (default: 365). */
    private int backupRetentionDays = 365;

    /** Minimum number of keys to keep in keyset for decryption (default: 2). */
    private int minKeysInKeyset = 2;

    /** Maximum number of keys before cleanup (default: 5). */
    private int maxKeysInKeyset = 5;

    /** Days after which old keys are automatically disabled (default: 180). */
    private int autoDisableDays = 180;

    public int getBackupRetentionDays() {
      return backupRetentionDays;
    }

    public void setBackupRetentionDays(int backupRetentionDays) {
      this.backupRetentionDays = backupRetentionDays;
    }

    public int getMinKeysInKeyset() {
      return minKeysInKeyset;
    }

    public void setMinKeysInKeyset(int minKeysInKeyset) {
      this.minKeysInKeyset = minKeysInKeyset;
    }

    public int getMaxKeysInKeyset() {
      return maxKeysInKeyset;
    }

    public void setMaxKeysInKeyset(int maxKeysInKeyset) {
      this.maxKeysInKeyset = maxKeysInKeyset;
    }

    public int getAutoDisableDays() {
      return autoDisableDays;
    }

    public void setAutoDisableDays(int autoDisableDays) {
      this.autoDisableDays = autoDisableDays;
    }
  }

  /** Nested re-encryption configuration. */
  public static class Reencryption {
    /** Enable scheduled re-encryption batch processing. */
    private boolean enabled = true;

    /** Cron schedule for re-encryption (default: daily at 3 AM, after rotation). */
    private String schedule = "0 0 3 * * ?";

    /** Number of records to process per batch (default: 500). */
    private int batchSize = 500;

    /** Delay in milliseconds between batches to prevent DB overload (default: 100). */
    private int throttleMs = 100;

    /** Maximum batches to process per execution (default: 100). */
    private int maxBatchesPerRun = 100;

    /** Maximum execution time per run in minutes (default: 60). */
    private int maxDurationMinutes = 60;

    /** Automatically retry failed batches (default: true). */
    private boolean autoRetryFailed = true;

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

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getThrottleMs() {
      return throttleMs;
    }

    public void setThrottleMs(int throttleMs) {
      this.throttleMs = throttleMs;
    }

    public int getMaxBatchesPerRun() {
      return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
      this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public int getMaxDurationMinutes() {
      return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
      this.maxDurationMinutes = maxDurationMinutes;
    }

    public boolean isAutoRetryFailed() {
      return autoRetryFailed;
    }

    public void setAutoRetryFailed(boolean autoRetryFailed) {
      this.autoRetryFailed = autoRetryFailed;
    }
  }
}

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

  private final Keyset keyset = new Keyset();

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

  public Keyset getKeyset() {
    return keyset;
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

    /**
     * Synchronization window in seconds before PENDING key becomes PRIMARY.
     *
     * <p>When a new key is introduced, it is set to PENDING status and will become PRIMARY after
     * this many seconds. This allows all instances to synchronize and load the new keyset before
     * the key is activated.
     *
     * <p>Default: 30 seconds (provides buffer for network latency, database replication, clock
     * skew).
     */
    private int syncWindowSeconds = 30;

    /**
     * Interval in seconds between checks for pending keys ready for promotion.
     *
     * <p>A scheduled job runs at this interval to check if any PENDING keys have passed their
     * effective_at timestamp and should be promoted to PRIMARY.
     *
     * <p>Default: 5 seconds.
     */
    private int promotionCheckIntervalSeconds = 5;

    public int getSyncWindowSeconds() {
      return syncWindowSeconds;
    }

    public void setSyncWindowSeconds(int syncWindowSeconds) {
      this.syncWindowSeconds = syncWindowSeconds;
    }

    public int getPromotionCheckIntervalSeconds() {
      return promotionCheckIntervalSeconds;
    }

    public void setPromotionCheckIntervalSeconds(int promotionCheckIntervalSeconds) {
      this.promotionCheckIntervalSeconds = promotionCheckIntervalSeconds;
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

    /**
     * Parallel workers for submitting <b>distinct</b> re-encryption batches (different batch_id /
     * targets). When 1, batches run sequentially on the caller thread. When &gt; 1, {@link
     * org.ezkey.security.ReencryptionBatchParallelRunner} still serializes work by {@code
     * target_table} so only one batch touches a given table at a time (avoids same-row contention
     * across column batches). With two physical tables, up to two batches may run at once.
     */
    private int parallelBatchWorkers = 1;

    /**
     * Queue capacity for the re-encryption batch executor (bounded back-pressure when parallel
     * workers &gt; 1).
     */
    private int parallelBatchQueueCapacity = 100;

    /**
     * When true, the first fetch slice uses {@link #recentDataChunkSize}; after {@code
     * lastRecordId} is non-null, uses {@link #staleDataChunkSize}. Reduces optimistic-lock pressure
     * on the leading edge of a migration; optional heuristic.
     */
    private boolean temporalBatchSizingEnabled = false;

    /**
     * Chunk size for the first slice (cursor not yet advanced). Used when temporal sizing enabled.
     */
    private int recentDataChunkSize = 250;

    /** Chunk size after the cursor has moved (resume / subsequent slices). */
    private int staleDataChunkSize = 1000;

    /**
     * Number of parallel shards for {@code ezkey_auth_attempt} re-encryption batches ({@code
     * mod(auth_attempt_id, N) = shard_index}). When {@code 1}, auth-attempt batches are a single
     * stream (same as legacy). Values such as 4–8 suit typical Docker deployments.
     */
    private int authAttemptShardCount = 1;

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

    public int getParallelBatchWorkers() {
      return parallelBatchWorkers;
    }

    public void setParallelBatchWorkers(int parallelBatchWorkers) {
      this.parallelBatchWorkers = parallelBatchWorkers;
    }

    public int getParallelBatchQueueCapacity() {
      return parallelBatchQueueCapacity;
    }

    public void setParallelBatchQueueCapacity(int parallelBatchQueueCapacity) {
      this.parallelBatchQueueCapacity = parallelBatchQueueCapacity;
    }

    public boolean isTemporalBatchSizingEnabled() {
      return temporalBatchSizingEnabled;
    }

    public void setTemporalBatchSizingEnabled(boolean temporalBatchSizingEnabled) {
      this.temporalBatchSizingEnabled = temporalBatchSizingEnabled;
    }

    public int getRecentDataChunkSize() {
      return recentDataChunkSize;
    }

    public void setRecentDataChunkSize(int recentDataChunkSize) {
      this.recentDataChunkSize = recentDataChunkSize;
    }

    public int getStaleDataChunkSize() {
      return staleDataChunkSize;
    }

    public void setStaleDataChunkSize(int staleDataChunkSize) {
      this.staleDataChunkSize = staleDataChunkSize;
    }

    public int getAuthAttemptShardCount() {
      return authAttemptShardCount;
    }

    public void setAuthAttemptShardCount(int authAttemptShardCount) {
      this.authAttemptShardCount = authAttemptShardCount;
    }
  }

  /** Nested keyset storage configuration. */
  public static class Keyset {

    /**
     * Keyset storage mode: FILE, DATABASE, or HYBRID.
     *
     * <ul>
     *   <li><b>FILE:</b> Keyset stored only in file (original behavior)
     *   <li><b>DATABASE:</b> Keyset stored in database as source of truth, file as cache
     *   <li><b>HYBRID:</b> Keyset stored in both, database preferred for reads
     * </ul>
     *
     * <p>Default: DATABASE (recommended for distributed deployments).
     */
    private StorageMode storageMode = StorageMode.DATABASE;

    /**
     * Enable degraded mode on decryption failure.
     *
     * <p>When enabled and a decryption failure occurs due to missing key, the instance will:
     *
     * <ul>
     *   <li>Attempt to reload keyset from database
     *   <li>If reload fails, enter degraded mode (read-only)
     *   <li>Log critical errors and emit audit events
     * </ul>
     *
     * <p>Default: false (fail fast on decryption errors).
     */
    private boolean degradedModeEnabled = false;

    /**
     * Maximum retries for keyset reload on decryption failure.
     *
     * <p>Default: 1 (try reload once before giving up).
     */
    private int maxReloadRetries = 1;

    public StorageMode getStorageMode() {
      return storageMode;
    }

    public void setStorageMode(StorageMode storageMode) {
      this.storageMode = storageMode;
    }

    public boolean isDegradedModeEnabled() {
      return degradedModeEnabled;
    }

    public void setDegradedModeEnabled(boolean degradedModeEnabled) {
      this.degradedModeEnabled = degradedModeEnabled;
    }

    public int getMaxReloadRetries() {
      return maxReloadRetries;
    }

    public void setMaxReloadRetries(int maxReloadRetries) {
      this.maxReloadRetries = maxReloadRetries;
    }

    /** Enum for keyset storage modes. */
    public enum StorageMode {
      /** Keyset stored only in file (original behavior). */
      FILE,

      /** Keyset stored in database as source of truth, file as cache. */
      DATABASE,

      /** Keyset stored in both, database preferred for reads. */
      HYBRID
    }
  }
}

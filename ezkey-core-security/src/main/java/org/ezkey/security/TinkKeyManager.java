package org.ezkey.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetManager;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.ezkey.config.TinkProperties;
import org.ezkey.config.TinkProperties.Keyset.StorageMode;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.ezkey.security.exception.KeysetKeyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Central manager for Tink keyset lifecycle: load/create, provide primitives, and rotation.
 *
 * <p>Master key (AES-256-GCM) encrypts keysets at rest; HA SoT is {@code ezkey_keyset_blob}.
 *
 * @since 2025
 */
@Component
public class TinkKeyManager implements KeyManagementOperations {

  private static final Logger logger = LoggerFactory.getLogger(TinkKeyManager.class);
  private static final byte[] KEYSET_ASSOCIATED_DATA = new byte[0];

  private final TinkProperties properties;
  private final KeysetBlobRepository keysetBlobRepository;
  private final KeysetHaSyncSupport haSync;

  private volatile KeysetHandle keysetHandle;
  private volatile long keysetFileLastModified = 0;
  private volatile String keysetFilePath;
  private volatile Aead masterAead;
  private volatile long databaseKeysetVersion = 0;

  /** Test-only: force {@link #writeKeysetFileCacheBestEffort} to fail (inject, not chmod). */
  private volatile boolean forceKeysetFileCacheWriteFailureForTests;

  /** Guards against recursive DB checks from EncryptionEntityListener → getAeadPrimitive. */
  private static final ThreadLocal<Boolean> CHECKING_DATABASE =
      ThreadLocal.withInitial(() -> false);

  /** Throttles getAeadPrimitive database version checks. */
  private volatile long lastDatabaseCheckTime = 0;

  /** Minimum interval between database version checks in milliseconds. */
  private static final long DATABASE_CHECK_INTERVAL_MS = 5000;

  /**
   * Protects {@link #keysetHandle} reads (encrypt/decrypt hot path) vs exclusive reload/rotation.
   *
   * <p>SEC-009: replaces method-level {@code synchronized} on {@link #getAeadPrimitive()} so
   * concurrent encryption does not serialize on keyset version checks.
   */
  private final ReentrantReadWriteLock keysetLock = new ReentrantReadWriteLock();

  /** Single-thread executor for throttled keyset version checks outside the read lock. */
  private final ExecutorService keysetCheckExecutor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "tink-keyset-version-check");
            thread.setDaemon(true);
            return thread;
          });

  /** Prevents overlapping async keyset version checks. */
  private final AtomicBoolean keysetCheckScheduled = new AtomicBoolean(false);

  /**
   * Constructor with optional KeysetBlobRepository for database-backed keyset storage.
   *
   * <p>Uses Spring's ObjectProvider to handle optional KeysetBlobRepository gracefully. This allows
   * the manager to work in FILE mode even when the repository is not available.
   *
   * @param properties Tink configuration properties
   * @param keysetBlobRepositoryProvider optional provider for database keyset storage
   */
  public TinkKeyManager(
      TinkProperties properties,
      org.springframework.beans.factory.ObjectProvider<KeysetBlobRepository>
          keysetBlobRepositoryProvider) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.keysetBlobRepository = keysetBlobRepositoryProvider.getIfAvailable();
    this.haSync = new KeysetHaSyncSupport(this.properties, this.keysetBlobRepository);
    try {
      AeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to register Tink AeadConfig", e);
    }
  }

  /**
   * Initialize Tink encryption with master key and keyset.
   *
   * <p>This method is called automatically after dependency injection. It loads the master key,
   * creates the master AEAD, and loads or creates the keyset.
   *
   * <p>If encryption is disabled or master key is not configured, initialization is skipped and the
   * service will operate without encryption (backward compatible mode).
   */
  @PostConstruct
  public void initialize() {
    if (!properties.isEnabled()) {
      if (properties.isRequired()) {
        throw new IllegalStateException(
            "Tink encryption is required (ezkey.encryption.required=true) but"
                + " ezkey.encryption.enabled=false");
      }
      logger.info("Tink encryption is disabled via configuration");
      return;
    }

    try {
      logger.info("Initializing Tink encryption...");

      byte[] masterKey = loadMasterKeyFromFile();
      logger.info("Master key loaded from file");

      this.masterAead = createMasterAead(masterKey);
      logger.info("Master AEAD created");

      String keysetPath = properties.getKeysetFile();
      StorageMode storageMode = properties.getKeyset().getStorageMode();
      logger.info("Keyset storage mode: {}", storageMode);

      boolean writer = properties.getKeyset().isWriter();
      boolean databaseBacked =
          (storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
              && keysetBlobRepository != null;

      boolean loadedFromDatabase = false;
      KeysetHaSyncSupport.StartupBlobOutcome startupOutcome =
          KeysetHaSyncSupport.StartupBlobOutcome.ABSENT;
      if (databaseBacked) {
        startupOutcome =
            haSync.loadExistingBlobAtStartup(
                writer,
                keysetBlobRepository::keysetExists,
                this::loadKeysetFromDatabaseTyped,
                properties.getKeyset().isAllowLegacyEnvelopeReplace(),
                () -> writeKeysetFileCacheBestEffort(keysetHandle),
                this::awaitDatabaseKeyset);
        loadedFromDatabase = startupOutcome == KeysetHaSyncSupport.StartupBlobOutcome.LOADED;
      }

      if (!loadedFromDatabase) {
        if (!writer) {
          if (storageMode == StorageMode.FILE) {
            loadedFromDatabase = loadExistingKeysetFileOnly(keysetPath);
          } else {
            logger.warn(
                "Timed out waiting for keyset blob; this process is not the keyset writer and"
                    + " will not generate or upsert a keyset");
          }
        } else if (startupOutcome == KeysetHaSyncSupport.StartupBlobOutcome.FAILED_CLOSED) {
          logger.error(
              "Encryption not initialized: database unreachable or blob unloadable at startup"
                  + " (fail closed; local file was not synced to ezkey_keyset_blob)");
        } else if (startupOutcome
            == KeysetHaSyncSupport.StartupBlobOutcome.LEGACY_REPLACE_ELIGIBLE) {
          replaceLegacyEnvelopeWithBackup(keysetPath);
        } else {
          materializeWriterKeyset(keysetPath, storageMode);
        }
      }

      if (isInitialized()) {
        verifyKeyset();
        haSync.startPeriodicVersionTickIfDatabaseBacked(
            databaseBacked, CHECKING_DATABASE, this::checkAndReloadKeysetIfNeeded);
        logger.info("Tink encryption ready for operation");
      }
    } catch (FileNotFoundException e) {
      logger.warn(
          "Master key file not found. Encryption will be disabled. "
              + "To enable encryption, run: sudo ./scripts/generate-master-key.sh (Linux/Mac) "
              + "or .\\scripts\\generate-master-key.ps1 (Windows as Administrator)",
          e);
    } catch (GeneralSecurityException
        | IOException
        | IllegalArgumentException
        | SecurityException e) {
      logger.error(
          "Failed to initialize Tink encryption. Encryption will be disabled. "
              + "Application will continue without encryption at rest.",
          e);
    }

    enforceRequiredEncryption();
  }

  /** Polls until Admin materializes the blob or bootstrap-wait elapses. */
  private boolean awaitDatabaseKeyset() {
    Duration wait = properties.getKeyset().getBootstrapWait();
    Duration interval = properties.getKeyset().getBootstrapPollInterval();
    if (wait.isNegative() || wait.isZero()) {
      logger.warn("Timed out waiting for keyset blob (bootstrap-wait is zero)");
      return false;
    }
    Instant deadline = Instant.now().plus(wait);
    logger.info(
        "Waiting up to {} for Admin to materialize ezkey_keyset_blob (poll {})", wait, interval);
    while (Instant.now().isBefore(deadline)) {
      long sleepMs = Math.max(0L, interval.toMillis());
      try {
        Thread.sleep(sleepMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Interrupted while waiting for keyset blob");
        return false;
      }
      try {
        if (loadKeysetFromDatabase()) {
          logger.info("✅ Keyset blob became available during bootstrap wait");
          return true;
        }
      } catch (DataAccessException | IllegalStateException e) {
        logger.debug("Keyset blob still unavailable: {}", e.getMessage());
      }
    }
    logger.warn("Timed out waiting for keyset blob after {}", wait);
    return false;
  }

  /** FILE-mode peripheral: load existing keyset file, never generate. */
  private boolean loadExistingKeysetFileOnly(String keysetPath) {
    if (keysetPath == null || keysetPath.isBlank()) {
      logger.warn("ezkey.encryption.keyset-file is not configured. Encryption disabled.");
      return false;
    }
    String normalizedKeysetPath = normalizePath(keysetPath);
    File keysetFile = new File(normalizedKeysetPath);
    if (!keysetFile.exists()) {
      logger.warn(
          "Keyset file not found at {}. This process is not the keyset writer and will not"
              + " generate a new keyset.",
          normalizedKeysetPath);
      return false;
    }
    try {
      this.keysetHandle = loadEncryptedKeyset(normalizedKeysetPath);
      this.keysetFilePath = normalizedKeysetPath;
      this.keysetFileLastModified = keysetFile.lastModified();
      logger.info("✅ Keyset loaded from file (non-writer FILE mode): {}", normalizedKeysetPath);
      return true;
    } catch (GeneralSecurityException | IOException e) {
      logger.error("Failed to load existing keyset file: {}", e.getMessage());
      return false;
    }
  }

  /** Writer empty-blob materialize; never CAS-overwrite an existing row from a file (B2/B3). */
  private void materializeWriterKeyset(String keysetPath, StorageMode storageMode)
      throws GeneralSecurityException, IOException {
    if (keysetPath == null || keysetPath.isBlank()) {
      logger.warn("ezkey.encryption.keyset-file is not configured. Encryption disabled.");
      return;
    }

    String normalizedKeysetPath = normalizePath(keysetPath);
    File keysetFile = new File(normalizedKeysetPath);
    boolean databaseBacked =
        (storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
            && keysetBlobRepository != null;

    logger.debug(
        "Keyset file check - Original path: {}, Normalized path: {}, Exists: {}",
        keysetPath,
        normalizedKeysetPath,
        keysetFile.exists());

    // Belt-and-suspenders: if a peer row appeared and is loadable, adopt it.
    if (databaseBacked) {
      KeysetLoadResult probe = loadKeysetFromDatabaseTyped();
      if (probe == KeysetLoadResult.LOADED) {
        writeKeysetFileCacheBestEffort(keysetHandle);
        logger.info(
            "✅ Keyset adopted from database before materialize (existing blob takes precedence"
                + " over file)");
        return;
      }
      if (probe == KeysetLoadResult.FAILED_CLOSED
          || probe == KeysetLoadResult.LEGACY_ENVELOPE
          || keysetBlobRepository.keysetExists()) {
        // B3: row present but not loadable here — never generate+CAS overwrite.
        logger.error(
            "Refusing materialize overwrite (loadResult={}, exists={}); fail closed",
            probe,
            keysetBlobRepository.keysetExists());
        return;
      }
    }

    if (keysetFile.exists()) {
      logger.info("Loading existing keyset from: {}", normalizedKeysetPath);
      this.keysetHandle = loadEncryptedKeyset(normalizedKeysetPath);
      this.keysetFilePath = normalizedKeysetPath;
      this.keysetFileLastModified = keysetFile.lastModified();
      logger.info("✅ Keyset loaded successfully from: {}", normalizedKeysetPath);

      if (databaseBacked) {
        try {
          // Claim only when absent — never CAS-update an existing blob from the file.
          claimOrAdoptGeneratedKeyset(normalizedKeysetPath);
        } catch (KeysetConcurrentModificationException e) {
          logger.info(
              "Startup file claim lost CAS (peer writer ahead); adopting database blob: {}",
              e.getMessage());
          if (loadKeysetFromDatabase()) {
            writeKeysetFileCacheBestEffort(keysetHandle);
          }
        } catch (GeneralSecurityException
            | IOException
            | DataAccessException
            | IllegalStateException e) {
          logger.warn("Failed to claim keyset into database from file: {}", e.getMessage());
        }
      }
      return;
    }

    logger.warn(
        "Keyset file not found at: {}. Generating new keyset. "
            + "NOTE: If this is not the first startup, check that the keyset file exists "
            + "and is accessible. All APIs must use the same keyset file.",
        normalizedKeysetPath);
    this.keysetHandle = generateAndSaveKeyset(normalizedKeysetPath);
    this.keysetFilePath = normalizedKeysetPath;
    this.keysetFileLastModified = keysetFile.lastModified();
    logger.info("🆕 New keyset created and saved to: {}", normalizedKeysetPath);

    if (databaseBacked) {
      try {
        claimOrAdoptGeneratedKeyset(normalizedKeysetPath);
      } catch (KeysetConcurrentModificationException e) {
        logger.info(
            "Startup new-keyset CAS lost (peer writer ahead); adopting database blob: {}",
            e.getMessage());
        if (loadKeysetFromDatabase()) {
          writeKeysetFileCacheBestEffort(keysetHandle);
        }
      } catch (GeneralSecurityException
          | IOException
          | DataAccessException
          | IllegalStateException e) {
        logger.warn("Failed to save new keyset to database: {}", e.getMessage());
      }
    }
  }

  /** Claims via insert or adopts peer; race loser that cannot load fails closed (B3). */
  private void claimOrAdoptGeneratedKeyset(String normalizedKeysetPath)
      throws GeneralSecurityException, IOException {
    String encryptedKeysetJson =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            keysetHandle, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
    byte[] encryptedData = encryptedKeysetJson.getBytes(StandardCharsets.UTF_8);
    int claimed =
        keysetBlobRepository.insertSingletonIfAbsent(
            encryptedData, OffsetDateTime.now(), "STARTUP_NEW_KEYSET");
    if (claimed > 0) {
      var version = findBlobVersionInstrumented();
      this.databaseKeysetVersion = version.orElse(1L);
      logger.info("✅ New keyset claimed in database (version: {})", databaseKeysetVersion);
      return;
    }
    KeysetLoadResult peerLoad = loadKeysetFromDatabaseTyped();
    if (peerLoad == KeysetLoadResult.LOADED) {
      writeKeysetFileCacheBestEffort(keysetHandle);
      logger.info("✅ Adopted peer keyset blob after losing first-boot claim");
      return;
    }
    if (peerLoad == KeysetLoadResult.LEGACY_ENVELOPE
        && properties.getKeyset().isAllowLegacyEnvelopeReplace()) {
      replaceLegacyEnvelopeWithBackup(normalizedKeysetPath);
      return;
    }
    throw new IllegalStateException(
        "First-boot claim lost and peer blob cannot be loaded (result="
            + peerLoad
            + "); fail closed — refusing overwrite");
  }

  /** ADR-0011 opt-in: backup old row, generate Tink-native envelope, CAS-replace. */
  private void replaceLegacyEnvelopeWithBackup(String keysetPath)
      throws GeneralSecurityException, IOException {
    String normalized = normalizePath(keysetPath);
    KeysetBlob old =
        keysetBlobRepository
            .findKeyset()
            .orElseThrow(
                () -> new IllegalStateException("Legacy replace requested but blob row is absent"));
    Path backupPath = Path.of(normalized + ".legacy-backup");
    Files.write(backupPath, old.getKeysetData());
    logger.warn("Wrote ADR-0011 legacy blob backup to {}", backupPath);
    this.keysetHandle = generateAndSaveKeyset(normalized);
    this.keysetFilePath = normalized;
    this.keysetFileLastModified = new File(normalized).lastModified();
    this.databaseKeysetVersion = old.getVersion() != null ? old.getVersion() : 1L;
    saveKeysetToDatabase("STARTUP_LEGACY_REPLACE");
    logger.info("✅ Replaced positively identified legacy envelope (backup at {})", backupPath);
  }

  /** Shuts down the async keyset version check executor on application stop. */
  @PreDestroy
  void shutdownKeysetCheckExecutor() {
    keysetCheckExecutor.shutdown();
    haSync.shutdown();
    try {
      if (!keysetCheckExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        keysetCheckExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      keysetCheckExecutor.shutdownNow();
      haSync.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Reads the blob version and increments the SEC-009 / test instrumentation counter.
   *
   * @return optional blob version
   */
  private Optional<Long> findBlobVersionInstrumented() {
    return haSync.findBlobVersionInstrumented();
  }

  /**
   * Resets the database version check throttle so the next {@link #getAeadPrimitive()} schedules a
   * check (tests only).
   */
  void resetDatabaseCheckThrottleForTests() {
    lastDatabaseCheckTime = 0;
  }

  /**
   * Forces the next keyset file-cache write to fail (tests only; inject, do not chmod — N1).
   *
   * @param force {@code true} to inject {@link IOException} in file-cache write
   */
  void forceKeysetFileCacheWriteFailureForTests(boolean force) {
    this.forceKeysetFileCacheWriteFailureForTests = force;
  }

  /**
   * Runs the keyset version check synchronously on the calling thread (tests only).
   *
   * <p>Bypasses the async executor so reload behavior can be asserted deterministically.
   */
  void runKeysetVersionCheckSynchronouslyForTests() {
    CHECKING_DATABASE.set(true);
    try {
      checkAndReloadKeysetIfNeeded();
    } finally {
      CHECKING_DATABASE.set(false);
    }
  }

  /**
   * Sets the cached database keyset version (tests only).
   *
   * @param version optimistic-lock version to simulate stale in-memory cache
   */
  void setDatabaseKeysetVersionForTests(long version) {
    databaseKeysetVersion = version;
  }

  /**
   * Waits until any already-scheduled async keyset version check completes (tests only).
   *
   * @param timeout maximum wait
   * @throws InterruptedException if interrupted while waiting
   */
  void awaitPendingKeysetVersionCheckForTests(java.time.Duration timeout)
      throws InterruptedException {
    try {
      keysetCheckExecutor.submit(() -> {}).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IllegalStateException("Keyset version check drain failed", e);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new IllegalStateException("Timed out waiting for keyset version check drain", e);
    }
  }

  /**
   * Fails startup when encryption is marked required but Tink did not initialize (SEC-002).
   *
   * @throws IllegalStateException when {@code ezkey.encryption.required=true} and encryption is not
   *     ready
   */
  void enforceRequiredEncryption() {
    if (!properties.isRequired()) {
      return;
    }
    if (!properties.isEnabled()) {
      throw new IllegalStateException(
          "Tink encryption is required (ezkey.encryption.required=true) but"
              + " ezkey.encryption.enabled=false");
    }
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption is required (ezkey.encryption.required=true) but initialization failed."
              + " Verify master key file, keyset configuration, and startup logs.");
    }
  }

  public boolean isInitialized() {
    return keysetHandle != null && masterAead != null;
  }

  public KeysetHandle getKeysetHandle() {
    keysetLock.readLock().lock();
    try {
      if (keysetHandle == null) {
        throw new IllegalStateException(
            "TinkKeyManager not initialized. "
                + "Check that master key file exists and encryption is enabled in configuration.");
      }
      return keysetHandle;
    } finally {
      keysetLock.readLock().unlock();
    }
  }

  public Aead getAeadPrimitive() {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. "
              + "Check that master key file exists and encryption is enabled.");
    }

    scheduleKeysetVersionCheckIfDue();

    boolean writeLockHeldByCurrentThread = keysetLock.writeLock().getHoldCount() > 0;
    if (!writeLockHeldByCurrentThread) {
      keysetLock.readLock().lock();
    }
    try {
      return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to obtain AEAD primitive from keyset", e);
    } finally {
      if (!writeLockHeldByCurrentThread) {
        keysetLock.readLock().unlock();
      }
    }
  }

  /**
   * Schedules a throttled keyset version check on a background thread (SEC-009).
   *
   * <p>Version checks and reloads run outside the read lock so encrypt/decrypt hot paths stay
   * concurrent. Reload acquires the write lock only when a newer keyset is detected.
   */
  private void scheduleKeysetVersionCheckIfDue() {
    if (CHECKING_DATABASE.get()) {
      return;
    }

    long now = System.currentTimeMillis();
    if ((now - lastDatabaseCheckTime) < DATABASE_CHECK_INTERVAL_MS) {
      return;
    }
    if (!keysetCheckScheduled.compareAndSet(false, true)) {
      return;
    }

    lastDatabaseCheckTime = now;
    keysetCheckExecutor.execute(
        () -> {
          try {
            CHECKING_DATABASE.set(true);
            checkAndReloadKeysetIfNeeded();
          } finally {
            CHECKING_DATABASE.set(false);
            keysetCheckScheduled.set(false);
          }
        });
  }

  private void checkAndReloadKeysetIfNeeded() {
    StorageMode storageMode = properties.getKeyset().getStorageMode();

    boolean shouldReloadFromDatabase = false;
    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        var dbVersion = findBlobVersionInstrumented();
        if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
          shouldReloadFromDatabase = true;
        }
      } catch (DataAccessException | IllegalStateException e) {
        logger.warn(
            "Failed to check keyset version in database. Using cached keyset. Error: {}",
            e.getMessage());
        logger.debug("Database keyset version check error", e);
      }
    }

    boolean shouldReloadFromFile = false;
    if ((storageMode == StorageMode.FILE || storageMode == StorageMode.HYBRID)
        && keysetFilePath != null) {
      try {
        File keysetFile = new File(keysetFilePath);
        if (keysetFile.exists() && keysetFile.lastModified() > keysetFileLastModified) {
          shouldReloadFromFile = true;
        }
      } catch (SecurityException e) {
        logger.warn(
            "Failed to check keyset file modification time. Using cached keyset. Error: {}",
            e.getMessage());
        logger.debug("Keyset file version check error", e);
      }
    }

    if (!shouldReloadFromDatabase && !shouldReloadFromFile) {
      return;
    }

    keysetLock.writeLock().lock();
    try {
      if (shouldReloadFromDatabase && keysetBlobRepository != null) {
        try {
          var dbVersion = findBlobVersionInstrumented();
          if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
            logger.info(
                "🔄 Database keyset updated (version: {} > {}), reloading keyset...",
                dbVersion.get(),
                databaseKeysetVersion);
            if (loadKeysetFromDatabaseUnlocked() == KeysetLoadResult.LOADED) {
              logger.info("✅ Keyset reloaded from database successfully");
            }
          }
        } catch (DataAccessException | IllegalStateException e) {
          logger.warn(
              "Failed to reload keyset from database. Using cached keyset. Error: {}",
              e.getMessage());
          logger.debug("Database keyset reload error", e);
        }
      }

      if (shouldReloadFromFile && keysetFilePath != null) {
        try {
          File keysetFile = new File(keysetFilePath);
          if (keysetFile.exists()) {
            long currentLastModified = keysetFile.lastModified();
            if (currentLastModified > keysetFileLastModified) {
              logger.info(
                  "🔄 Keyset file modified (last modified: {} > {}), reloading keyset...",
                  currentLastModified,
                  keysetFileLastModified);
              this.keysetHandle = loadEncryptedKeyset(keysetFilePath);
              this.keysetFileLastModified = currentLastModified;
              logger.info("✅ Keyset reloaded from file successfully");
            }
          }
        } catch (GeneralSecurityException
            | IOException
            | IllegalStateException
            | SecurityException e) {
          logger.warn(
              "Failed to reload keyset file. Using cached keyset. Error: {}", e.getMessage());
          logger.debug("Keyset reload error", e);
        }
      }
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  private static long toUnsignedLong(int signedKeyId) {
    return Integer.toUnsignedLong(signedKeyId);
  }

  private static long toUnsignedLong(long signedKeyId) {
    if (signedKeyId >= 0) {
      return signedKeyId;
    }
    if (signedKeyId >= Integer.MIN_VALUE && signedKeyId <= Integer.MAX_VALUE) {
      return Integer.toUnsignedLong((int) signedKeyId);
    }
    throw new IllegalArgumentException(
        "Key ID value out of range for database storage: "
            + signedKeyId
            + " (unsigned: "
            + Long.toUnsignedString(signedKeyId)
            + "). "
            + "Tink should not generate such values. If this occurs, the key ID may be too large "
            + "for PostgreSQL BIGINT with CHECK >= 0 constraint.");
  }

  private static long getUnsignedPrimaryKeyId(KeysetHandle handle) {
    return toUnsignedLong(handle.getPrimary().getId());
  }

  private static Set<Long> collectUnsignedKeyIds(KeysetHandle handle) {
    java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
    for (int i = 0; i < handle.size(); i++) {
      ids.add(toUnsignedLong(handle.getAt(i).getId()));
    }
    return ids;
  }

  private static int findNewSignedKeyIdOrThrow(KeysetHandle handle, Set<Long> existingKeyIds)
      throws GeneralSecurityException {
    for (int i = 0; i < handle.size(); i++) {
      int signedKeyId = handle.getAt(i).getId();
      long unsignedKeyId = toUnsignedLong(signedKeyId);
      if (!existingKeyIds.contains(unsignedKeyId)) {
        return signedKeyId;
      }
    }
    throw new GeneralSecurityException("Failed to find new key after keyset update");
  }

  private static boolean containsSignedKeyId(KeysetHandle handle, int signedKeyId) {
    for (int i = 0; i < handle.size(); i++) {
      if (handle.getAt(i).getId() == signedKeyId) {
        return true;
      }
    }
    return false;
  }

  public long getCurrentPrimaryKeyId() {
    keysetLock.readLock().lock();
    try {
      if (!isInitialized()) {
        throw new IllegalStateException(
            "Tink encryption not initialized. "
                + "Check that master key file exists and encryption is enabled.");
      }
      long signedKeyId = keysetHandle.getPrimary().getId();
      return toUnsignedLong(signedKeyId);
    } finally {
      keysetLock.readLock().unlock();
    }
  }

  private void verifyKeyset() throws GeneralSecurityException {
    Aead aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    String testData = "verification-test";
    byte[] ciphertext = aead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);
    byte[] plaintext = aead.decrypt(ciphertext, null);

    if (!testData.equals(new String(plaintext, StandardCharsets.UTF_8))) {
      throw new GeneralSecurityException("❌ Keyset verification failed");
    }

    logger.info("✅ Keyset verified operational");
  }

  private byte[] loadMasterKeyFromFile() throws IOException {
    String filePath = properties.getMasterKeyFile();

    if (filePath == null || filePath.isBlank()) {
      throw new IllegalStateException(
          "❌ Master key file path not configured. "
              + "Set ezkey.encryption.master-key-file in application.properties");
    }

    String normalizedPath = normalizePath(filePath);
    Path path = Path.of(normalizedPath);

    if (!Files.exists(path)) {
      throw new FileNotFoundException(
          "❌ Master key file not found: "
              + normalizedPath
              + " (original path: "
              + filePath
              + ")\n"
              + "Run: sudo ./scripts/generate-master-key.sh (Linux/Mac) or "
              + ".\\scripts\\generate-master-key.ps1 (Windows as Administrator)");
    }

    if (!isWindows()) {
      verifyFilePermissions(path);
    }

    String masterKeyBase64 = Files.readString(path, StandardCharsets.UTF_8).trim();

    if (masterKeyBase64.isEmpty()) {
      throw new IllegalStateException("❌ Master key file is empty: " + normalizedPath);
    }

    logger.info("🔑 Master key loaded from: {}", normalizedPath);
    return Base64.getDecoder().decode(masterKeyBase64);
  }

  private void verifyFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Files.getPosixFilePermissions(path);

    if (!permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ)) {
      throw new SecurityException("❌ Master key file must be readable by owner");
    }

    Set<java.nio.file.attribute.PosixFilePermission> forbidden =
        Set.of(
            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
            java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
            java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);

    for (java.nio.file.attribute.PosixFilePermission perm : forbidden) {
      if (permissions.contains(perm)) {
        throw new SecurityException(
            "❌ Master key file has insecure permissions: "
                + java.nio.file.attribute.PosixFilePermissions.toString(permissions)
                + "\n"
                + "Run: sudo chmod 600 "
                + path);
      }
    }

    logger.info(
        "🔒 Master key file permissions verified: {}",
        java.nio.file.attribute.PosixFilePermissions.toString(permissions));
  }

  private Aead createMasterAead(byte[] masterKey) throws GeneralSecurityException {
    if (masterKey.length != 32) {
      throw new IllegalArgumentException(
          "Master key must be exactly 32 bytes (256 bits), got: " + masterKey.length);
    }
    return new AesGcmJce(masterKey);
  }

  private KeysetHandle loadEncryptedKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {
    String normalizedPath = normalizePath(keysetPath);
    logger.info("📂 Loading encrypted keyset from: {}", normalizedPath);

    File keysetFile = new File(normalizedPath);
    if (!keysetFile.exists()) {
      throw new FileNotFoundException("Keyset file not found: " + normalizedPath);
    }
    if (keysetFile.length() == 0) {
      throw new IllegalStateException("Keyset file is empty: " + normalizedPath);
    }

    try {
      String encryptedKeysetJson = Files.readString(keysetFile.toPath(), StandardCharsets.UTF_8);
      KeysetHandle handle =
          TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
              encryptedKeysetJson, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());

      long signedPrimaryKeyId = handle.getPrimary().getId();
      long unsignedPrimaryKeyId = toUnsignedLong(signedPrimaryKeyId);
      logger.info(
          "✅ Keyset decrypted successfully. Primary key ID: {} (signed: {})",
          unsignedPrimaryKeyId,
          signedPrimaryKeyId);
      return handle;
    } catch (GeneralSecurityException e) {
      logger.error(
          "❌ Failed to decrypt keyset from: {}. "
              + "This usually means the master key is incorrect or the keyset was encrypted "
              + "with a different master key. Ensure all APIs use the same master key file.",
          normalizedPath);
      throw e;
    }
  }

  private KeysetHandle generateAndSaveKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {
    logger.info("🆕 Generating new keyset...");
    String normalizedPath = normalizePath(keysetPath);
    var template = getKeyTemplate();
    KeysetHandle newKeyset = KeysetHandle.generateNew(template);

    File keysetFile = new File(normalizedPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    String encryptedKeysetJson =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            newKeyset, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
    Files.writeString(keysetFile.toPath(), encryptedKeysetJson, StandardCharsets.UTF_8);

    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ New keyset generated and encrypted");
    return newKeyset;
  }

  private void setSecureFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(path, permissions);
    logger.info("🔒 Set file permissions to 600: {}", path);
  }

  private com.google.crypto.tink.KeyTemplate getKeyTemplate() throws GeneralSecurityException {
    String algorithm = properties.getAlgorithm();
    return switch (algorithm) {
      case "CHACHA20_POLY1305" -> KeyTemplates.get("CHACHA20_POLY1305");
      case "AES256_GCM" -> KeyTemplates.get("AES256_GCM");
      default -> {
        logger.warn("Unknown algorithm: {}, using AES256_GCM", algorithm);
        try {
          yield KeyTemplates.get("AES256_GCM");
        } catch (GeneralSecurityException e) {
          throw new IllegalStateException("Failed to get AES256_GCM template", e);
        }
      }
    };
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }

    if (isWindows() && path.startsWith("/") && path.length() >= 3) {
      char firstChar = path.charAt(1);
      char secondChar = path.length() > 2 ? path.charAt(2) : 0;

      if (Character.isLetter(firstChar) && secondChar == '/') {
        char driveLetter = Character.toUpperCase(firstChar);
        String restOfPath = path.substring(3);
        String windowsPath = restOfPath.replace('/', '\\');
        return driveLetter + ":\\" + windowsPath;
      }
    }

    return path;
  }

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  public long rotateKey() throws GeneralSecurityException, IOException {
    keysetLock.writeLock().lock();
    try {
      return rotateKeyUnderWriteLock();
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  private long rotateKeyUnderWriteLock() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot rotate keys without initialized keyset.");
    }

    reloadKeysetBeforeMutationUnlocked();

    logger.info("🔄 Starting key rotation...");
    long signedOldPrimaryKeyId = keysetHandle.getPrimary().getId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    var existingKeyIds = collectUnsignedKeyIds(keysetHandle);
    logger.debug("Existing key IDs before rotation: {}", existingKeyIds);

    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetManager rotatedManager = manager.add(template);

    int signedNewKeyIdInt =
        findNewSignedKeyIdOrThrow(rotatedManager.getKeysetHandle(), existingKeyIds);

    KeysetHandle rotatedHandle = rotatedManager.setPrimary(signedNewKeyIdInt).getKeysetHandle();
    long signedNewPrimaryKeyId = rotatedHandle.getPrimary().getId();
    long newPrimaryKeyId = toUnsignedLong(signedNewPrimaryKeyId);
    logger.info(
        "New primary key ID: {} (unsigned: {})",
        signedNewPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));

    // H3: CAS (or FILE commit) before mutating this.keysetHandle / file.
    commitMutatedKeysetUnlocked(rotatedHandle, "KEY_ROTATION");

    logger.info(
        "✅ Key rotation completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned),
        newPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));
    return newPrimaryKeyId;
  }

  public long addKeyWithoutPromotion() throws GeneralSecurityException, IOException {
    keysetLock.writeLock().lock();
    try {
      return addKeyWithoutPromotionUnderWriteLock();
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  private long addKeyWithoutPromotionUnderWriteLock() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot add keys without initialized keyset.");
    }

    reloadKeysetBeforeMutationUnlocked();

    logger.info("🔄 Adding new key to keyset (without promotion)...");
    long signedCurrentPrimaryKeyId = keysetHandle.getPrimary().getId();
    long currentPrimaryKeyIdUnsigned = toUnsignedLong(signedCurrentPrimaryKeyId);
    logger.info(
        "Current primary key ID (will remain primary): {} (unsigned: {})",
        signedCurrentPrimaryKeyId,
        Long.toUnsignedString(currentPrimaryKeyIdUnsigned));

    var existingKeyIds = collectUnsignedKeyIds(keysetHandle);
    logger.debug("Existing key IDs before adding: {}", existingKeyIds);

    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetManager updatedManager = manager.add(template);

    int signedNewKeyId =
        findNewSignedKeyIdOrThrow(updatedManager.getKeysetHandle(), existingKeyIds);
    long newKeyId = toUnsignedLong(signedNewKeyId);

    KeysetHandle updatedHandle = updatedManager.getKeysetHandle();
    // H3: CAS (or FILE commit) before mutating this.keysetHandle / file.
    commitMutatedKeysetUnlocked(updatedHandle, "KEY_ADDED_PENDING");

    logger.info(
        "✅ New key added (NOT promoted). New key ID: {} (unsigned: {}). "
            + "Current primary remains: {} (unsigned: {})",
        toSignedLong(newKeyId),
        Long.toUnsignedString(newKeyId),
        signedCurrentPrimaryKeyId,
        Long.toUnsignedString(currentPrimaryKeyIdUnsigned));

    return newKeyId;
  }

  public long promoteToPrimary(long keyId) throws GeneralSecurityException, IOException {
    keysetLock.writeLock().lock();
    try {
      return promoteToPrimaryUnderWriteLock(keyId);
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  private long promoteToPrimaryUnderWriteLock(long keyId)
      throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot promote keys without initialized keyset.");
    }

    reloadKeysetBeforeMutationUnlocked();

    logger.info(
        "🔄 Promoting key {} (unsigned: {}) to PRIMARY...",
        toSignedLong(keyId),
        Long.toUnsignedString(keyId));

    long signedOldPrimaryKeyId = keysetHandle.getPrimary().getId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    int signedKeyIdInt = toSignedInt(keyId);
    boolean keyExists = containsSignedKeyId(keysetHandle, signedKeyIdInt);

    if (!keyExists) {
      boolean blobCurrent = isDatabaseBlobCurrentUnlocked();
      throw new KeysetKeyNotFoundException(
          keyId,
          blobCurrent,
          "Key %s (unsigned: %s) not found in keyset after versioned reload. Cannot promote."
              .formatted(signedKeyIdInt, Long.toUnsignedString(keyId)));
    }

    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetHandle promotedHandle = manager.setPrimary(signedKeyIdInt).getKeysetHandle();
    long newPrimaryKeyId = getUnsignedPrimaryKeyId(promotedHandle);
    if (newPrimaryKeyId != keyId) {
      throw new GeneralSecurityException(
          "Key promotion failed. Expected primary: %s, actual: %s"
              .formatted(Long.toUnsignedString(keyId), Long.toUnsignedString(newPrimaryKeyId)));
    }

    // H3: CAS (or FILE commit) before mutating this.keysetHandle / file.
    commitMutatedKeysetUnlocked(promotedHandle, "KEY_PROMOTED_PRIMARY");

    logger.info(
        "✅ Key promotion completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned),
        toSignedLong(keyId),
        Long.toUnsignedString(keyId));

    return oldPrimaryKeyIdUnsigned;
  }

  private int toSignedInt(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  private long toSignedLong(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  private void saveKeyset(KeysetHandle handle, String keysetPath)
      throws IOException, GeneralSecurityException {
    if (properties.getRotation().isBackupBeforeRotation()) {
      createBackup(keysetPath);
    }

    File keysetFile = new File(keysetPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    String encryptedKeysetJson =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
    Files.writeString(keysetFile.toPath(), encryptedKeysetJson, StandardCharsets.UTF_8);

    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ Keyset saved to: {}", keysetPath);
  }

  private void createBackup(String keysetPath) throws IOException {
    File keysetFile = new File(keysetPath);
    if (!keysetFile.exists()) {
      logger.warn("Keyset file does not exist, skipping backup: {}", keysetPath);
      return;
    }

    String timestamp =
        OffsetDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
    File parentDir = keysetFile.getParentFile();
    String backupFilename = "keyset-backup-" + timestamp + ".json.encrypted";
    File backupFile = new File(parentDir, backupFilename);

    Files.copy(
        keysetFile.toPath(),
        backupFile.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    if (!isWindows()) {
      setSecureFilePermissions(backupFile.toPath());
    }

    logger.info("✅ Keyset backup created: {}", backupFile.getAbsolutePath());
    cleanupOldBackups(parentDir);
  }

  private void cleanupOldBackups(File backupDir) {
    int retentionDays = properties.getRotation().getBackupRetentionDays();
    if (retentionDays <= 0) {
      return;
    }

    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
    File[] backupFiles =
        backupDir.listFiles(
            (_, name) -> name.startsWith("keyset-backup-") && name.endsWith(".json.encrypted"));

    if (backupFiles == null) {
      return;
    }

    int deletedCount = 0;
    for (File backup : backupFiles) {
      try {
        OffsetDateTime backupTime =
            java.time.Instant.ofEpochMilli(backup.lastModified())
                .atOffset(java.time.ZoneOffset.UTC);
        if (backupTime.isBefore(cutoffDate)) {
          if (backup.delete()) {
            deletedCount++;
            logger.debug("Deleted old backup: {}", backup.getName());
          }
        }
      } catch (IllegalArgumentException | SecurityException e) {
        logger.warn("Failed to delete old backup: {}", backup.getName(), e);
      }
    }

    if (deletedCount > 0) {
      logger.info(
          "Cleaned up {} old backup files (retention: {} days)", deletedCount, retentionDays);
    }
  }

  public List<Long> getAllKeyIds() {
    keysetLock.readLock().lock();
    try {
      if (!isInitialized()) {
        throw new IllegalStateException("Tink encryption not initialized");
      }
      return new java.util.ArrayList<>(collectUnsignedKeyIds(keysetHandle));
    } finally {
      keysetLock.readLock().unlock();
    }
  }

  public Aead getMasterAead() {
    return masterAead;
  }

  public boolean loadKeysetFromDatabase() {
    return loadKeysetFromDatabaseTyped() == KeysetLoadResult.LOADED;
  }

  /** Typed blob load under write lock (B3 — failures are never ABSENT). */
  KeysetLoadResult loadKeysetFromDatabaseTyped() {
    keysetLock.writeLock().lock();
    try {
      return loadKeysetFromDatabaseUnlocked();
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  private KeysetLoadResult loadKeysetFromDatabaseUnlocked() {
    if (keysetBlobRepository == null) {
      logger.debug("KeysetBlobRepository not available, cannot load from database");
      return KeysetLoadResult.ABSENT;
    }

    if (masterAead == null) {
      logger.warn("Master AEAD not initialized, cannot decrypt keyset from database");
      return KeysetLoadResult.FAILED_CLOSED;
    }

    try {
      var keysetBlobOpt = keysetBlobRepository.findKeyset();
      if (keysetBlobOpt.isEmpty()) {
        logger.debug("No keyset blob found in database");
        return KeysetLoadResult.ABSENT;
      }

      KeysetBlob keysetBlob = keysetBlobOpt.get();
      byte[] encryptedData = keysetBlob.getKeysetData();
      KeysetLoadResult classified =
          KeysetBlobEnvelopeClassifier.classify(encryptedData, masterAead);
      if (classified == KeysetLoadResult.LEGACY_ENVELOPE) {
        logger.warn("Positively identified ADR-0011 legacy outer-encrypted keyset blob");
        return KeysetLoadResult.LEGACY_ENVELOPE;
      }
      if (classified == KeysetLoadResult.FAILED_CLOSED) {
        return KeysetLoadResult.FAILED_CLOSED;
      }

      String encryptedKeysetJson = new String(encryptedData, StandardCharsets.UTF_8);
      this.keysetHandle =
          TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
              encryptedKeysetJson, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
      this.databaseKeysetVersion = keysetBlob.getVersion() != null ? keysetBlob.getVersion() : 0;

      long signedPrimaryKeyId = keysetHandle.getPrimary().getId();
      long unsignedPrimaryKeyId = toUnsignedLong(signedPrimaryKeyId);
      logger.info(
          "✅ Keyset loaded from database. Primary key ID: {} (signed: {}), version: {}",
          unsignedPrimaryKeyId,
          signedPrimaryKeyId,
          databaseKeysetVersion);
      return KeysetLoadResult.LOADED;
    } catch (DataAccessException e) {
      logger.error("Failed to load keyset from database (DB unreachable): {}", e.getMessage());
      logger.debug("Database keyset load error", e);
      return KeysetLoadResult.FAILED_CLOSED;
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      logger.error(
          "Failed to decrypt/parse keyset from database (fail closed): {}", e.getMessage());
      logger.debug("Database keyset decrypt/parse error", e);
      return KeysetLoadResult.FAILED_CLOSED;
    }
  }

  public void saveKeysetToDatabase(String updatedBy) throws GeneralSecurityException, IOException {
    if (!properties.getKeyset().isWriter()) {
      logger.warn(
          "Refusing keyset database save; this process is not the keyset writer (updatedBy={})",
          updatedBy);
      return;
    }
    if (keysetBlobRepository == null) {
      logger.debug("KeysetBlobRepository not available, cannot save to database");
      return;
    }

    keysetLock.readLock().lock();
    try {
      saveKeysetToDatabaseUnlocked(updatedBy);
    } finally {
      keysetLock.readLock().unlock();
    }
  }

  private void saveKeysetToDatabaseUnlocked(String updatedBy)
      throws GeneralSecurityException, IOException {
    saveKeysetHandleToDatabaseUnlocked(keysetHandle, updatedBy);
  }

  /** CAS-writes handle without installing memory first (H3). */
  private void saveKeysetHandleToDatabaseUnlocked(KeysetHandle handle, String updatedBy)
      throws GeneralSecurityException, IOException {
    if (handle == null) {
      throw new IllegalStateException("No keyset loaded, cannot save to database");
    }
    if (masterAead == null) {
      throw new IllegalStateException("Master AEAD not initialized, cannot encrypt keyset");
    }
    try {
      String encryptedKeysetJson =
          TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
              handle, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
      byte[] encryptedData = encryptedKeysetJson.getBytes(StandardCharsets.UTF_8);
      haSync.claimOrCasUpdateKeyset(
          encryptedData, updatedBy, () -> databaseKeysetVersion, v -> databaseKeysetVersion = v);
    } catch (KeysetConcurrentModificationException e) {
      logger.warn("Keyset CAS conflict: {}", e.getMessage());
      throw e;
    } catch (GeneralSecurityException | DataAccessException | IllegalStateException e) {
      logger.error("Failed to save keyset to database: {}", e.getMessage());
      throw e;
    }
  }

  /** CAS first (H3), install memory, best-effort file cache. */
  private void commitMutatedKeysetUnlocked(KeysetHandle newHandle, String updatedBy)
      throws GeneralSecurityException, IOException {
    StorageMode storageMode = properties.getKeyset().getStorageMode();
    boolean databaseBacked =
        (storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
            && keysetBlobRepository != null;
    if (databaseBacked) {
      saveKeysetHandleToDatabaseUnlocked(newHandle, updatedBy);
      logger.info("✅ Keyset saved to database ({})", updatedBy);
      // CAS fixed databaseKeysetVersion — install memory immediately so memory matches the blob
      // even if the local file cache write fails.
      this.keysetHandle = newHandle;
      writeKeysetFileCacheBestEffort(newHandle);
      return;
    }
    String normalizedPath = normalizePath(properties.getKeysetFile());
    saveKeyset(newHandle, normalizedPath);
    this.keysetHandle = newHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }
  }

  /** Best-effort file cache after successful CAS (ERROR log on failure; no rollback). */
  private void writeKeysetFileCacheBestEffort(KeysetHandle handle) {
    String normalizedPath = normalizePath(properties.getKeysetFile());
    try {
      if (forceKeysetFileCacheWriteFailureForTests) {
        throw new IOException("forced keyset file cache write failure for tests");
      }
      saveKeyset(handle, normalizedPath);
      File keysetFile = new File(normalizedPath);
      if (keysetFile.exists()) {
        this.keysetFileLastModified = keysetFile.lastModified();
        this.keysetFilePath = normalizedPath;
      }
    } catch (IOException | GeneralSecurityException e) {
      logger.error(
          "Keyset file cache write failed after successful DB CAS"
              + " (file is a best-effort cache; memory matches blob): {}",
          e.getMessage());
      logger.debug("Keyset file cache write error", e);
    }
  }

  /** Reload under write lock when DB blob is newer (Option A). */
  private void reloadKeysetBeforeMutationUnlocked() {
    haSync.reloadKeysetBeforeMutationIfNeeded(
        () -> databaseKeysetVersion,
        () -> loadKeysetFromDatabaseUnlocked() == KeysetLoadResult.LOADED);
  }

  private boolean isDatabaseBlobCurrentUnlocked() {
    return haSync.isDatabaseBlobCurrent(databaseKeysetVersion);
  }

  @Override
  public boolean reloadKeysetIfDatabaseNewer() {
    StorageMode storageMode = properties.getKeyset().getStorageMode();
    if ((storageMode != StorageMode.DATABASE && storageMode != StorageMode.HYBRID)
        || keysetBlobRepository == null) {
      return false;
    }
    keysetLock.writeLock().lock();
    try {
      Optional<Long> dbVersion = findBlobVersionInstrumented();
      if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
        return loadKeysetFromDatabaseUnlocked() == KeysetLoadResult.LOADED;
      }
      return false;
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  @Override
  public boolean tryReloadAfterDecryptFailure() {
    return haSync.tryReloadAfterDecryptFailure(this::loadKeysetFromDatabase);
  }

  public boolean forceReloadFromDatabase() {
    logger.info("🔄 Force reloading keyset from database...");
    return loadKeysetFromDatabase();
  }

  public boolean isDatabaseStorageAvailable() {
    return keysetBlobRepository != null;
  }

  public long getDatabaseKeysetVersion() {
    return databaseKeysetVersion;
  }

  /** Test / SEC-009: cumulative {@code findVersion} invocations. */
  long getFindVersionInvocationCountForTests() {
    return haSync.getFindVersionInvocationCount();
  }

  void resetFindVersionInvocationCountForTests() {
    haSync.resetFindVersionInvocationCount();
  }
}

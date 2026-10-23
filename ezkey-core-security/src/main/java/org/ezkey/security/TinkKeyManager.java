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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Central manager for Tink keyset lifecycle: load/create, provide primitives, and online rotation.
 *
 * <p><b>Blob-only authority (ADR-0016 / #952):</b> {@code ezkey_keyset_blob} is the sole runtime
 * source of truth. API processes neither read nor write a keyset file. The master key remains a
 * file/secret used to unwrap the Tink encrypted-keyset envelope.
 *
 * <p><b>CAS rotation protocol:</b> mutations stage a candidate handle, persist via Hibernate
 * {@code @Version} CAS, and install the live primary only after a successful save. CAS losers
 * discard the staging handle, reload the blob, and fail closed.
 *
 * <p><b>Security properties:</b>
 *
 * <ul>
 *   <li>Master key stored outside the application directory (POSIX 600 verified on Unix)
 *   <li>Keyset encrypted at rest in the database envelope
 *   <li>Logs may include key IDs (kids) and blob versions only — never raw key material
 * </ul>
 *
 * @since 2025
 */
@Component
public class TinkKeyManager implements KeyManagementOperations {

  private static final Logger logger = LoggerFactory.getLogger(TinkKeyManager.class);
  private static final byte[] KEYSET_ASSOCIATED_DATA = new byte[0];

  /**
   * Hard upper bound for database version-check ticks (milliseconds).
   *
   * <p>Design Q5 / ADR-0016: operators may configure a shorter interval via {@code
   * ezkey.encryption.keyset.version-check-interval}; values above five seconds are clamped.
   */
  private static final long DATABASE_CHECK_INTERVAL_MS = 5000;

  private final TinkProperties properties;
  private final KeysetBlobRepository keysetBlobRepository;

  private volatile KeysetHandle keysetHandle;
  private volatile Aead masterAead;
  private volatile long databaseKeysetVersion = 0;

  /**
   * ThreadLocal guard to prevent recursive database calls during keyset reload.
   *
   * <p>When getAeadPrimitive() checks the database for version changes, this can trigger JPA
   * operations that use EncryptionEntityListener, which calls getAeadPrimitive() again, causing a
   * StackOverflowError. This flag prevents that recursion.
   */
  private static final ThreadLocal<Boolean> CHECKING_DATABASE =
      ThreadLocal.withInitial(() -> false);

  /** Timestamp of last database version check to throttle frequency. */
  private volatile long lastDatabaseCheckTime = 0;

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
   * Set when a tick finds a newer blob that cannot be loaded (legacy/corrupt). Cache is kept;
   * health signals degraded so operators see the failure (ADR-0016 review — not silent).
   */
  private final AtomicBoolean keysetReloadDegraded = new AtomicBoolean(false);

  /**
   * Constructs the manager with optional {@link KeysetBlobRepository}.
   *
   * <p>When encryption is enabled, a repository is required for successful initialization. Missing
   * repository with {@code required=true} fails startup via {@link #enforceRequiredEncryption()}.
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
    try {
      AeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to register Tink AeadConfig", e);
    }
  }

  /**
   * Initialize Tink encryption with master key and database keyset blob.
   *
   * <p>Boot sequence (ADR-0016): load master → try load blob → non-writer {@link
   * #awaitDatabaseKeyset()} → writer {@link #materializeBlobOnly()}. Never generates a new keyset
   * after a {@link DataAccessException} on find (invariant 6). Never overwrites an unloadable /
   * legacy blob (invariant 8).
   *
   * <p>If encryption is disabled or master key is not configured, initialization is skipped and the
   * service will operate without encryption when {@code required=false}.
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
      logger.info("Initializing Tink encryption (blob-only / ADR-0016)...");

      if (keysetBlobRepository == null) {
        logger.error(
            "KeysetBlobRepository is required for enabled encryption (ezkey_keyset_blob is the"
                + " sole source of truth). Encryption will remain uninitialized.");
        enforceRequiredEncryption();
        return;
      }

      byte[] masterKey = loadMasterKeyFromFile();
      logger.info("Master key loaded from file");

      this.masterAead = createMasterAead(masterKey);
      logger.info("Master AEAD created");

      boolean writer = properties.getKeyset().isWriter();
      boolean loadedFromDatabase = false;
      boolean dbFindFailed = false;

      try {
        loadedFromDatabase = loadKeysetFromDatabase();
        if (loadedFromDatabase) {
          logger.info("✅ Keyset loaded from database");
        } else if (!writer) {
          loadedFromDatabase = awaitDatabaseKeyset();
        }
      } catch (DataAccessException e) {
        // Invariant 6: DB errors during find never trigger generation of a new keyset.
        dbFindFailed = true;
        logger.error(
            "DataAccessException while loading keyset blob; leaving encryption uninitialized"
                + " (will not generate a new keyset): {}",
            e.getMessage());
        logger.debug("Database keyset load error", e);
      }
      // IllegalStateException from unloadable/legacy blob propagates (invariant 8 — fail startup).

      if (!loadedFromDatabase && !dbFindFailed) {
        if (writer) {
          materializeBlobOnly();
        } else {
          logger.warn(
              "Timed out waiting for keyset blob; this process is not the keyset writer and"
                  + " will not generate or upsert a keyset");
        }
      }

      if (isInitialized()) {
        verifyKeyset();
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

  /**
   * Polls {@code ezkey_keyset_blob} until Admin materializes it or {@code bootstrap-wait} elapses.
   *
   * @return {@code true} if the keyset was loaded
   */
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
      } catch (DataAccessException e) {
        logger.debug("Keyset blob still unavailable (DB error): {}", e.getMessage());
      }
      // IllegalStateException (unloadable blob) propagates — fail closed.
    }
    logger.warn("Timed out waiting for keyset blob after {}", wait);
    return false;
  }

  /**
   * Writer first-boot: generate a keyset in memory and claim the singleton blob row.
   *
   * <p>On claim loss, adopts the peer blob. Never overwrites an unloadable/legacy row (invariant
   * 8).
   *
   * @throws GeneralSecurityException if keyset generation or peer adoption fails
   * @throws IOException if serialization fails
   */
  private void materializeBlobOnly() throws GeneralSecurityException, IOException {
    logger.info("🆕 Materializing new keyset into ezkey_keyset_blob (writer, blob-only)...");
    KeysetHandle generated = generateKeyset();
    String encryptedKeysetJson =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            generated, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
    byte[] encryptedData = encryptedKeysetJson.getBytes(StandardCharsets.UTF_8);
    int claimed =
        keysetBlobRepository.insertSingletonIfAbsent(
            encryptedData, OffsetDateTime.now(), "STARTUP_NEW_KEYSET");
    if (claimed > 0) {
      var version = keysetBlobRepository.findVersion();
      this.keysetHandle = generated;
      this.databaseKeysetVersion = version.orElse(1L);
      logger.info(
          "✅ New keyset claimed in database (primary kid: {}, version: {})",
          Long.toUnsignedString(getUnsignedPrimaryKeyId(generated)),
          databaseKeysetVersion);
      return;
    }
    if (loadKeysetFromDatabase()) {
      logger.info("✅ Adopted peer keyset blob after losing first-boot claim");
      return;
    }
    // Row absent after lost claim is unexpected; leave uninitialized rather than overwrite.
    throw new IllegalStateException(
        "Lost first-boot claim but peer keyset blob is still absent; refusing to overwrite");
  }

  /** Shuts down the async keyset version check executor on application stop. */
  @PreDestroy
  void shutdownKeysetCheckExecutor() {
    keysetCheckExecutor.shutdown();
    try {
      if (!keysetCheckExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        keysetCheckExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      keysetCheckExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Resets the database version check throttle so the next {@link #getAeadPrimitive()} schedules a
   * check (tests only).
   */
  void resetDatabaseCheckThrottleForTests() {
    lastDatabaseCheckTime = 0;
  }

  /**
   * Runs one keyset version check/reload synchronously (tests only).
   *
   * <p>Bypasses the async executor so tick fail-closed / cache-keep behaviour is deterministic.
   */
  void runKeysetVersionCheckForTests() {
    checkAndReloadKeysetIfNeeded();
  }

  /**
   * Whether the last tick found an unloadable newer blob while keeping the cached keyset.
   *
   * @return {@code true} when encryption health should report degraded reload state
   */
  public boolean isKeysetReloadDegraded() {
    return keysetReloadDegraded.get();
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
              + " Verify master key file, keyset blob, and startup logs.");
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
   * Effective database version-check interval in milliseconds (clamped to {@link
   * #DATABASE_CHECK_INTERVAL_MS}).
   *
   * @return tick interval used by {@link #scheduleKeysetVersionCheckIfDue()}
   */
  long databaseCheckIntervalMs() {
    long configured = properties.getKeyset().getVersionCheckInterval().toMillis();
    if (configured <= 0) {
      return DATABASE_CHECK_INTERVAL_MS;
    }
    return Math.min(configured, DATABASE_CHECK_INTERVAL_MS);
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
    if ((now - lastDatabaseCheckTime) < databaseCheckIntervalMs()) {
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
    if (keysetBlobRepository == null) {
      return;
    }

    boolean shouldReloadFromDatabase = false;
    try {
      var dbVersion = keysetBlobRepository.findVersion();
      if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
        shouldReloadFromDatabase = true;
      }
    } catch (DataAccessException | IllegalStateException e) {
      logger.warn(
          "Failed to check keyset version in database. Using cached keyset. Error: {}",
          e.getMessage());
      logger.debug("Database keyset version check error", e);
    }

    if (!shouldReloadFromDatabase) {
      return;
    }

    keysetLock.writeLock().lock();
    try {
      try {
        var dbVersion = keysetBlobRepository.findVersion();
        if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
          logger.info(
              "🔄 Database keyset updated (version: {} > {}), reloading keyset...",
              dbVersion.get(),
              databaseKeysetVersion);
          if (loadKeysetFromDatabaseUnlocked()) {
            keysetReloadDegraded.set(false);
            logger.info("✅ Keyset reloaded from database successfully");
          }
        }
      } catch (DataAccessException e) {
        // Keep serving the last blob-backed cache; do not clear the live handle (M11).
        logger.warn(
            "Failed to reload keyset from database. Using cached keyset. Error: {}",
            e.getMessage());
        logger.debug("Database keyset reload error", e);
      } catch (IllegalStateException e) {
        // Unloadable/legacy peer blob: keep cache, signal health (not silent fail-open).
        keysetReloadDegraded.set(true);
        logger.error(
            "Peer keyset blob is present but unloadable during version tick; keeping cached"
                + " keyset and marking encryption health degraded. Error: {}",
            e.getMessage());
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

  private KeysetHandle generateKeyset() throws GeneralSecurityException {
    logger.info("🆕 Generating new keyset...");
    var template = getKeyTemplate();
    KeysetHandle newKeyset = KeysetHandle.generateNew(template);
    logger.info(
        "✅ New keyset generated (primary kid: {})",
        Long.toUnsignedString(getUnsignedPrimaryKeyId(newKeyset)));
    return newKeyset;
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
    requireWriterForMutation("KEY_ROTATION");

    // Invariant 1: re-read blob under write lock before mutation.
    reloadBlobUnderWriteLockOrThrow("KEY_ROTATION");

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

    // Invariant 2: stage only — do not install live primary before CAS.
    KeysetHandle stagingHandle = rotatedManager.setPrimary(signedNewKeyIdInt).getKeysetHandle();
    long signedNewPrimaryKeyId = stagingHandle.getPrimary().getId();
    long newPrimaryKeyId = toUnsignedLong(signedNewPrimaryKeyId);
    logger.info(
        "Staging new primary key ID: {} (unsigned: {})",
        signedNewPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));

    casInstallStagingHandle(stagingHandle, "KEY_ROTATION");

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
    requireWriterForMutation("KEY_ADDED_PENDING");
    reloadBlobUnderWriteLockOrThrow("KEY_ADDED_PENDING");

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

    KeysetHandle stagingHandle = updatedManager.getKeysetHandle();
    casInstallStagingHandle(stagingHandle, "KEY_ADDED_PENDING");

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
    requireWriterForMutation("KEY_PROMOTED_PRIMARY");
    reloadBlobUnderWriteLockOrThrow("KEY_PROMOTED_PRIMARY");

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
      throw new GeneralSecurityException(
          "Key %s (unsigned: %s) not found in keyset. Cannot promote non-existent key."
              .formatted(signedKeyIdInt, Long.toUnsignedString(keyId)));
    }

    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetHandle stagingHandle = manager.setPrimary(signedKeyIdInt).getKeysetHandle();
    long newPrimaryKeyId = getUnsignedPrimaryKeyId(stagingHandle);
    if (newPrimaryKeyId != keyId) {
      throw new GeneralSecurityException(
          "Key promotion failed. Expected primary: %s, actual: %s"
              .formatted(Long.toUnsignedString(keyId), Long.toUnsignedString(newPrimaryKeyId)));
    }

    casInstallStagingHandle(stagingHandle, "KEY_PROMOTED_PRIMARY");

    logger.info(
        "✅ Key promotion completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned),
        toSignedLong(keyId),
        Long.toUnsignedString(keyId));

    return oldPrimaryKeyIdUnsigned;
  }

  private void requireWriterForMutation(String updatedBy) {
    if (!properties.getKeyset().isWriter()) {
      throw new IllegalStateException(
          "Refusing keyset mutation; this process is not the keyset writer (updatedBy="
              + updatedBy
              + ")");
    }
    if (keysetBlobRepository == null) {
      throw new IllegalStateException(
          "KeysetBlobRepository is required for keyset mutations (updatedBy=" + updatedBy + ")");
    }
  }

  /**
   * Reloads the blob under the held write lock so mutations start from the current CAS version
   * (invariant 1).
   *
   * @param reason mutation reason for logging
   * @throws IllegalStateException if the blob cannot be loaded
   */
  private void reloadBlobUnderWriteLockOrThrow(String reason) {
    try {
      if (!loadKeysetFromDatabaseUnlocked()) {
        throw new IllegalStateException(
            "Cannot mutate keyset: ezkey_keyset_blob is absent (reason=" + reason + ")");
      }
    } catch (DataAccessException e) {
      throw new IllegalStateException(
          "Cannot mutate keyset: database error re-reading blob (reason=" + reason + ")", e);
    }
  }

  /**
   * CAS-persists a staging handle and installs it as the live primary only after a successful save
   * (invariants 2–3 / Q4).
   *
   * @param stagingHandle candidate keyset (not yet live)
   * @param updatedBy audit label for the blob row
   * @throws GeneralSecurityException if serialization fails
   * @throws IOException if persistence fails
   * @throws IllegalStateException on CAS loss after reload (fail closed)
   */
  private void casInstallStagingHandle(KeysetHandle stagingHandle, String updatedBy)
      throws GeneralSecurityException, IOException {
    // Capture before any install so CAS+reload failure never leaves a staging primary that is
    // absent from the blob (invariant 2 / probe A). An install-before-CAS mutation is neutralized
    // (not eliminated) by restoring this handle when reload also fails.
    KeysetHandle previousLive = this.keysetHandle;
    long expectedVersion = databaseKeysetVersion;
    try {
      saveKeysetToDatabaseUnlocked(stagingHandle, updatedBy);
      // Invariant 2: install only after successful CAS (never before).
      this.keysetHandle = stagingHandle;
    } catch (DataAccessException | IllegalStateException e) {
      // Invariant 3 / Q4: discard staging, reload blob, fail closed — no orphan primary.
      // OptimisticLockingFailureException is a DataAccessException subclass.
      logger.error(
          "CAS failed for keyset mutation ({}). Discarding staging handle and reloading blob."
              + " Expected version was {}. Error: {}",
          updatedBy,
          expectedVersion,
          e.getMessage());
      try {
        if (!loadKeysetFromDatabaseUnlocked()) {
          logger.error(
              "CAS loser reload found no blob; restoring pre-mutation live handle"
                  + " (updatedBy={})",
              updatedBy);
          this.keysetHandle = previousLive;
        }
      } catch (DataAccessException | IllegalStateException reloadError) {
        logger.error(
            "Failed to reload keyset blob after CAS loss; restoring pre-mutation live handle"
                + " so a key absent from the blob is never served (updatedBy={}): {}",
            updatedBy,
            reloadError.getMessage());
        this.keysetHandle = previousLive;
      }
      throw new IllegalStateException(
          "Keyset mutation CAS failed (updatedBy="
              + updatedBy
              + "); staging discarded and blob reloaded",
          e);
    }
  }

  private int toSignedInt(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  private long toSignedLong(long unsignedKeyId) {
    return (int) unsignedKeyId;
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

  /**
   * Loads the keyset from {@code ezkey_keyset_blob} under the write lock.
   *
   * @return {@code true} if a blob row was present and loaded
   * @throws IllegalStateException if a row is present but cannot be parsed (invariant 8)
   * @throws DataAccessException if the database find fails
   */
  public boolean loadKeysetFromDatabase() {
    keysetLock.writeLock().lock();
    try {
      return loadKeysetFromDatabaseUnlocked();
    } finally {
      keysetLock.writeLock().unlock();
    }
  }

  /**
   * Loads the keyset blob without acquiring the write lock (caller must hold it).
   *
   * <p>Absent row → {@code false}. Present but unloadable/legacy/tampered → {@link
   * IllegalStateException} (invariant 8 — fail closed, no overwrite).
   *
   * @return {@code true} if loaded successfully
   */
  private boolean loadKeysetFromDatabaseUnlocked() {
    if (keysetBlobRepository == null) {
      logger.debug("KeysetBlobRepository not available, cannot load from database");
      return false;
    }

    if (masterAead == null) {
      logger.warn("Master AEAD not initialized, cannot decrypt keyset from database");
      return false;
    }

    var keysetBlobOpt = keysetBlobRepository.findKeyset();
    if (keysetBlobOpt.isEmpty()) {
      logger.debug("No keyset blob found in database");
      return false;
    }

    KeysetBlob keysetBlob = keysetBlobOpt.get();
    byte[] encryptedData = keysetBlob.getKeysetData();
    String encryptedKeysetJson = new String(encryptedData, StandardCharsets.UTF_8);

    try {
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
      return true;
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      // Invariant 8: present but unloadable → fail closed; never overwrite.
      throw new IllegalStateException(
          "ezkey_keyset_blob is present but cannot be parsed (legacy or tampered envelope)."
              + " Refusing to overwrite. Restore a Tink-native envelope or recover via"
              + " supported tooling.",
          e);
    }
  }

  /**
   * Persists the current in-memory keyset to the database (writer only).
   *
   * @param updatedBy audit label for the blob row
   * @throws GeneralSecurityException if serialization fails
   * @throws IOException if persistence fails
   */
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
      if (keysetHandle == null) {
        throw new IllegalStateException("No keyset loaded, cannot save to database");
      }
      saveKeysetToDatabaseUnlocked(keysetHandle, updatedBy);
    } finally {
      keysetLock.readLock().unlock();
    }
  }

  /**
   * Serializes and CAS-saves the given handle to {@code ezkey_keyset_blob}.
   *
   * <p>Does <b>not</b> assign {@link #keysetHandle}; callers that mutate must install only after
   * this method returns successfully (invariant 2).
   *
   * @param handle keyset to persist (live or staging)
   * @param updatedBy audit label for the blob row
   * @throws GeneralSecurityException if serialization fails
   * @throws IOException if persistence fails
   * @throws OptimisticLockingFailureException on Hibernate {@code @Version} conflict
   * @throws DataAccessException on other persistence failures
   * @throws IllegalStateException when the repository returns null or master AEAD is missing
   */
  private void saveKeysetToDatabaseUnlocked(KeysetHandle handle, String updatedBy)
      throws GeneralSecurityException, IOException {
    if (handle == null) {
      throw new IllegalStateException("No keyset handle provided, cannot save to database");
    }

    if (masterAead == null) {
      throw new IllegalStateException("Master AEAD not initialized, cannot encrypt keyset");
    }

    String encryptedKeysetJson =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, masterAead, KEYSET_ASSOCIATED_DATA, RegistryConfiguration.get());
    byte[] encryptedData = encryptedKeysetJson.getBytes(StandardCharsets.UTF_8);

    var existingOpt = keysetBlobRepository.findKeyset();
    KeysetBlob keysetBlob;
    byte[] previousData = null;
    String previousUpdatedBy = null;
    OffsetDateTime previousUpdatedAt = null;
    if (existingOpt.isPresent()) {
      keysetBlob = existingOpt.get();
      previousData = keysetBlob.getKeysetData();
      previousUpdatedBy = keysetBlob.getUpdatedBy();
      previousUpdatedAt = keysetBlob.getLastUpdatedAt();
      // Application CAS vs the version observed at re-read (invariant 1). Hibernate @Version
      // alone is not enough: findKeyset() here loads a fresh entity, so @Version would match
      // a peer commit between re-read and save (B2 / probe B).
      if (databaseKeysetVersion <= 0) {
        throw new IllegalStateException(
            "Refusing keyset blob save with non-positive in-memory version (expected > 0)");
      }
      if (keysetBlob.getVersion() != null
          && !keysetBlob.getVersion().equals(databaseKeysetVersion)) {
        throw new OptimisticLockingFailureException(
            "Keyset blob version mismatch before save (expected="
                + databaseKeysetVersion
                + ", actual="
                + keysetBlob.getVersion()
                + ")");
      }
    } else {
      keysetBlob = new KeysetBlob(encryptedData, updatedBy);
    }
    keysetBlob.setKeysetData(encryptedData);
    keysetBlob.setUpdatedBy(updatedBy);
    keysetBlob.setLastUpdatedAt(OffsetDateTime.now());

    try {
      KeysetBlob saved = keysetBlobRepository.save(keysetBlob);
      if (saved == null) {
        throw new IllegalStateException("Keyset repository returned null on save");
      }
      this.databaseKeysetVersion = saved.getVersion() != null ? saved.getVersion() : 0;
    } catch (DataAccessException | IllegalStateException e) {
      // Restore prior envelope if we mutated a shared/managed entity before save failed
      // (keeps in-memory mock / persistence-context state consistent with fail-closed CAS).
      if (previousData != null) {
        keysetBlob.setKeysetData(previousData);
        keysetBlob.setUpdatedBy(previousUpdatedBy);
        keysetBlob.setLastUpdatedAt(previousUpdatedAt);
      }
      throw e;
    }

    logger.info(
        "✅ Keyset saved to database (version: {}, updated by: {}, primary kid: {})",
        databaseKeysetVersion,
        updatedBy,
        Long.toUnsignedString(getUnsignedPrimaryKeyId(handle)));
  }

  /**
   * Forces a reload of the keyset from the database (AEAD recovery path).
   *
   * @return {@code true} if reload succeeded
   */
  public boolean forceReloadFromDatabase() {
    logger.info("🔄 Force reloading keyset from database...");
    try {
      return loadKeysetFromDatabase();
    } catch (DataAccessException | IllegalStateException e) {
      logger.warn("Force reload from database failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Returns whether database keyset storage is available.
   *
   * @return {@code true} when a {@link KeysetBlobRepository} is wired
   */
  public boolean isDatabaseStorageAvailable() {
    return keysetBlobRepository != null;
  }

  /**
   * Returns the cached optimistic-lock version of the loaded blob.
   *
   * @return database keyset version
   */
  public long getDatabaseKeysetVersion() {
    return databaseKeysetVersion;
  }
}

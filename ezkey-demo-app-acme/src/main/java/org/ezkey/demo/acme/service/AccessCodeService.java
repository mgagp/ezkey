/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AccessCodeService
 * Description: Validates access-code slots and resolves them with SHA-256 digests; hot-reloads
 *              external tenant-alias mappings on URL access.
 */

package org.ezkey.demo.acme.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.AcmeProperties.AccessCodeSlotProperties;
import org.ezkey.demo.acme.config.TenantAliasProperties;
import org.ezkey.demo.acme.service.TenantAliasFileParser.ParseResult;
import org.ezkey.demo.acme.service.TenantAliasFileParser.ParsedAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads optional multi-tenant access-code slots and resolves URL tokens with SHA-256 digests.
 *
 * <p>Base slots come from {@code ezkey.access-codes.*} in application.properties (fail-fast at
 * startup). Community evaluator mappings live in an external volume file ({@link
 * TenantAliasProperties#getFile()}) and are hot-reloaded on {@code /t/{code}} access: mtime is
 * checked at most once every N seconds; the file is reread only when it changed. Unreadable or
 * unusable file content keeps the last valid external mappings; WARN logs never include secrets or
 * the full alias.
 *
 * <p>Lookup hashes the presented code and compares it to every slot digest with {@link
 * MessageDigest#isEqual} (no early exit).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AccessCodeService {

  private static final Logger LOG = LoggerFactory.getLogger(AccessCodeService.class);

  /**
   * Hex codes: community aliases are UUID-v4 truncated to 16 hex chars; longer codes (e.g. {@code
   * openssl rand -hex 16}) remain valid for base application.properties slots.
   */
  static final Pattern CODE_PATTERN = Pattern.compile("^[0-9a-f]{16,64}$");

  private final AcmeProperties properties;
  private final TenantAliasProperties tenantAliasProperties;
  private final Clock clock;

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final List<LoadedSlot> baseSlots = new ArrayList<>();
  private final List<LoadedSlot> externalSlots = new ArrayList<>();
  private final Map<String, LoadedSlot> slotsById = new LinkedHashMap<>();

  private Instant lastMtimeCheck = Instant.EPOCH;
  private FileTime lastLoadedMtime;
  private boolean externalLoadedOnce;

  /**
   * Creates the service with configuration that will be validated at startup.
   *
   * <p>Only constructor (required for Spring constructor injection without {@code @Autowired}).
   *
   * @param properties ACME configuration including optional {@code ezkey.access-codes}
   * @param tenantAliasProperties external mapping file path and reload throttle
   * @param clock clock used for reload throttle (bean provided by the application)
   */
  public AccessCodeService(
      AcmeProperties properties, TenantAliasProperties tenantAliasProperties, Clock clock) {
    this.properties = properties;
    this.tenantAliasProperties = tenantAliasProperties;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  /**
   * Test factory that only exercises base {@code ezkey.access-codes} slots.
   *
   * @param properties ACME configuration
   * @return service instance
   */
  static AccessCodeService forTests(AcmeProperties properties) {
    return new AccessCodeService(properties, new TenantAliasProperties(), Clock.systemUTC());
  }

  /**
   * Test factory with an explicit clock for throttle tests.
   *
   * @param properties ACME configuration
   * @param tenantAliasProperties external mapping file path and reload throttle
   * @param clock clock for throttle tests
   * @return service instance
   */
  static AccessCodeService forTests(
      AcmeProperties properties, TenantAliasProperties tenantAliasProperties, Clock clock) {
    return new AccessCodeService(properties, tenantAliasProperties, clock);
  }

  /**
   * Validates configured base slots and loads the external mapping file once.
   *
   * @throws IllegalStateException if a base code is malformed, duplicated, or a key/label is blank
   */
  @PostConstruct
  public void initialize() {
    loadBaseSlots(properties.getAccessCodes());
    maybeReloadExternal(true);
  }

  /**
   * Validates and loads base slots from application.properties (package-visible for unit tests).
   *
   * @param configured raw slot map from configuration
   * @throws IllegalStateException on validation failure
   */
  void loadSlots(Map<String, AccessCodeSlotProperties> configured) {
    loadBaseSlots(configured);
    rebuildIndex();
  }

  /**
   * Resolves a presented access code / alias to a slot id using constant-time digest comparison.
   *
   * <p>Triggers a throttled mtime check and optional reread of the external mapping file before
   * lookup.
   *
   * @param presentedCode the code from {@code /t/{code}} (never logged by callers)
   * @return matching slot id, or empty when unknown / blank
   */
  public Optional<String> findSlotIdByCode(String presentedCode) {
    maybeReloadExternal(false);
    if (presentedCode == null || presentedCode.isBlank()) {
      return Optional.empty();
    }
    byte[] presentedDigest = sha256(presentedCode.trim());
    lock.readLock().lock();
    try {
      String matchedSlotId = null;
      for (LoadedSlot slot : allSlotsUnlocked()) {
        if (MessageDigest.isEqual(slot.codeDigest(), presentedDigest)) {
          matchedSlotId = slot.slotId();
        }
      }
      return Optional.ofNullable(matchedSlotId);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns Integration API credentials for a slot id, or {@code null} if unknown.
   *
   * @param slotId session-stored slot id
   * @return credentials, or null
   */
  public DemoApiKeyConfigService.DemoApiKeyCredentials resolveCredentials(String slotId) {
    lock.readLock().lock();
    try {
      LoadedSlot slot = slotsById.get(slotId);
      if (slot == null) {
        return null;
      }
      return new DemoApiKeyConfigService.DemoApiKeyCredentials(
          slot.integrationKey(), slot.secretKey());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the operator-facing label for a slot, or {@code null} if unknown.
   *
   * @param slotId session-stored slot id
   * @return label such as {@code Northwind Portal}, or null
   */
  public String getLabel(String slotId) {
    lock.readLock().lock();
    try {
      LoadedSlot slot = slotsById.get(slotId);
      return slot != null ? slot.label() : null;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Forces an external-file reload attempt (tests). Bypasses the throttle when {@code force} is
   * true.
   *
   * @param force when true, skip the throttle window
   */
  void maybeReloadExternal(boolean force) {
    String configuredPath = tenantAliasProperties.getFile();
    if (configuredPath == null || configuredPath.isBlank()) {
      return;
    }

    Instant now = clock.instant();
    if (!force) {
      int throttleSeconds = tenantAliasProperties.getReloadThrottleSeconds();
      if (throttleSeconds > 0
          && lastMtimeCheck != null
          && now.isBefore(lastMtimeCheck.plusSeconds(throttleSeconds))) {
        return;
      }
    }
    lastMtimeCheck = now;

    Path path = Path.of(configuredPath);
    if (!Files.isRegularFile(path)) {
      if (externalLoadedOnce) {
        LOG.warn(
            "Tenant-alias mapping file is missing or not a regular file; keeping last valid"
                + " external mappings");
      } else {
        LOG.info(
            "No tenant-alias mapping file at configured path; external overlays empty until the"
                + " file appears");
      }
      return;
    }

    FileTime mtime;
    try {
      mtime = Files.getLastModifiedTime(path);
    } catch (IOException e) {
      LOG.warn(
          "Could not read mtime of tenant-alias mapping file; keeping last valid external"
              + " mappings");
      return;
    }

    if (!force && externalLoadedOnce && mtime.equals(lastLoadedMtime)) {
      return;
    }

    String text;
    try {
      text = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.warn("Could not read tenant-alias mapping file; keeping last valid external mappings");
      return;
    }

    ParseResult parsed = TenantAliasFileParser.parse(text);
    if (parsed.hasUnusableContent()) {
      LOG.warn(
          "Tenant-alias mapping file has no valid lines ({} content line(s) rejected); keeping"
              + " last valid external mappings",
          parsed.contentLineCount());
      return;
    }

    List<LoadedSlot> nextExternal = new ArrayList<>();
    List<byte[]> digests = new ArrayList<>();
    for (LoadedSlot base : baseSlots) {
      digests.add(base.codeDigest());
    }

    for (ParsedAlias alias : parsed.mappings().values()) {
      byte[] digest = sha256(alias.alias());
      boolean duplicate = false;
      for (byte[] existing : digests) {
        if (MessageDigest.isEqual(existing, digest)) {
          duplicate = true;
          break;
        }
      }
      if (duplicate) {
        LOG.warn(
            "Skipping tenant-alias line for label={}: alias digest collides with another slot",
            alias.label());
        continue;
      }
      digests.add(digest);
      String slotId = externalSlotId(alias.alias());
      nextExternal.add(
          new LoadedSlot(slotId, digest, alias.integrationKey(), alias.secretKey(), alias.label()));
    }

    lock.writeLock().lock();
    try {
      externalSlots.clear();
      externalSlots.addAll(nextExternal);
      rebuildIndexUnlocked();
      lastLoadedMtime = mtime;
      externalLoadedOnce = true;
    } finally {
      lock.writeLock().unlock();
    }

    if (nextExternal.isEmpty()) {
      LOG.info("Tenant-alias mapping file loaded with 0 external slots");
    } else {
      for (LoadedSlot slot : nextExternal) {
        LOG.info("Access-code slot loaded from external file: label={}", slot.label());
      }
    }
  }

  private void loadBaseSlots(Map<String, AccessCodeSlotProperties> configured) {
    lock.writeLock().lock();
    try {
      baseSlots.clear();
      if (configured == null || configured.isEmpty()) {
        LOG.info(
            "No access-code slots configured in application.properties; temporary access links use"
                + " the external mapping file only (if present).");
        rebuildIndexUnlocked();
        return;
      }

      List<byte[]> digests = new ArrayList<>();
      for (Map.Entry<String, AccessCodeSlotProperties> entry : configured.entrySet()) {
        String slotId = entry.getKey();
        AccessCodeSlotProperties raw = entry.getValue();
        if (raw == null) {
          throw new IllegalStateException(
              "Access-code slot '" + slotId + "' is missing configuration.");
        }
        String code = raw.getCode() != null ? raw.getCode().trim() : "";
        String integrationKey =
            raw.getIntegrationKey() != null ? raw.getIntegrationKey().trim() : "";
        String secretKey = raw.getSecretKey() != null ? raw.getSecretKey().trim() : "";
        String label = raw.getLabel() != null ? raw.getLabel().trim() : "";

        if (!CODE_PATTERN.matcher(code).matches()) {
          throw new IllegalStateException(
              "Access-code slot '"
                  + slotId
                  + "' has an invalid code (expected 16–64 lowercase hex characters).");
        }
        if (integrationKey.isBlank() || secretKey.isBlank() || label.isBlank()) {
          throw new IllegalStateException(
              "Access-code slot '"
                  + slotId
                  + "' requires non-blank integration-key, secret-key, and label.");
        }

        byte[] digest = sha256(code);
        for (byte[] existing : digests) {
          if (MessageDigest.isEqual(existing, digest)) {
            throw new IllegalStateException(
                "Duplicate access code configured (slots must not share a code).");
          }
        }
        digests.add(digest);

        LoadedSlot loaded = new LoadedSlot(slotId, digest, integrationKey, secretKey, label);
        baseSlots.add(loaded);
        LOG.info("Access-code slot loaded: id={}, label={}", slotId, label);
      }
      rebuildIndexUnlocked();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void rebuildIndex() {
    lock.writeLock().lock();
    try {
      rebuildIndexUnlocked();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void rebuildIndexUnlocked() {
    slotsById.clear();
    for (LoadedSlot slot : baseSlots) {
      slotsById.put(slot.slotId(), slot);
    }
    for (LoadedSlot slot : externalSlots) {
      slotsById.put(slot.slotId(), slot);
    }
  }

  private List<LoadedSlot> allSlotsUnlocked() {
    List<LoadedSlot> all = new ArrayList<>(baseSlots.size() + externalSlots.size());
    all.addAll(baseSlots);
    all.addAll(externalSlots);
    return all;
  }

  /**
   * Stable slot id that is not the plaintext alias (avoids logging the capability token).
   *
   * @param alias capability token
   * @return opaque slot id
   */
  static String externalSlotId(String alias) {
    String digestHex = HexFormat.of().formatHex(sha256(alias));
    return "ext:" + digestHex.substring(0, 16);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * In-memory slot after hashing.
   *
   * @param slotId configuration key or opaque external id
   * @param codeDigest SHA-256 of the access code / alias
   * @param integrationKey Integration API integration key
   * @param secretKey Integration API secret key
   * @param label operator-facing label shown on /login
   */
  private record LoadedSlot(
      String slotId, byte[] codeDigest, String integrationKey, String secretKey, String label) {}
}

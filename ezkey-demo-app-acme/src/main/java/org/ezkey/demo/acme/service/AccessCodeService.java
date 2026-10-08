/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AccessCodeService
 * Description: Validates access-code slots at startup and resolves them with SHA-256 digests.
 */

package org.ezkey.demo.acme.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.AcmeProperties.AccessCodeSlotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads optional multi-tenant access-code slots, fail-fast validates them, and keeps only SHA-256
 * digests of codes in memory.
 *
 * <p>Lookup hashes the presented code and compares it to every slot digest with {@link
 * MessageDigest#isEqual} (no early exit, never {@code equals} or a map keyed by plaintext).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AccessCodeService {

  private static final Logger LOG = LoggerFactory.getLogger(AccessCodeService.class);

  /** Hex codes from {@code openssl rand -hex 16} (32) up to 64 hex chars (256 bits). */
  static final Pattern CODE_PATTERN = Pattern.compile("^[0-9a-f]{32,64}$");

  private final AcmeProperties properties;
  private final List<LoadedSlot> slots = new ArrayList<>();
  private final Map<String, LoadedSlot> slotsById = new LinkedHashMap<>();

  /**
   * Creates the service with configuration that will be validated at startup.
   *
   * @param properties ACME configuration including optional {@code ezkey.access-codes}
   */
  public AccessCodeService(AcmeProperties properties) {
    this.properties = properties;
  }

  /**
   * Validates configured slots and replaces plaintext codes with SHA-256 digests.
   *
   * @throws IllegalStateException if a code is malformed, duplicated, or a key/label is blank
   */
  @PostConstruct
  public void initialize() {
    loadSlots(properties.getAccessCodes());
  }

  /**
   * Validates and loads slots (package-visible for unit tests without a Spring context).
   *
   * @param configured raw slot map from configuration
   * @throws IllegalStateException on validation failure
   */
  void loadSlots(Map<String, AccessCodeSlotProperties> configured) {
    slots.clear();
    slotsById.clear();
    if (configured == null || configured.isEmpty()) {
      LOG.info(
          "No access-code slots configured; temporary access links will show the generic error.");
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
      String integrationKey = raw.getIntegrationKey() != null ? raw.getIntegrationKey().trim() : "";
      String secretKey = raw.getSecretKey() != null ? raw.getSecretKey().trim() : "";
      String label = raw.getLabel() != null ? raw.getLabel().trim() : "";

      if (!CODE_PATTERN.matcher(code).matches()) {
        throw new IllegalStateException(
            "Access-code slot '"
                + slotId
                + "' has an invalid code (expected 32–64 lowercase hex characters from openssl"
                + " rand -hex 16).");
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
      slots.add(loaded);
      slotsById.put(slotId, loaded);
      LOG.info("Access-code slot loaded: id={}, label={}", slotId, label);
    }
  }

  /**
   * Resolves a presented access code to a slot id using constant-time digest comparison.
   *
   * @param presentedCode the code from {@code /t/{code}} (never logged by callers)
   * @return matching slot id, or empty when unknown / blank
   */
  public Optional<String> findSlotIdByCode(String presentedCode) {
    if (presentedCode == null || presentedCode.isBlank() || slots.isEmpty()) {
      return Optional.empty();
    }
    byte[] presentedDigest = sha256(presentedCode.trim());
    String matchedSlotId = null;
    // Compare against every digest; no early exit (constant-time style lookup).
    for (LoadedSlot slot : slots) {
      if (MessageDigest.isEqual(slot.codeDigest(), presentedDigest)) {
        matchedSlotId = slot.slotId();
      }
    }
    return Optional.ofNullable(matchedSlotId);
  }

  /**
   * Returns Integration API credentials for a slot id, or {@code null} if unknown.
   *
   * @param slotId session-stored slot id
   * @return credentials, or null
   */
  public DemoApiKeyConfigService.DemoApiKeyCredentials resolveCredentials(String slotId) {
    LoadedSlot slot = slotsById.get(slotId);
    if (slot == null) {
      return null;
    }
    return new DemoApiKeyConfigService.DemoApiKeyCredentials(
        slot.integrationKey(), slot.secretKey());
  }

  /**
   * Returns the operator-facing label for a slot, or {@code null} if unknown.
   *
   * @param slotId session-stored slot id
   * @return label such as {@code Northwind Portal}, or null
   */
  public String getLabel(String slotId) {
    LoadedSlot slot = slotsById.get(slotId);
    return slot != null ? slot.label() : null;
  }

  /**
   * Returns whether any access-code slots are loaded.
   *
   * @return true when at least one slot is configured
   */
  public boolean hasSlots() {
    return !slots.isEmpty();
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * In-memory slot after startup hashing.
   *
   * @param slotId configuration key
   * @param codeDigest SHA-256 of the access code
   * @param integrationKey Integration API integration key
   * @param secretKey Integration API secret key
   * @param label operator-facing label shown on /login
   */
  private record LoadedSlot(
      String slotId, byte[] codeDigest, String integrationKey, String secretKey, String label) {}
}

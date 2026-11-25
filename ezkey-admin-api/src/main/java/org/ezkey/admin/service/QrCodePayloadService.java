/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: QrCodePayloadService
 * Description: Centralized service for composing QR code payloads in JSON format.
 */

package org.ezkey.admin.service;

import org.ezkey.config.QrCodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Centralized service for composing QR code payloads.
 *
 * <p>This service produces JSON payloads for QR codes containing enrollment credentials and,
 * optionally, the public auth-api URL. The JSON format is extensible and already supported by the
 * mobile application's {@code parseQrPayload} parser.
 *
 * <p><b>QR Code JSON Format:</b>
 *
 * <pre>
 * {
 *   "enrollmentProofToken": "abc123...",
 *   "authUrl": "https://ezkey.acme.com:8080"
 * }
 * </pre>
 *
 * <p>The {@code authUrl} field is only included when {@code ezkey.qr.auth-base-url} is configured.
 * When absent, the mobile application falls back to its own configured base URL.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class QrCodePayloadService {

  private static final Logger logger = LoggerFactory.getLogger(QrCodePayloadService.class);

  private static final int TERMINAL_WIDTH_WARNING_THRESHOLD = 150;

  private final QrCodeProperties qrCodeProperties;

  private final ObjectMapper objectMapper;

  /**
   * Constructs the QR code payload service with required dependencies.
   *
   * @param qrCodeProperties the QR code configuration properties
   */
  public QrCodePayloadService(QrCodeProperties qrCodeProperties) {
    this.qrCodeProperties = qrCodeProperties;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Composes a JSON payload for QR code encoding.
   *
   * <p>The payload is the enrollment proof token. {@code enrollmentId} is not an invitation field
   * (ADR-0019). If {@code ezkey.qr.auth-base-url} is configured, an {@code authUrl} field is added
   * so the device can reach the Auth API for this instance.
   *
   * @param enrollmentProofToken the enrollment proof token
   * @return JSON string suitable for QR code encoding
   * @throws IllegalStateException if JSON serialization fails
   */
  public String composePayload(String enrollmentProofToken) {
    try {
      ObjectNode payload = objectMapper.createObjectNode();
      payload.put("enrollmentProofToken", enrollmentProofToken);

      String authBaseUrl = qrCodeProperties.getAuthBaseUrl();
      if (authBaseUrl != null && !authBaseUrl.isBlank()) {
        payload.put("authUrl", authBaseUrl.strip());
      }

      String json = objectMapper.writeValueAsString(payload);

      if (json.length() > TERMINAL_WIDTH_WARNING_THRESHOLD) {
        logger.debug(
            "QR payload length is {} chars — ASCII QR code may require a terminal wider"
                + " than 120 columns",
            json.length());
      }

      return json;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      throw new IllegalStateException("Failed to compose QR code payload", e);
    }
  }

  /**
   * Composes the enrollment code: the text an operator copies for an authenticator that cannot scan
   * (#1494).
   *
   * <p>It is exactly the QR payload ({@link #composePayload(String)}), so the copied text and the
   * QR code never differ. It is offered only when {@code ezkey.qr.auth-base-url} is configured:
   * without {@code authUrl}, an authenticator that receives pasted text has no way to find the Auth
   * API, so the caller gets {@code null} and the Admin UI says why instead of offering a copy.
   *
   * @param enrollmentProofToken the enrollment proof token, or null when the caller may not see it
   * @return the enrollment code JSON, or null when there is no token or no Auth API address
   */
  public String composeEnrollmentCode(String enrollmentProofToken) {
    if (enrollmentProofToken == null || enrollmentProofToken.isBlank()) {
      return null;
    }
    String authBaseUrl = qrCodeProperties.getAuthBaseUrl();
    if (authBaseUrl == null || authBaseUrl.isBlank()) {
      return null;
    }
    return composePayload(enrollmentProofToken);
  }
}

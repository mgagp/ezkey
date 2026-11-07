/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Port: DeliveryConnectorPort
 */

package org.ezkey.admin.connector;

import java.util.List;
import java.util.Optional;

/**
 * Delivery port: how the Admin API reaches a delivery connector (contract {@code
 * ezkey-connectors/contracts/delivery-v0.openapi.yaml}).
 *
 * @since 2026
 */
public interface DeliveryConnectorPort {

  /** The contract version this core speaks. */
  String CONTRACT_VERSION = "v0";

  /** The only template this contract version knows. */
  String TEMPLATE_ENROLLMENT_QR = "enrollment-qr";

  /**
   * Sends one job. Never throws for a connector failure: every failure maps to an outcome.
   *
   * @param job the job
   * @return the outcome
   */
  DeliveryResult send(DeliveryJob job);

  /**
   * Reads the connector's state and capabilities. Does not contact the provider.
   *
   * @return the health, or empty when the connector is unreachable or answers something unreadable
   */
  Optional<ConnectorHealth> readHealth();

  /** What happened to a job, in the contract, the UI and the audit. */
  enum Outcome {
    /** The provider took the message. This does not mean the person received it. */
    ACCEPTED,
    /** The message did not leave. */
    REJECTED,
    /** The message may have left. */
    UNKNOWN
  }

  /**
   * One job. {@link #toString()} shows neither the QR nor the recipient, so it is safe if logged.
   *
   * @param sendId one per operator click
   * @param tenantRef opaque tenant reference
   * @param locale template locale
   * @param recipient the enrollment's contact email
   * @param organisationName the only free text in the job
   * @param expectedServer the server the app should show
   * @param qrPngBase64 the QR, a bearer secret until bind
   */
  record DeliveryJob(
      String sendId,
      String tenantRef,
      String locale,
      String recipient,
      String organisationName,
      String expectedServer,
      String qrPngBase64) {

    @Override
    public String toString() {
      return "DeliveryJob[sendId=" + sendId + "]";
    }
  }

  /**
   * The outcome of one job.
   *
   * @param outcome what happened
   * @param code typed error code, or null when accepted
   * @param providerReference provider's reference, or null
   * @param testMode true when the connector said it is a test double
   */
  record DeliveryResult(Outcome outcome, String code, String providerReference, boolean testMode) {}

  /**
   * The connector's state and capabilities.
   *
   * @param status {@code ok} or {@code degraded}
   * @param connectorName connector name
   * @param contractVersion contract version it speaks
   * @param channel channel it serves
   * @param locales locales it offers for the enrollment QR template
   * @param testMode true for a test double
   */
  record ConnectorHealth(
      String status,
      String connectorName,
      String contractVersion,
      String channel,
      List<String> locales,
      boolean testMode) {}
}

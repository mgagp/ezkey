/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EvaluatorSelfRegistrationProperties
 * Description: Installation-scoped settings for anonymous evaluator self-registration.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for anonymous evaluator self-registration on experimental preview instances.
 *
 * <p>When {@code enabled} is {@code false}, the public signup endpoint responds with HTTP 404.
 *
 * <p>URL fields intentionally have <b>no product default hostname</b>. When {@code enabled=true},
 * startup fails unless {@code admin-ui-url} and {@code guided-tour-url} are set via environment or
 * overlay (fail-closed). See {@link EvaluatorSelfRegistrationStartupValidator}.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.evaluator.self-registration}
 */
@ConfigurationProperties(prefix = "ezkey.evaluator.self-registration")
public class EvaluatorSelfRegistrationProperties {

  private boolean enabled = false;

  private int dailyCap = 5;

  private int perIpWindowHours = 24;

  private int perIpMaxSuccess = 1;

  /** Admin UI origin returned after signup; empty until set for a live surface. */
  private String adminUiUrl = "";

  /** Guided tour URL returned after signup; empty until set for a live surface. */
  private String guidedTourUrl = "";

  /**
   * Absolute TTL in hours for the Admin UI bootstrap session minted with signup.
   *
   * <p>Product lock: exactly 8 hours, no sliding. Separate from activation-code TTL (7 days).
   */
  private int bootstrapSessionTtlHours = 8;

  /**
   * Max successful onboarding re-issues per client IP per rolling hour (community resume path).
   *
   * <p>Separate from signup caps so a same-day logout can still resume incomplete enrollment.
   */
  private int reissuePerIpMaxPerHour = 10;

  /** Max successful onboarding re-issues per username per rolling hour. */
  private int reissuePerUsernameMaxPerHour = 5;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getDailyCap() {
    return dailyCap;
  }

  public void setDailyCap(int dailyCap) {
    this.dailyCap = dailyCap;
  }

  public int getPerIpWindowHours() {
    return perIpWindowHours;
  }

  public void setPerIpWindowHours(int perIpWindowHours) {
    this.perIpWindowHours = perIpWindowHours;
  }

  public int getPerIpMaxSuccess() {
    return perIpMaxSuccess;
  }

  public void setPerIpMaxSuccess(int perIpMaxSuccess) {
    this.perIpMaxSuccess = perIpMaxSuccess;
  }

  public String getAdminUiUrl() {
    return adminUiUrl;
  }

  public void setAdminUiUrl(String adminUiUrl) {
    this.adminUiUrl = adminUiUrl;
  }

  public String getGuidedTourUrl() {
    return guidedTourUrl;
  }

  public void setGuidedTourUrl(String guidedTourUrl) {
    this.guidedTourUrl = guidedTourUrl;
  }

  public int getBootstrapSessionTtlHours() {
    return bootstrapSessionTtlHours;
  }

  public void setBootstrapSessionTtlHours(int bootstrapSessionTtlHours) {
    this.bootstrapSessionTtlHours = bootstrapSessionTtlHours;
  }

  public int getReissuePerIpMaxPerHour() {
    return reissuePerIpMaxPerHour;
  }

  public void setReissuePerIpMaxPerHour(int reissuePerIpMaxPerHour) {
    this.reissuePerIpMaxPerHour = reissuePerIpMaxPerHour;
  }

  public int getReissuePerUsernameMaxPerHour() {
    return reissuePerUsernameMaxPerHour;
  }

  public void setReissuePerUsernameMaxPerHour(int reissuePerUsernameMaxPerHour) {
    this.reissuePerUsernameMaxPerHour = reissuePerUsernameMaxPerHour;
  }
}

/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EvaluatorSelfRegistrationProperties
 * Description: Installation-scoped settings for anonymous EXP1 evaluator self-registration.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for anonymous evaluator self-registration on experimental preview instances.
 *
 * <p>When {@code enabled} is {@code false}, the public signup endpoint responds with HTTP 404.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.evaluator.self-registration}
 */
@ConfigurationProperties(prefix = "ezkey.evaluator.self-registration")
public class EvaluatorSelfRegistrationProperties {

  private boolean enabled = false;

  private int dailyCap = 5;

  private int perIpWindowHours = 24;

  private int perIpMaxSuccess = 1;

  private String adminUiUrl = "https://exp1-admin-ui.ezkey.org";

  private String guidedTourUrl = "https://ezkey.org/exp1-guided-tour.html";

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
}

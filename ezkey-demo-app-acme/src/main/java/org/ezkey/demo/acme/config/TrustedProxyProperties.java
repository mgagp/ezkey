/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TrustedProxyProperties
 * Description: Trusted proxy CIDR list for client IP resolution in the ACME demo application.
 */

package org.ezkey.demo.acme.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trusted proxy CIDR list for client IP resolution.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.trusted-proxies")
public class TrustedProxyProperties {

  private List<String> cidrs = new ArrayList<>();

  public List<String> getCidrs() {
    return cidrs == null ? Collections.emptyList() : Collections.unmodifiableList(cidrs);
  }

  public void setCidrs(List<String> cidrs) {
    this.cidrs = cidrs == null ? new ArrayList<>() : new ArrayList<>(cidrs);
  }
}

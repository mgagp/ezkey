/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Properties: IntegrityAsyncJobProperties
 * Description: TTL and executor settings for Integrity async operator jobs.
 */

package org.ezkey.audit.integrity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Integrity async operator jobs ({@code ezkey.audit.integrity.async-job.*}).
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.async-job")
public class IntegrityAsyncJobProperties {

  /**
   * Max age of a RUNNING job without heartbeat before it becomes EXPIRED on the next GET/POST.
   * Default 60 minutes.
   */
  private Duration ttl = Duration.ofMinutes(60);

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }
}

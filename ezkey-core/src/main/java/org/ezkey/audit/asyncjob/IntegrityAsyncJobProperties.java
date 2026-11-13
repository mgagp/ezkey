/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Properties: IntegrityAsyncJobProperties
 * Description: TTL and executor settings for Integrity async operator jobs.
 */

package org.ezkey.audit.asyncjob;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Integrity async operator jobs ({@code ezkey.audit.integrity.async-job.*}).
 *
 * <p>Admin-only when {@code enabled=true}. Auth/Integration leave this flag unset/false so the
 * async-job beans and Admin-only table grants stay off those runtimes.
 *
 * @since 2026
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.async-job.enabled",
    havingValue = "true",
    matchIfMissing = false)
@ConfigurationProperties(prefix = "ezkey.audit.integrity.async-job")
public class IntegrityAsyncJobProperties {

  /**
   * When true, Admin API loads Integrity async job beans. Defaults false so Auth/Integration do not
   * register the Admin-only slot.
   */
  private boolean enabled = false;

  /**
   * Max age of a RUNNING job without heartbeat before it becomes EXPIRED on the next GET/POST.
   * Default 60 minutes.
   */
  private Duration ttl = Duration.ofMinutes(60);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }
}

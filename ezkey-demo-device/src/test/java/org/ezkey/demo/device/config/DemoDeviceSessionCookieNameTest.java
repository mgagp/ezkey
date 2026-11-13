/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DemoDeviceSessionCookieNameTest
 * Description: Demo device uses a distinct servlet session cookie name (#799).
 */

package org.ezkey.demo.device.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.demo.device.DemoDeviceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Guards the localhost cookie isolation contract with Acme (#799).
 *
 * @since 2026
 */
@SpringBootTest(classes = DemoDeviceApplication.class)
class DemoDeviceSessionCookieNameTest {

  @Autowired private Environment environment;

  @Test
  void usesDistinctSessionCookieName() {
    assertThat(environment.getProperty("server.servlet.session.cookie.name"))
        .isEqualTo("EZKEY_DEVICE_SESSION");
  }
}

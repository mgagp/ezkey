package org.ezkey.demo.device;

import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for Ezkey Demo Device application.
 *
 * @since 2025
 */
@SpringBootApplication
@EnableConfigurationProperties(DemoDeviceProperties.class)
public class DemoDeviceApplication {
  public static void main(String[] args) {
    SpringApplication.run(DemoDeviceApplication.class, args);
  }
}

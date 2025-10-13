package org.ezkey.demo.device.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Ezkey Auth API WebClient.
 *
 * @since 2025
 */
@Configuration
public class AuthApiClientConfig {

  @Bean(name = "ezkeyAuthApiClient")
  public WebClient ezkeyAuthApiClient(
      @Value("${ezkey.auth.api.url:http://localhost:8080}") String baseUrl) {
    return WebClient.builder().baseUrl(baseUrl).build();
  }
}

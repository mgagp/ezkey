package org.ezkey.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;

/**
 * EzkeyCoreApp - Application for running Flyway migrations only.
 * <p>
 * This application is intended to be used for database schema migrations (Flyway)
 * without starting any REST controllers or web server.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class})
public class EzkeyCoreApp {
    public static void main(String[] args) {
        SpringApplication.run(EzkeyCoreApp.class, args);
    }
}

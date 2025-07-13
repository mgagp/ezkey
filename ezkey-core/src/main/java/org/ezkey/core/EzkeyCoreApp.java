package org.ezkey.core;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.flywaydb.core.Flyway;

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
@SpringBootApplication
public class EzkeyCoreApp {
    
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EzkeyCoreApp.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
    
    @Bean
    public CommandLineRunner flywayRunner(Flyway flyway) {
        return args -> {
            System.out.println("=== Starting Flyway Migration ===");
            flyway.migrate();
            System.out.println("=== Flyway Migration Completed ===");
        };
    }
}

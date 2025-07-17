package org.ezkey.core;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;

/**
 * EzkeyCoreApp - Application for running Flyway migrations only.
 * <p>
 * This application is intended to be used for database schema migrations (Flyway)
 * without starting any REST controllers or web server.
 * </p>
 * <p>
 * Usage:
 * - No args: runs migrate
 * - --info: shows migration info
 * - --repair: runs repair
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication
public class EzkeyCoreApp {

    public static void main(String[] args){
        SpringApplication app = new SpringApplication(EzkeyCoreApp.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    public CommandLineRunner flywayRunner(Flyway flyway){
        return args -> {
            if (args.length == 0){
                // Default: migrate
                System.out.println("=== Starting Flyway Migration ===");
                flyway.migrate();
                System.out.println("=== Flyway Migration Completed ===");
            } else{
                String command = args[0].toLowerCase();
                switch (command) {
                case "--info":
                    showInfo(flyway);
                    break;
                case "--repair":
                    System.out.println("=== Starting Flyway Repair ===");
                    flyway.repair();
                    System.out.println("=== Flyway Repair Completed ===");
                    break;
                case "--migrate":
                    System.out.println("=== Starting Flyway Migration ===");
                    flyway.migrate();
                    System.out.println("=== Flyway Migration Completed ===");
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    showHelp();
                    break;
                }
            }
        };
    }

    private void showInfo(Flyway flyway){
        System.out.println("=== Flyway Migration Info ===");
        MigrationInfoService info = flyway.info();

        System.out.println("Current version: " + info.current());
        System.out.println("Pending migrations: " + info.pending().length);
        System.out.println("Applied migrations: " + info.applied().length);
        if (info.pending().length > 0){
            System.out.println("\nPending migrations:");
            for (var migration : info.pending()){
                System.out.println("  - " + migration.getVersion() + " : " + migration.getDescription());
            }
        }
        if (info.applied().length > 0){
            System.out.println("\nApplied migrations:");
            for (var migration : info.applied()){
                System.out.println("  - " + migration.getVersion() + " : " + migration.getDescription() + " (executed: " + migration.getInstalledOn() + ")");
            }
        }
        System.out.println("=== Info Completed ===");
    }

    private void showHelp(){
        System.out.println("Available commands:");
        System.out.println("  --migrate  : Run migrations (default)");
        System.out.println("  --info     : Show migration info");
        System.out.println("  --repair   : Repair migration history");
    }
}

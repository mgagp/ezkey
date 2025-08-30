/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: MigrationApplication
 * Description: Docker-specific alias for EzkeyCoreApp to handle database migrations
 */

package org.ezkey.core;

/**
 * MigrationApplication - Docker alias for EzkeyCoreApp.
 * <p>
 * This class provides a simple alias for Docker deployment where we want
 * a clear main class name for migration operations.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class MigrationApplication {

    /**
     * Main method that delegates to EzkeyCoreApp.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        EzkeyCoreApp.main(args);
    }
}
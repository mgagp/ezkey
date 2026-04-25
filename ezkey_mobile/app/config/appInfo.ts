/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: appInfo
 * Description: Centralizes app metadata (display name, version) sourced from package.json so the UI
 * stays in lockstep with build pipeline metadata (versionName/versionCode managed in Gradle).
 * @since 2025
 */

import packageJson from '../../package.json';

/**
 * Human-readable application name displayed in the About screen and shareable diagnostics.
 *
 * @since 2025
 */
export const APP_DISPLAY_NAME = 'Ezkey';

/**
 * Semantic application version sourced from `package.json`. Mirrors the Android
 * `versionName` and the iOS `CFBundleShortVersionString` and is the single source of truth
 * surfaced to users in the UI.
 *
 * @since 2025
 */
export const APP_VERSION: string = packageJson.version;

/**
 * Stable npm package identifier (kept for diagnostics; not user-facing).
 *
 * @since 2025
 */
export const APP_PACKAGE_NAME: string = packageJson.name;

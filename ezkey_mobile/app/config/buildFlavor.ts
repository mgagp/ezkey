/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: buildFlavor
 * Description: Sync read of native build flavor (debug vs release) for production-clean gates.
 * @since 2025
 */

import {NativeModules} from 'react-native';

type EzkeyCryptoNativeConstants = {
  isDebugBuild?: boolean;
};

/**
 * Returns whether the installed native binary is an Android (or future iOS) **debug** build.
 *
 * Source of truth: {@code EzkeyCryptoModule} {@code getConstants().isDebugBuild}
 * ({@code BuildConfig.DEBUG} on Android). This is **not** the same as React Native {@code __DEV__},
 * which can be false on offline-capable debug APKs that still use the debug Gradle build type.
 *
 * When the native module is missing (Jest without mock, or unlinked binary), returns {@code false}
 * (fail closed for test-only surfaces).
 *
 * @returns true only when the native module reports a debug build
 */
export function readIsDebugBuild(): boolean {
  const mod = NativeModules.EzkeyCryptoModule as EzkeyCryptoNativeConstants | undefined;
  return mod?.isDebugBuild === true;
}

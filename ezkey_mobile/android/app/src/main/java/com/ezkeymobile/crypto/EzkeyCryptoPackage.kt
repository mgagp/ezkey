/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyCryptoPackage.kt
 * Description: ReactPackage that registers Ezkey's cryptographic native module.
 * Security Context: Guarantees that the RSA module is always part of the native bridge ensuring
 * cryptographic parity with backend services as outlined in docs/CRYPTO.md.
 * @since 2025
 */

package com.ezkeymobile.crypto

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * React package responsible for exposing Ezkey crypto native modules to React Native.
 *
 * @since 2025
 */
class EzkeyCryptoPackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
      listOf(EzkeyCryptoModule(reactContext))

  override fun createViewManagers(
      reactContext: ReactApplicationContext
  ): List<ViewManager<*, *>> = emptyList()
}

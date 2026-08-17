/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyPlayUpdatePackage.kt
 * Description: Registers the Play flexible-update native module.
 * @since 2026
 */

package org.ezkey.mobile.play

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * React package that exposes [EzkeyPlayUpdateModule] to JavaScript.
 *
 * @since 2026
 */
class EzkeyPlayUpdatePackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
      listOf(EzkeyPlayUpdateModule(reactContext))

  override fun createViewManagers(
      reactContext: ReactApplicationContext
  ): List<ViewManager<*, *>> = emptyList()
}

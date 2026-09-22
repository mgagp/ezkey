/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: MainApplication.kt
 * Description: React Native application class responsible for bootstrapping native modules.
 * Security Context: Registers the cryptographic bridge defined in docs/CRYPTO.md and docs/ENDPOINT.md.
 * @since 2025
 */

package org.ezkey.mobile

import android.app.Application
import org.ezkey.mobile.crypto.EzkeyCryptoPackage
import org.ezkey.mobile.play.EzkeyPlayUpdatePackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
/**
 * Application entry point that registers Ezkey-specific native modules.
 *
 * @since 2025
 */
class MainApplication : Application(), ReactApplication {

  /**
   * Provides the React Native host configured with Ezkey-specific packages.
   *
   * @since 2025
   */
  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this@MainApplication) {
        override fun getPackages(): List<ReactPackage> =
            PackageList(this@MainApplication).packages.apply {
              add(EzkeyCryptoPackage())
              add(EzkeyPlayUpdatePackage())
            }

        override fun getJSMainModuleName(): String = "index"

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.USE_DEVELOPER_SUPPORT

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  /**
   * Exposes the React Host used by the new architecture runtime.
   *
   * @since 2025
   */
  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  /**
   * Initializes native modules and the SoLoader stack.
   *
   * @since 2025
   */
  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, OpenSourceMergedSoMapping)
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      load()
    }
  }
}

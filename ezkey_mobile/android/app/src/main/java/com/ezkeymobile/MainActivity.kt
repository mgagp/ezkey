/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: MainActivity.kt
 * Description: Entry point activity that hosts the React Native surface for Ezkey Mobile.
 * Security Context: Maintains the bridge to the React Navigation stack that drives secure enrollment
 * and authentication flows.
 * @since 2025
 */

package com.ezkeymobile

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * Hosts the Ezkey React Native application surface.
 *
 * @since 2025
 */
class MainActivity : ReactActivity() {

  /**
   * Returns the main React Native component name used by the navigation host.
   *
   * @since 2025
   */
  override fun getMainComponentName(): String = "EzkeyMobile"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   *
   * @since 2025
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}

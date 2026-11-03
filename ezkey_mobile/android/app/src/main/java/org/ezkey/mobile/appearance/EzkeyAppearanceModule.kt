/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyAppearanceModule.kt
 * Description: Per-app night mode for the Appearance setting (Light / Dark / System default).
 * @since 2026
 */

package org.ezkey.mobile.appearance

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.core.view.WindowCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Sets the per-app night mode (API 31, our `minSdk`). The system persists it, so the splash
 * screen and system dialogs match the chosen theme from the next cold start, before JavaScript
 * runs. React Native's `Appearance.setColorScheme` handles the in-session switch.
 *
 * Fail-open: an unknown mode or a missing service is logged and the in-app theme still applies.
 *
 * @since 2026
 */
class EzkeyAppearanceModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "EzkeyAppearanceModule"

    /**
     * Matches the status and navigation bar icons to the activity's current night mode: dark
     * icons on the light theme, light icons on the dark theme.
     *
     * @param activity activity whose window bars to update; call on the UI thread
     * @since 2026
     */
    fun applySystemBarIcons(activity: Activity) {
      val night =
          (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
              Configuration.UI_MODE_NIGHT_YES
      WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = !night
        isAppearanceLightNavigationBars = !night
      }
    }
  }

  override fun getName(): String = NAME

  /**
   * Sets the per-app night mode.
   *
   * @param mode `system`, `light` or `dark`
   * @since 2026
   */
  @ReactMethod
  fun setApplicationNightMode(mode: String) {
    val nightMode =
        when (mode) {
          "light" -> UiModeManager.MODE_NIGHT_NO
          "dark" -> UiModeManager.MODE_NIGHT_YES
          "system" -> UiModeManager.MODE_NIGHT_AUTO
          else -> {
            Log.w(NAME, "Unknown appearance mode: $mode")
            return
          }
        }
    val uiModeManager = reactContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
    if (uiModeManager == null) {
      Log.w(NAME, "UiModeManager unavailable; night mode not persisted")
      return
    }
    uiModeManager.setApplicationNightMode(nightMode)
  }
}

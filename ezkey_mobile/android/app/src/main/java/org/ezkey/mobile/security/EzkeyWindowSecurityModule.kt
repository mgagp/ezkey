/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyWindowSecurityModule.kt
 * Description: Window protections for the pending-auth (Approve / Deny) screen.
 * Security Context: Blocks screenshots and Recents thumbnails, hides third-party overlay windows
 * and drops touches delivered through an overlay while a decision is on screen (#1154).
 * @since 2026
 */

package org.ezkey.mobile.security

import android.util.Log
import android.view.Window
import android.view.WindowManager
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil

/**
 * Toggles the sensitive-screen window protections on the current activity.
 *
 * Fail-open: when the activity is missing or a platform call is refused, the failure is logged
 * and the approval flow continues. These are defense-in-depth controls, not the authentication
 * guarantee, which rests on the Keystore signature.
 *
 * @since 2026
 */
class EzkeyWindowSecurityModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "EzkeyWindowSecurityModule"

    /**
     * Applies or clears the protections on [window]. Must run on the UI thread.
     *
     * `FLAG_SECURE` blocks screenshots, screen recording and the Recents thumbnail.
     * `setHideOverlayWindows` (API 31, our `minSdk`) hides non-system overlay windows.
     * `filterTouchesWhenObscured` on the decor view drops every touch that reaches the window
     * through another visible window, so it covers Approve and Deny without per-button wiring.
     *
     * @param window activity window to update
     * @param enabled true while the sensitive screen is focused
     * @since 2026
     */
    fun apply(window: Window, enabled: Boolean) {
      if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
      window.decorView.filterTouchesWhenObscured = enabled
      try {
        window.setHideOverlayWindows(enabled)
      } catch (error: SecurityException) {
        Log.w(NAME, "setHideOverlayWindows refused", error)
      }
    }
  }

  override fun getName(): String = NAME

  /**
   * Enables or clears the sensitive-screen protections on the current activity window.
   *
   * @param enabled true when the pending-auth screen gains focus, false when it loses it
   * @since 2026
   */
  @ReactMethod
  fun setSensitiveScreen(enabled: Boolean) {
    UiThreadUtil.runOnUiThread {
      val window = reactContext.currentActivity?.window
      if (window == null) {
        Log.w(NAME, "No current activity; sensitive-screen protections not applied")
        return@runOnUiThread
      }
      apply(window, enabled)
    }
  }
}

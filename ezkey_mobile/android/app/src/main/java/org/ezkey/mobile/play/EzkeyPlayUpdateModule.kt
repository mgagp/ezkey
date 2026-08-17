/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyPlayUpdateModule.kt
 * Description: Play Core flexible in-app update bridge. Fail-open when Play is unavailable.
 * @since 2026
 */

package org.ezkey.mobile.play

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import org.ezkey.mobile.BuildConfig

/**
 * Thin Play In-App Updates wrapper. Flexible (soft) updates only. Debug builds and any Play
 * failure resolve as "no update" so enrollment and authentication are never blocked.
 *
 * @since 2026
 */
class EzkeyPlayUpdateModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "EzkeyPlayUpdateModule"
    private const val REQUEST_CODE = 48151
  }

  private val appUpdateManager by lazy { AppUpdateManagerFactory.create(reactContext) }

  override fun getName(): String = NAME

  /**
   * Checks whether Play reports a flexible update for this package.
   *
   * @param promise map with `available` (boolean) and optional `availableVersionCode` (int)
   */
  @ReactMethod
  fun checkFlexibleUpdate(promise: Promise) {
    if (BuildConfig.DEBUG) {
      promise.resolve(unavailableMap())
      return
    }
    try {
      appUpdateManager.appUpdateInfo
          .addOnSuccessListener { info ->
            val allowed =
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            val map = Arguments.createMap()
            map.putBoolean("available", allowed)
            if (allowed) {
              map.putInt("availableVersionCode", info.availableVersionCode())
            }
            promise.resolve(map)
          }
          .addOnFailureListener { error ->
            Log.w(NAME, "Play flexible update check failed", error)
            promise.resolve(unavailableMap())
          }
    } catch (error: Exception) {
      Log.w(NAME, "Play flexible update check threw", error)
      promise.resolve(unavailableMap())
    }
  }

  /**
   * Starts the Play flexible update UI. Resolves false when Play or the activity is unavailable.
   *
   * @param promise true when Play accepted the start request
   */
  @ReactMethod
  fun startFlexibleUpdate(promise: Promise) {
    val activity = reactContext.currentActivity
    if (activity == null) {
      promise.resolve(false)
      return
    }
    try {
      appUpdateManager.appUpdateInfo
          .addOnSuccessListener { info ->
            val allowed =
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            if (!allowed) {
              promise.resolve(false)
              return@addOnSuccessListener
            }
            try {
              val started =
                  appUpdateManager.startUpdateFlowForResult(
                      info,
                      activity,
                      AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                      REQUEST_CODE,
                  )
              promise.resolve(started)
            } catch (error: Exception) {
              Log.w(NAME, "startFlexibleUpdate flow failed", error)
              promise.resolve(false)
            }
          }
          .addOnFailureListener { error ->
            Log.w(NAME, "startFlexibleUpdate info failed", error)
            promise.resolve(false)
          }
    } catch (error: Exception) {
      Log.w(NAME, "startFlexibleUpdate threw", error)
      promise.resolve(false)
    }
  }

  private fun unavailableMap() =
      Arguments.createMap().apply { putBoolean("available", false) }
}

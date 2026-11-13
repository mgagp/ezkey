/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyLocaleModule.kt
 * Description: Per-app language (Android 13+ Settings → App languages) and device language list.
 * @since 2026
 */

package org.ezkey.mobile.locale

import android.app.LocaleManager
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Reads and writes the per-app language through the framework [LocaleManager] (API 33). The
 * app's own picker stays the only control on Android 12, our `minSdk`. `locale` is in the
 * manifest `configChanges`, so a change does not recreate the activity (#1157).
 *
 * @since 2026
 */
class EzkeyLocaleModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "EzkeyLocaleModule"
  }

  override fun getName(): String = NAME

  private fun localeManager(): LocaleManager? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        reactContext.getSystemService(LocaleManager::class.java)
      } else {
        null
      }

  /**
   * Resolves the first per-app language tag, `""` when Android follows the system default, or
   * null below Android 13.
   *
   * @param promise receives the tag
   * @since 2026
   */
  @ReactMethod
  fun getApplicationLanguage(promise: Promise) {
    val manager = localeManager()
    if (manager == null) {
      promise.resolve(null)
      return
    }
    val locales = manager.applicationLocales
    promise.resolve(if (locales.isEmpty) "" else locales[0].toLanguageTag())
  }

  /**
   * Sets the per-app language; `""` returns Ezkey to the system default. No-op below Android 13.
   *
   * @param tag BCP 47 language tag, or empty
   * @param promise resolved once applied
   * @since 2026
   */
  @ReactMethod
  fun setApplicationLanguage(tag: String, promise: Promise) {
    try {
      localeManager()?.applicationLocales =
          if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
      promise.resolve(null)
    } catch (error: RuntimeException) {
      promise.reject("E_APP_LANGUAGE", error)
    }
  }

  /**
   * Resolves the phone's language list, unaffected by the per-app language.
   *
   * @param promise receives the language tags in preference order
   * @since 2026
   */
  @ReactMethod
  fun getSystemLanguages(promise: Promise) {
    val locales = localeManager()?.systemLocales ?: Resources.getSystem().configuration.locales
    val tags = Arguments.createArray()
    for (index in 0 until locales.size()) {
      tags.pushString(locales[index].toLanguageTag())
    }
    promise.resolve(tags)
  }
}

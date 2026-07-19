/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: CapturingPromise.kt
 * Description: Test Promise that records resolve/reject for instrumentation assertions.
 * @since 2026
 */

package org.ezkey.mobile.crypto

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.fail

/**
 * Minimal [Promise] implementation for calling [@ReactMethod] APIs from androidTest.
 *
 * Synchronous Keystore methods resolve on the calling thread; the latch still covers any
 * unexpected async path.
 */
class CapturingPromise : Promise {
  @Volatile var resolvedValue: Any? = null
    private set

  @Volatile var rejectedCode: String? = null
    private set

  @Volatile var rejectedMessage: String? = null
    private set

  @Volatile var rejectedThrowable: Throwable? = null
    private set

  private val latch = CountDownLatch(1)

  override fun resolve(value: Any?) {
    resolvedValue = value
    latch.countDown()
  }

  override fun reject(code: String?, message: String?) {
    rejectedCode = code
    rejectedMessage = message
    latch.countDown()
  }

  override fun reject(code: String?, throwable: Throwable?) {
    rejectedCode = code
    rejectedThrowable = throwable
    rejectedMessage = throwable?.message
    latch.countDown()
  }

  override fun reject(code: String?, message: String?, throwable: Throwable?) {
    rejectedCode = code
    rejectedMessage = message
    rejectedThrowable = throwable
    latch.countDown()
  }

  override fun reject(throwable: Throwable) {
    rejectedCode = "EUNSPECIFIED"
    rejectedThrowable = throwable
    rejectedMessage = throwable.message
    latch.countDown()
  }

  override fun reject(throwable: Throwable, userInfo: WritableMap) {
    reject(throwable)
  }

  override fun reject(code: String?, userInfo: WritableMap) {
    rejectedCode = code
    latch.countDown()
  }

  override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap) {
    reject(code, throwable)
  }

  override fun reject(code: String?, message: String?, userInfo: WritableMap) {
    reject(code, message)
  }

  override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) {
    reject(code, message, throwable)
  }

  @Deprecated("Prefer reject(code, message)")
  override fun reject(message: String) {
    rejectedCode = "EUNSPECIFIED"
    rejectedMessage = message
    latch.countDown()
  }

  fun await(timeoutSeconds: Long = 10) {
    if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
      fail("Promise did not complete within ${timeoutSeconds}s")
    }
  }

  fun requireResolved(): Any? {
    await()
    if (rejectedCode != null) {
      fail("Expected resolve but got reject code=$rejectedCode message=$rejectedMessage")
    }
    return resolvedValue
  }

  fun requireRejectedCode(): String {
    await()
    if (rejectedCode == null) {
      fail("Expected reject but got resolve value=$resolvedValue")
    }
    return rejectedCode!!
  }
}

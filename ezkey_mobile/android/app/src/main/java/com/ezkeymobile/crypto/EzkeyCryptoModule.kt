package com.ezkeymobile.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class EzkeyCryptoModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = NAME

  @ReactMethod
  fun generateRsaKeyPair(alias: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      if (keyStore.containsAlias(alias)) {
        promise.resolve(true)
        return
      }

      val keyPairGenerator =
          KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
      val builder =
          KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
              .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
              .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
              .setKeySize(KEY_SIZE)
              .setRandomizedEncryptionRequired(true)
              .setUserAuthenticationRequired(false)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setUnlockedDeviceRequired(true)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
          builder.setIsStrongBoxBacked(true)
        } catch (error: StrongBoxUnavailableException) {
          // Device does not provide StrongBox; continue without it.
        }
      }

      keyPairGenerator.initialize(builder.build())
      keyPairGenerator.generateKeyPair()

      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_KEYPAIR, error)
    }
  }

  @ReactMethod
  fun getPublicKey(alias: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val certificate = keyStore.getCertificate(alias)
      val publicKey = certificate?.publicKey as? RSAPublicKey
      if (publicKey == null) {
        promise.reject(ERROR_CODE_NOT_FOUND, "RSA public key for alias $alias not found")
        return
      }

      val encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
      promise.resolve(encoded)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_PUBLIC_KEY, error)
    }
  }

  @ReactMethod
  fun sign(alias: String, dataBase64: String, promise: Promise) {
    try {
      val payload = Base64.decode(dataBase64, Base64.NO_WRAP)
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val privateKey = keyStore.getKey(alias, null) as? PrivateKey
      if (privateKey == null) {
        promise.reject(ERROR_CODE_NOT_FOUND, "RSA private key for alias $alias not found")
        return
      }

      val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
      signature.initSign(privateKey)
      signature.update(payload)
      val signed = signature.sign()
      val encoded = Base64.encodeToString(signed, Base64.NO_WRAP)

      promise.resolve(encoded)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_SIGN, error)
    }
  }

  @ReactMethod
  fun deleteKey(alias: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      if (keyStore.containsAlias(alias)) {
        keyStore.deleteEntry(alias)
      }
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_DELETE, error)
    }
  }

  companion object {
    const val NAME = "EzkeyCryptoModule"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_SIZE = 2048
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    private const val ERROR_CODE_KEYPAIR = "EZK_KEYPAIR_ERROR"
    private const val ERROR_CODE_PUBLIC_KEY = "EZK_PUBLIC_KEY_ERROR"
    private const val ERROR_CODE_SIGN = "EZK_SIGN_ERROR"
    private const val ERROR_CODE_NOT_FOUND = "EZK_KEY_NOT_FOUND"
    private const val ERROR_CODE_DELETE = "EZK_DELETE_ERROR"
  }
}

package org.ezkey.mobile.crypto

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SealedSecretEnvelope(
    val version: Int,
    val algorithm: String,
    val ivBase64: String,
    val ciphertextBase64: String,
) {
  fun toJson(): String {
    return """
      {"version":$version,"algorithm":"$algorithm","iv":"$ivBase64","ciphertext":"$ciphertextBase64"}
    """.trimIndent()
  }

  companion object {
    const val VERSION = 1
    const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12

    private val versionRegex = Regex("\"version\"\\s*:\\s*(\\d+)")
    private val algorithmRegex = Regex("\"algorithm\"\\s*:\\s*\"([^\"]+)\"")
    private val ivRegex = Regex("\"iv\"\\s*:\\s*\"([^\"]+)\"")
    private val ciphertextRegex = Regex("\"ciphertext\"\\s*:\\s*\"([^\"]+)\"")

    fun seal(secretKey: SecretKey, logicalKey: String, plaintext: String): SealedSecretEnvelope {
      val cipher = Cipher.getInstance(ALGORITHM)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      cipher.updateAAD(logicalKey.toByteArray(StandardCharsets.UTF_8))
      val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
      val iv = cipher.iv
      require(iv != null && iv.size == IV_LENGTH_BYTES) {
        "Unexpected AES/GCM IV length: ${iv?.size ?: 0}"
      }
      return SealedSecretEnvelope(
          version = VERSION,
          algorithm = ALGORITHM,
          ivBase64 = Base64.getEncoder().encodeToString(iv),
          ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
      )
    }

    fun unseal(secretKey: SecretKey, logicalKey: String, envelope: SealedSecretEnvelope): String {
      require(envelope.version == VERSION) {
        "Unsupported sealed secret version: ${envelope.version}"
      }
      require(envelope.algorithm == ALGORITHM) {
        "Unsupported sealed secret algorithm: ${envelope.algorithm}"
      }

      val cipher = Cipher.getInstance(ALGORITHM)
      val iv = Base64.getDecoder().decode(envelope.ivBase64)
      val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
      cipher.updateAAD(logicalKey.toByteArray(StandardCharsets.UTF_8))
      val plaintext = cipher.doFinal(ciphertext)
      return String(plaintext, StandardCharsets.UTF_8)
    }

    fun fromJson(json: String): SealedSecretEnvelope {
      val version = versionRegex.find(json)?.groupValues?.get(1)?.toIntOrNull()
          ?: throw IllegalArgumentException("Missing or invalid sealed secret version")
      val algorithm = algorithmRegex.find(json)?.groupValues?.get(1)
          ?: throw IllegalArgumentException("Missing sealed secret algorithm")
      val ivBase64 = ivRegex.find(json)?.groupValues?.get(1)
          ?: throw IllegalArgumentException("Missing sealed secret IV")
      val ciphertextBase64 = ciphertextRegex.find(json)?.groupValues?.get(1)
          ?: throw IllegalArgumentException("Missing sealed secret ciphertext")
      return SealedSecretEnvelope(version, algorithm, ivBase64, ciphertextBase64)
    }
  }
}

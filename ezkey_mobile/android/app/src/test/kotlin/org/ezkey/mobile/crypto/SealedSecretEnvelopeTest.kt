package org.ezkey.mobile.crypto

import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("SealedSecretEnvelope")
class SealedSecretEnvelopeTest {

  private fun generateAesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  @Test
  @DisplayName("seals and unseals a plaintext round-trip")
  fun sealsAndUnsealsRoundTrip() {
    val secretKey = generateAesKey()
    val envelope = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")

    val plaintext = SealedSecretEnvelope.unseal(secretKey, "logical-key", envelope)

    assertEquals("sensitive-value", plaintext)
  }

  @Test
  @DisplayName("uses a fresh IV for each seal of the same plaintext")
  fun usesFreshIvForEachSeal() {
    val secretKey = generateAesKey()

    val first = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")
    val second = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")

    assertNotEquals(first.ivBase64, second.ivBase64)
    assertNotEquals(first.ciphertextBase64, second.ciphertextBase64)
  }

  @Test
  @DisplayName("fails to unseal when logical key changes because AAD binding is enforced")
  fun failsWhenLogicalKeyChanges() {
    val secretKey = generateAesKey()
    val envelope = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")

    assertThrows<Exception> {
      SealedSecretEnvelope.unseal(secretKey, "other-logical-key", envelope)
    }
  }

  @Test
  @DisplayName("fails to unseal when ciphertext is tampered")
  fun failsWhenCiphertextIsTampered() {
    val secretKey = generateAesKey()
    val envelope = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")
    val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
    ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 0x01).toByte()
    val tamperedEnvelope =
        envelope.copy(ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext))

    assertThrows<Exception> {
      SealedSecretEnvelope.unseal(secretKey, "logical-key", tamperedEnvelope)
    }
  }

  @Test
  @DisplayName("fails to unseal when IV is tampered")
  fun failsWhenIvIsTampered() {
    val secretKey = generateAesKey()
    val envelope = SealedSecretEnvelope.seal(secretKey, "logical-key", "sensitive-value")
    val iv = Base64.getDecoder().decode(envelope.ivBase64)
    iv[0] = (iv[0].toInt() xor 0x01).toByte()
    val tamperedEnvelope = envelope.copy(ivBase64 = Base64.getEncoder().encodeToString(iv))

    assertThrows<Exception> {
      SealedSecretEnvelope.unseal(secretKey, "logical-key", tamperedEnvelope)
    }
  }

  @Test
  @DisplayName("serializes and parses JSON envelopes")
  fun serializesAndParsesJson() {
    val envelope =
        SealedSecretEnvelope(
            version = SealedSecretEnvelope.VERSION,
            algorithm = SealedSecretEnvelope.ALGORITHM,
            ivBase64 = "aXY=",
            ciphertextBase64 = "Y2lwaGVydGV4dA==",
        )

    val parsed = SealedSecretEnvelope.fromJson(envelope.toJson())

    assertEquals(envelope, parsed)
  }

  @Test
  @DisplayName("rejects unsupported versions")
  fun rejectsUnsupportedVersions() {
    val secretKey = generateAesKey()
    val envelope =
        SealedSecretEnvelope(
            version = 99,
            algorithm = SealedSecretEnvelope.ALGORITHM,
            ivBase64 = "aXY=",
            ciphertextBase64 = "Y2lwaGVydGV4dA==",
        )

    val error =
        assertThrows<IllegalArgumentException> {
          SealedSecretEnvelope.unseal(secretKey, "logical-key", envelope)
        }
    assertTrue(error.message!!.contains("Unsupported sealed secret version"))
  }

  @Test
  @DisplayName("rejects unsupported algorithms")
  fun rejectsUnsupportedAlgorithms() {
    val secretKey = generateAesKey()
    val envelope =
        SealedSecretEnvelope(
            version = SealedSecretEnvelope.VERSION,
            algorithm = "AES/CBC/PKCS7Padding",
            ivBase64 = "aXY=",
            ciphertextBase64 = "Y2lwaGVydGV4dA==",
        )

    val error =
        assertThrows<IllegalArgumentException> {
          SealedSecretEnvelope.unseal(secretKey, "logical-key", envelope)
        }
    assertTrue(error.message!!.contains("Unsupported sealed secret algorithm"))
  }
}

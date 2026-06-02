package com.tubetoast.tether.security

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-format compatibility for [wrapInX509Spki].
 *
 * Apple-side `DeviceKeyPair` extracts a 65-byte uncompressed EC point via
 * `SecKeyCopyExternalRepresentation` (ANSI X9.63 `0x04 || X || Y`) and prepends
 * [P256_SPKI_PREFIX] before sending it on the wire. The JVM peer parses the resulting
 * 91 bytes via `KeyFactory("EC").generatePublic(X509EncodedKeySpec(...))`.
 *
 * Apple's runtime path is not callable from JVM tests, so we substitute the X9.63 point
 * by extracting it from a freshly-generated JVM P-256 key (the bytes are wire-equivalent).
 * If [P256_SPKI_PREFIX] or [wrapInX509Spki] ever drift from the canonical SubjectPublicKeyInfo
 * shape, JCE will reject the result.
 */
class X509SubjectPublicKeyInfoCompatTest {
    @Test
    fun `wrapped raw point parses as X509 EC public key via JCE`() {
        val keyPair = generateP256KeyPair()
        val rawPoint = extractUncompressedPoint(keyPair.public as ECPublicKey)

        val wrapped = wrapInX509Spki(rawPoint)

        val parsed = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(wrapped))
        assertEquals(keyPair.public, parsed, "wrapped SPKI must round-trip through JCE to the original public key")
    }

    @Test
    fun `wrapped SPKI is bit-identical to the SPKI JCE emits for the same key`() {
        val keyPair = generateP256KeyPair()
        val jceSpki = keyPair.public.encoded
        val rawPoint = extractUncompressedPoint(keyPair.public as ECPublicKey)

        val wrapped = wrapInX509Spki(rawPoint)

        assertTrue(
            jceSpki.contentEquals(wrapped),
            "wrapInX509Spki must produce byte-identical SPKI to JCE's own encoding",
        )
    }

    private fun generateP256KeyPair() = KeyPairGenerator
        .getInstance("EC")
        .apply { initialize(256) }
        .generateKeyPair()

    private fun extractUncompressedPoint(key: ECPublicKey): ByteArray {
        val w = key.w
        val x = unsignedBytes(w.affineX.toByteArray(), 32)
        val y = unsignedBytes(w.affineY.toByteArray(), 32)
        return byteArrayOf(0x04) + x + y
    }

    private fun unsignedBytes(signed: ByteArray, size: Int): ByteArray {
        val trimmed = if (signed.size > size && signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
        if (trimmed.size == size) return trimmed
        val padded = ByteArray(size)
        System.arraycopy(trimmed, 0, padded, size - trimmed.size, trimmed.size)
        return padded
    }
}

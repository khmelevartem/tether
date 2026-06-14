package com.tubetoast.tether.security

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the cross-platform SPKI/fingerprint parity invariant.
 *
 * Apple-side `DeviceKeyPair` extracts a 65-byte uncompressed EC point via
 * `SecKeyCopyExternalRepresentation` (ANSI X9.63 `0x04 || X || Y`) and wraps it with
 * [wrapInX509Spki]; the JVM path uses `KeyPairGenerator("EC").public.encoded`. For the same
 * key both must produce byte-identical SPKI, hence identical fingerprints.
 *
 * Apple's runtime path is not callable from a JVM test, so the X9.63 point is taken from a
 * freshly generated JVM P-256 key — the bytes are wire-equivalent. If [wrapInX509Spki] ever
 * drifts from the canonical SubjectPublicKeyInfo shape, these break.
 */
class X509SubjectPublicKeyInfoTest {
    @Test
    fun `wrapped SPKI is byte-identical to the JVM canonical encoding`() {
        repeat(SAMPLE_SIZE) {
            val keyPair = generateP256KeyPair()
            val rawPoint = extractUncompressedPoint(keyPair)
            val wrapped = wrapInX509Spki(rawPoint)
            assertTrue(
                keyPair.public.encoded.contentEquals(wrapped),
                "wrapInX509Spki must produce byte-identical SPKI to JCE's own encoding",
            )
        }
    }

    @Test
    fun `wrapped SPKI yields the same fingerprint as the JVM encoding`() {
        repeat(SAMPLE_SIZE) {
            val keyPair = generateP256KeyPair()
            val rawPoint = extractUncompressedPoint(keyPair)
            assertEquals(
                deviceIdFromPublicKey(keyPair.public.encoded),
                deviceIdFromPublicKey(wrapInX509Spki(rawPoint)),
                "same key must produce the same fingerprint regardless of SPKI construction path",
            )
        }
    }

    @Test
    fun `common-wrapped SPKI parses back to the original public key`() {
        repeat(SAMPLE_SIZE) {
            val keyPair = generateP256KeyPair()
            val rawPoint = extractUncompressedPoint(keyPair)
            val parsed = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(wrapInX509Spki(rawPoint)))
            assertEquals(keyPair.public, parsed, "wrapped SPKI must round-trip through JCE to the original public key")
        }
    }

    private fun generateP256KeyPair(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    private fun extractUncompressedPoint(keyPair: KeyPair): ByteArray {
        val w = (keyPair.public as ECPublicKey).w
        val x = fixedBytes(w.affineX, 32)
        val y = fixedBytes(w.affineY, 32)
        return byteArrayOf(0x04) + x + y
    }

    /** Converts a BigInteger coordinate to an unsigned fixed-width big-endian byte array. */
    private fun fixedBytes(n: BigInteger, size: Int): ByteArray {
        val signed = n.toByteArray()
        // BigInteger.toByteArray() prepends a 0x00 sign byte when the high bit is set.
        val trimmed = if (signed.size > size && signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
        if (trimmed.size == size) return trimmed
        val padded = ByteArray(size)
        System.arraycopy(trimmed, 0, padded, size - trimmed.size, trimmed.size)
        return padded
    }

    private companion object {
        const val SAMPLE_SIZE = 25
    }
}

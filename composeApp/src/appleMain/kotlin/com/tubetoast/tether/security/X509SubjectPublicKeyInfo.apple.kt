package com.tubetoast.tether.security

// Fixed 26-byte DER prefix for a P-256 (prime256v1) SubjectPublicKeyInfo:
//   SEQUENCE { AlgorithmIdentifier { OID ecPublicKey, OID prime256v1 }, BIT STRING uncompressed-point }
// Prepending this to the raw 65-byte uncompressed point yields 91-byte X.509 SPKI
// compatible with JVM KeyFactory("EC").generatePublic(X509EncodedKeySpec(bytes)).
private val P256_SPKI_PREFIX = byteArrayOf(
    0x30,
    0x59, // SEQUENCE, 89 bytes
    0x30,
    0x13, // SEQUENCE (AlgorithmIdentifier), 19 bytes
    0x06,
    0x07,
    0x2a,
    0x86.toByte(),
    0x48,
    0xce.toByte(),
    0x3d,
    0x02,
    0x01, // OID id-ecPublicKey
    0x06,
    0x08,
    0x2a,
    0x86.toByte(),
    0x48,
    0xce.toByte(),
    0x3d,
    0x03,
    0x01,
    0x07, // OID prime256v1
    0x03,
    0x42,
    0x00, // BIT STRING, 66 bytes, 0 unused bits
)

internal fun wrapInX509Spki(rawUncompressedPoint: ByteArray): ByteArray {
    require(rawUncompressedPoint.size == 65 && rawUncompressedPoint[0] == 0x04.toByte()) {
        "Expected 65-byte uncompressed EC point (0x04 || X || Y), got size=${rawUncompressedPoint.size}"
    }
    return P256_SPKI_PREFIX + rawUncompressedPoint
}

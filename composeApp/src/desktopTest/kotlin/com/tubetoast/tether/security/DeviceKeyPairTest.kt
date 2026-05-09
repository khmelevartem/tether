package com.tubetoast.tether.security

import java.nio.file.Files
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertTrue

class DeviceKeyPairTest {
    @Test
    fun `public key is stable across instances`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            val first = DeviceKeyPair(configDir).publicKey
            val second = DeviceKeyPair(configDir).publicKey
            assertTrue(first.contentEquals(second), "public key must be identical after reload")
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `generates non-empty EC key`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            val keyPair = DeviceKeyPair(configDir)
            assertTrue(keyPair.publicKey.isNotEmpty())
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyPair.publicKey))
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `corrupted key file is regenerated on next load`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            val original = DeviceKeyPair(configDir).publicKey

            val publicKeyFile = configDir.resolve("device_public.key")
            publicKeyFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02))

            val regenerated = DeviceKeyPair(configDir).publicKey
            assertTrue(regenerated.isNotEmpty(), "regenerated key must be non-empty")
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(regenerated))
        } finally {
            configDir.deleteRecursively()
        }
    }
}

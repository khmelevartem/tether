package com.tubetoast.tether.security

import java.nio.file.Files
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertFailsWith
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
    fun `both files corrupted are regenerated on next load`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            DeviceKeyPair(configDir)

            configDir.resolve("device_public.key").writeBytes(byteArrayOf(0x00, 0x01, 0x02))
            configDir.resolve("device_private.key").writeBytes(byteArrayOf(0x00, 0x01, 0x02))

            val regenerated = DeviceKeyPair(configDir).publicKey
            assertTrue(regenerated.isNotEmpty(), "regenerated key must be non-empty")
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(regenerated))
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `partial corruption of only public key refuses to rotate identity`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            DeviceKeyPair(configDir)
            val privateKeyFile = configDir.resolve("device_private.key")
            val privateBefore = privateKeyFile.readBytes()

            configDir.resolve("device_public.key").writeBytes(byteArrayOf(0x00, 0x01, 0x02))

            assertFailsWith<IllegalStateException> { DeviceKeyPair(configDir) }
            assertTrue(privateKeyFile.exists(), "private key must NOT be deleted on partial corruption")
            assertTrue(privateBefore.contentEquals(privateKeyFile.readBytes()), "private key bytes must be untouched")
        } finally {
            configDir.deleteRecursively()
        }
    }

    @Test
    fun `missing public file with valid private file refuses to rotate identity`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            DeviceKeyPair(configDir)
            configDir.resolve("device_public.key").delete()

            assertFailsWith<IllegalStateException> { DeviceKeyPair(configDir) }
            assertTrue(configDir.resolve("device_private.key").exists(), "private key must NOT be deleted")
        } finally {
            configDir.deleteRecursively()
        }
    }
}

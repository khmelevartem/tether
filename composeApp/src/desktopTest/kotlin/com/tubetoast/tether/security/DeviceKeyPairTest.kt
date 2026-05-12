package com.tubetoast.tether.security

import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `device_private_key has POSIX 600 permissions on supporting filesystems`() {
        val configDir = Files.createTempDirectory("tether-keypair-test").toFile()
        try {
            DeviceKeyPair(configDir)
            val privateKeyPath = configDir.resolve("device_private.key").toPath()
            val view = Files.getFileAttributeView(privateKeyPath, PosixFileAttributeView::class.java)
                ?: return // non-POSIX FS (Windows) — restriction is documented no-op
            val perms = view.readAttributes().permissions()
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                perms,
                "device_private.key must be readable/writable only by owner (POSIX 600)",
            )
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

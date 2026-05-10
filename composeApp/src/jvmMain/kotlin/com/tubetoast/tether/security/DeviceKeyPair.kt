package com.tubetoast.tether.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec

class DeviceKeyPair(
    private val configDir: File = File(System.getProperty("user.home"), ".config/tether"),
) {
    val publicKey: ByteArray

    init {
        val publicKeyFile = File(configDir, "device_public.key")
        val privateKeyFile = File(configDir, "device_private.key")

        publicKey = if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                loadPublicKey(publicKeyFile)
            } catch (e: Exception) {
                System.err.println("WARN: device key corrupted, regenerating — ${e.message}")
                publicKeyFile.delete()
                privateKeyFile.delete()
                generateAndPersist(publicKeyFile, privateKeyFile)
            }
        } else {
            generateAndPersist(publicKeyFile, privateKeyFile)
        }
    }

    private fun loadPublicKey(publicKeyFile: File): ByteArray = publicKeyFile.readBytes().also { encoded ->
        validatePublicKeyBytes(encoded)
    }

    private fun validatePublicKeyBytes(encoded: ByteArray) {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun generateAndPersist(
        publicKeyFile: File,
        privateKeyFile: File,
    ): ByteArray {
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw IOException("Failed to create config directory: $configDir")
        }
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        val keyPair = generator.generateKeyPair()
        publicKeyFile.writeBytes(keyPair.public.encoded)
        privateKeyFile.writeBytes(keyPair.private.encoded)
        restrictToOwner(privateKeyFile)
        return keyPair.public.encoded
    }

    private fun restrictToOwner(file: File) {
        val view = Files.getFileAttributeView(
            file.toPath(),
            java.nio.file.attribute.PosixFileAttributeView::class.java,
        ) ?: return // non-POSIX filesystem (e.g. Android internal storage already per-app, Windows)
        view.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }
}

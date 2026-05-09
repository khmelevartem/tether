package com.tubetoast.tether.security

import java.io.File
import java.io.IOException
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
        return keyPair.public.encoded
    }
}

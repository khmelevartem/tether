package com.tubetoast.tether.security

import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class DeviceKeyPair(
    private val configDir: File = File(System.getProperty("user.home"), ".config/tether"),
) {
    val publicKey: ByteArray

    init {
        val publicKeyFile = File(configDir, "device_public.key")
        val privateKeyFile = File(configDir, "device_private.key")

        publicKey = if (publicKeyFile.exists() && privateKeyFile.exists()) {
            loadPublicKey(publicKeyFile)
        } else {
            generateAndPersist(publicKeyFile, privateKeyFile)
        }
    }

    private fun loadPublicKey(publicKeyFile: File): ByteArray = publicKeyFile.readBytes().also { encoded ->
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun generateAndPersist(
        publicKeyFile: File,
        privateKeyFile: File,
    ): ByteArray {
        configDir.mkdirs()
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        val keyPair = generator.generateKeyPair()
        publicKeyFile.writeBytes(keyPair.public.encoded)
        privateKeyFile.writeBytes(keyPair.private.encoded)
        return keyPair.public.encoded
    }
}

@Suppress("unused")
private fun loadPrivateKey(privateKeyFile: File) {
    KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
}

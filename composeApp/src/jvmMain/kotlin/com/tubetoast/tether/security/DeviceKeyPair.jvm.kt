package com.tubetoast.tether.security

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

private val log = KydraLog.withTag(default = "DeviceKeyPair")

actual class DeviceKeyPair(
    private val configDir: File = File(System.getProperty("user.home"), ".config/tether"),
) {
    actual val publicKey: ByteArray

    init {
        val publicKeyFile = File(configDir, "device_public.key")
        val privateKeyFile = File(configDir, "device_private.key")

        publicKey = when {
            !publicKeyFile.exists() && !privateKeyFile.exists() ->
                generateAndPersist(publicKeyFile, privateKeyFile)

            publicKeyFile.exists() && privateKeyFile.exists() ->
                loadOrRegenerate(publicKeyFile, privateKeyFile)

            else ->
                throw IllegalStateException(
                    "Inconsistent device key state in $configDir: " +
                        "${publicKeyFile.name}=${publicKeyFile.exists()} " +
                        "${privateKeyFile.name}=${privateKeyFile.exists()}. " +
                        "Refusing to silently rotate identity. Remove both files to regenerate.",
                )
        }
    }

    private fun loadOrRegenerate(publicKeyFile: File, privateKeyFile: File): ByteArray {
        val publicValid = runCatching { validatePublicKeyBytes(publicKeyFile.readBytes()) }.isSuccess
        val privateValid = runCatching { validatePrivateKeyBytes(privateKeyFile.readBytes()) }.isSuccess
        return when {
            publicValid && privateValid -> publicKeyFile.readBytes()
            !publicValid && !privateValid -> {
                log.warn { "device key pair corrupted, regenerating in $configDir" }
                publicKeyFile.delete()
                privateKeyFile.delete()
                generateAndPersist(publicKeyFile, privateKeyFile)
            }
            else ->
                throw IllegalStateException(
                    "Partial corruption of device key pair in $configDir " +
                        "(public valid=$publicValid, private valid=$privateValid). " +
                        "Refusing to discard the surviving half. " +
                        "Restore from backup or remove both files to regenerate identity.",
                )
        }
    }

    private fun validatePublicKeyBytes(encoded: ByteArray) {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun validatePrivateKeyBytes(encoded: ByteArray) {
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))
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
        // Restrict the empty file before the secret lands, so the key never exists with default permissions.
        privateKeyFile.createNewFile()
        restrictToOwner(privateKeyFile)
        privateKeyFile.writeBytes(keyPair.private.encoded)
        return keyPair.public.encoded
    }

    private fun restrictToOwner(file: File) {
        val posixView = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)
        if (posixView != null) {
            posixView.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
            return
        }
        restrictToOwnerAcl(file)
    }

    private fun restrictToOwnerAcl(file: File) {
        val aclView = Files.getFileAttributeView(file.toPath(), AclFileAttributeView::class.java)
        if (aclView == null) {
            log.warn { "cannot restrict ${file.name}: filesystem supports neither POSIX nor ACL permissions" }
            return
        }
        val ownerOnly = AclEntry
            .newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(Files.getOwner(file.toPath()))
            .setPermissions(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.DELETE,
                AclEntryPermission.SYNCHRONIZE,
            ).build()
        aclView.acl = listOf(ownerOnly)
    }
}

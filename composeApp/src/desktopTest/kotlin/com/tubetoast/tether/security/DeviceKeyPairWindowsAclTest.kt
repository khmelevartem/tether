package com.tubetoast.tether.security

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceKeyPairWindowsAclTest {
    @Test
    fun `device_private_key ACL grants access to the owner only on Windows`() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("win"))

        val configDir = Files.createTempDirectory("tether-keypair-acl-test").toFile()
        try {
            DeviceKeyPair(configDir)
            val privateKeyPath = configDir.resolve("device_private.key").toPath()
            val aclView = Files.getFileAttributeView(privateKeyPath, AclFileAttributeView::class.java)
            assertTrue(aclView != null, "Windows NTFS must expose an AclFileAttributeView")

            val owner = Files.getOwner(privateKeyPath)
            val acl = aclView.acl
            assertTrue(acl.isNotEmpty(), "ACL must not be empty")
            acl.forEach { entry ->
                assertEquals(
                    owner,
                    entry.principal(),
                    "every ACL entry must belong to the file owner — no Everyone / BUILTIN\\Users / Authenticated Users",
                )
                assertEquals(
                    AclEntryType.ALLOW,
                    entry.type(),
                    "owner entry must grant access, not deny it",
                )
            }
        } finally {
            configDir.deleteRecursively()
        }
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFBooleanFalse
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecPrivateKeyAttrs

/**
 * In-process P-256 keypair store for tests — generates one keypair on first [generatePrivateKey]
 * call and keeps it in memory. Multiple [DeviceKeyPair] instances sharing the same store instance
 * see the same key.
 */
internal class InMemoryKeychainStore : KeychainStore {
    private var stored: SecKeyRef? = null

    // CFRetain balances the CFRelease the caller (DeviceKeyPair) performs after extracting bytes.
    override fun findPrivateKey(): SecKeyRef? = stored?.also { CFRetain(it) }

    override fun generatePrivateKey(): SecKeyRef {
        val privateAttrs = buildQuery {
            put(kSecAttrIsPermanent, kCFBooleanFalse)
        }
        val attrs = buildQuery {
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrKeySizeInBits, NSNumber(int = 256))
            put(kSecPrivateKeyAttrs, CFBridgingRelease(privateAttrs))
        }
        val key = memScoped {
            val errorRef = alloc<CFErrorRefVar>()
            val k = SecKeyCreateRandomKey(attrs, errorRef.ptr)
            CFRelease(attrs)
            k ?: throw IllegalStateException("InMemoryKeychainStore: SecKeyCreateRandomKey failed")
        }
        // Retain an extra reference for the store; the caller will CFRelease its copy.
        CFRetain(key)
        stored = key
        return key
    }

    override fun extractPublicKeyBytes(key: SecKeyRef): ByteArray? =
        Keychain("unused").extractPublicKeyBytes(key)

    override fun deleteEntry() {
        stored?.let { CFRelease(it) }
        stored = null
    }
}

/**
 * Wraps an [InMemoryKeychainStore] and returns null from [extractPublicKeyBytes] for the first
 * [failCount] calls, then delegates to the real implementation. Used to simulate transient
 * key-extraction failures.
 *
 * [callLog] records the sequence of store interactions as string tokens so tests can assert
 * the exact control-flow path taken through the production code:
 * - `"find:hit"` / `"find:miss"` — [findPrivateKey] returned non-null / null
 * - `"extract:fail"` / `"extract:ok"` — [extractPublicKeyBytes] returned null / non-null
 * - `"delete"` — [deleteEntry] was called
 * - `"generate"` — [generatePrivateKey] was called
 */
internal class FlakyExtractKeychainStore(
    private val failCount: Int,
    private val delegate: InMemoryKeychainStore = InMemoryKeychainStore(),
) : KeychainStore by delegate {
    private var failsRemaining = failCount
    val callLog = mutableListOf<String>()

    override fun findPrivateKey(): SecKeyRef? =
        delegate.findPrivateKey().also { result ->
            callLog += if (result != null) "find:hit" else "find:miss"
        }

    override fun generatePrivateKey(): SecKeyRef =
        delegate.generatePrivateKey().also { callLog += "generate" }

    override fun extractPublicKeyBytes(key: SecKeyRef): ByteArray? = if (failsRemaining > 0) {
        failsRemaining--
        callLog += "extract:fail"
        null
    } else {
        delegate.extractPublicKeyBytes(key).also { callLog += "extract:ok" }
    }

    override fun deleteEntry() {
        callLog += "delete"
        delegate.deleteEntry()
    }
}

/** Fake that always throws [IllegalStateException] from [findPrivateKey]. */
internal class UnavailableKeychainStore : KeychainStore {
    override fun findPrivateKey(): SecKeyRef? =
        throw IllegalStateException(
            "Keychain unavailable on this platform — DeviceKeyPair requires a signed app bundle",
        )

    override fun generatePrivateKey(): SecKeyRef =
        throw IllegalStateException("Keychain unavailable")

    override fun extractPublicKeyBytes(key: SecKeyRef): ByteArray? =
        throw IllegalStateException("Keychain unavailable")

    override fun deleteEntry() =
        throw IllegalStateException("Keychain unavailable")
}

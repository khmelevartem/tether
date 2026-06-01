@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.tubetoast.tether.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.value
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFBooleanFalse
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
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
        val privateAttrs = buildInMemoryQuery {
            put(kSecAttrIsPermanent, kCFBooleanFalse)
        }
        val attrs = buildInMemoryQuery {
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
        stored = null
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

private fun buildInMemoryQuery(block: InMemoryQueryBuilder.() -> Unit): platform.CoreFoundation.CFDictionaryRef {
    val dict = NSMutableDictionary()
    InMemoryQueryBuilder(dict).block()
    return CFBridgingRetain(dict) as platform.CoreFoundation.CFDictionaryRef
}

private class InMemoryQueryBuilder(
    private val dict: NSMutableDictionary,
) {
    fun put(key: kotlinx.cinterop.CPointer<*>?, value: Any?) {
        if (key == null || value == null) return
        val nsKey = interpretObjCPointer<NSString>(key.rawValue)
        val nsValue: Any = when (value) {
            is kotlinx.cinterop.CPointer<*> ->
                interpretObjCPointer<platform.darwin.NSObject>(value.rawValue)
            else -> value
        }
        dict.setObject(nsValue, forKey = nsKey)
    }
}

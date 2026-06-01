@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.tubetoast.tether.security

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.dataWithBytes
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import platform.Security.kSecUseDataProtectionKeychain

/**
 * Seam over the system Keychain for P-256 key storage. Package-internal; injected into
 * [DeviceKeyPair] so tests can supply an in-memory substitute without a real app identity.
 *
 * [findPrivateKey] returns null when no key is stored yet (first-launch happy path).
 * It throws [IllegalStateException] when the Keychain itself is unavailable — e.g. a
 * headless binary without a bundle ID or entitlements.
 */
internal interface KeychainStore {
    fun findPrivateKey(): SecKeyRef?

    fun generatePrivateKey(): SecKeyRef

    fun extractPublicKeyBytes(key: SecKeyRef): ByteArray?

    fun deleteEntry()

    companion object {
        const val DEFAULT_DEVICE_KEY_TAG = "com.tubetoast.tether.devicekey"

        fun real(applicationTag: String): KeychainStore = Keychain(applicationTag)
    }
}

internal class Keychain(
    private val applicationTag: String,
) : KeychainStore {
    override fun generatePrivateKey(): SecKeyRef {
        val privateAttrs = buildQuery {
            put(kSecAttrIsPermanent, kCFBooleanTrue)
            put(kSecAttrApplicationTag, tagData())
            put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val attrs = buildQuery {
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrKeySizeInBits, NSNumber(int = 256))
            put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
            put(kSecPrivateKeyAttrs, CFBridgingRelease(privateAttrs))
        }
        return memScoped {
            val errorRef = alloc<CFErrorRefVar>()
            val key = SecKeyCreateRandomKey(attrs, errorRef.ptr)
            CFRelease(attrs)
            if (key == null) {
                val description = errorDescription(errorRef.value)
                errorRef.value?.let { CFRelease(it) }
                throw IllegalStateException("SecKeyCreateRandomKey failed: $description")
            }
            key
        }
    }

    override fun findPrivateKey(): SecKeyRef? {
        val query = buildQuery {
            put(kSecClass, kSecClassKey)
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrApplicationTag, tagData())
            put(kSecReturnRef, kCFBooleanTrue)
            put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }
        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, resultRef.ptr)
            CFRelease(query)
            when (status) {
                errSecSuccess -> resultRef.value as? SecKeyRef
                errSecItemNotFound -> null
                errSecNotAvailable -> throw IllegalStateException(
                    "Keychain unavailable on this platform — DeviceKeyPair requires a signed app bundle",
                )
                else -> throw IllegalStateException("SecItemCopyMatching failed: OSStatus=$status")
            }
        }
    }

    override fun deleteEntry() {
        val query = buildQuery {
            put(kSecClass, kSecClassKey)
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrApplicationTag, tagData())
            put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
        }
        SecItemDelete(query)
        CFRelease(query)
    }

    override fun extractPublicKeyBytes(key: SecKeyRef): ByteArray? {
        val publicKey = SecKeyCopyPublicKey(key) ?: return null
        return memScoped {
            val errorRef = alloc<CFErrorRefVar>()
            val cfData = SecKeyCopyExternalRepresentation(publicKey, errorRef.ptr)
            CFRelease(publicKey)
            if (cfData == null) {
                errorRef.value?.let { CFRelease(it) }
                null
            } else {
                val length = CFDataGetLength(cfData).toInt()
                val result = cfDataToByteArray(cfData, length)
                CFRelease(cfData)
                result
            }
        }
    }

    /**
     * Reconstructs a public SecKeyRef from a 65-byte uncompressed EC point.
     * Returns null if the bytes don't represent a valid P-256 public key.
     */
    fun publicKeyFromRawPoint(rawPoint: ByteArray): SecKeyRef? {
        val nsData = rawPoint.toNSData() ?: return null
        val attrs = buildQuery {
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrKeyClass, kSecAttrKeyClassPublic)
            put(kSecAttrKeySizeInBits, NSNumber(int = 256))
        }
        return memScoped {
            val errorRef = alloc<CFErrorRefVar>()
            val cfData = CFBridgingRetain(nsData) as platform.CoreFoundation.CFDataRef
            val key = SecKeyCreateWithData(cfData, attrs, errorRef.ptr)
            CFRelease(cfData)
            CFRelease(attrs)
            if (key == null) errorRef.value?.let { CFRelease(it) }
            key
        }
    }

    private fun tagData(): NSData = applicationTag.encodeToByteArray().toNSData()!!
}

/** The caller owns the returned ref. */
private fun buildQuery(block: QueryBuilder.() -> Unit): CFDictionaryRef {
    val dict = NSMutableDictionary()
    QueryBuilder(dict).block()
    return CFBridgingRetain(dict) as CFDictionaryRef
}

private class QueryBuilder(
    private val dict: NSMutableDictionary,
) {
    fun put(key: kotlinx.cinterop.CPointer<*>?, value: Any?) {
        if (key == null || value == null) return
        // Toll-free bridge: CF string constants share memory layout with NSString.
        val nsKey = interpretObjCPointer<NSString>(key.rawValue)
        val nsValue: Any = when (value) {
            is kotlinx.cinterop.CPointer<*> ->
                // Toll-free bridge: CFBooleanRef, CFStringRef, etc. bridge to ObjC objects.
                interpretObjCPointer<platform.darwin.NSObject>(value.rawValue)
            else -> value
        }
        dict.setObject(nsValue, forKey = nsKey)
    }
}

private fun cfDataToByteArray(cfData: platform.CoreFoundation.CFDataRef, length: Int): ByteArray? {
    val rawPtr = CFDataGetBytePtr(cfData) ?: return null
    val bytes = interpretCPointer<ByteVar>(rawPtr.rawValue) ?: return null
    return bytes.readBytes(length)
}

internal fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

private fun errorDescription(cfError: CFErrorRef?): String {
    if (cfError == null) return "unknown error"
    val cfString = CFErrorCopyDescription(cfError) ?: return "unknown error"
    return (CFBridgingRelease(cfString) as? NSString)?.toString() ?: "unknown error"
}

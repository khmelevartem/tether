@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.tubetoast.tether.security

import com.tubetoast.tether.util.toNSData
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import platform.Security.kSecUseDataProtectionKeychain
import platform.darwin.NSObject
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Keychain")

/**
 * [findPrivateKey] returns null when no key is stored yet.
 * Throws [IllegalStateException] when the Keychain is unavailable — e.g. a headless binary
 * without a bundle ID or entitlements.
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
        }
        val attrs = buildQuery {
            put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            put(kSecAttrKeySizeInBits, NSNumber(int = 256))
            put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
            put(kSecPrivateKeyAttrs, privateAttrs)
        }
        CFRelease(privateAttrs)
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
                    "Keychain service unavailable (errSecNotAvailable). Restart and retry, or verify the system Keychain is running.",
                )
                errSecMissingEntitlement -> throw IllegalStateException(
                    "Keychain entitlement missing — DeviceKeyPair requires a signed app bundle with keychain-access-groups",
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
        val status = SecItemDelete(query)
        CFRelease(query)
        // A non-deleted entry will collide with the next generate via errSecDuplicateItem;
        // log it so the resulting startup crash is debuggable.
        if (status != errSecSuccess && status != errSecItemNotFound) {
            log.warn {
                "SecItemDelete failed: OSStatus=$status; subsequent regenerate may fail with errSecDuplicateItem"
            }
        }
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

    private fun tagData(): NSData = applicationTag.encodeToByteArray().toNSData()!!
}

/** The caller owns the returned ref. */
internal fun buildQuery(block: QueryBuilder.() -> Unit): CFDictionaryRef {
    val dict = CFDictionaryCreateMutable(
        null,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )!!
    QueryBuilder(dict).block()
    return dict
}

internal class QueryBuilder(
    private val dict: CFMutableDictionaryRef,
) {
    fun put(key: CPointer<*>?, value: CPointer<*>?) {
        if (key == null || value == null) return
        CFDictionarySetValue(dict, key, value)
    }

    fun put(key: CPointer<*>?, value: NSObject?) {
        if (key == null || value == null) return
        val cfValue = CFBridgingRetain(value)
        CFDictionarySetValue(dict, key, cfValue)
        // CFDictionarySetValue retains; release the +1 from CFBridgingRetain.
        CFRelease(cfValue)
    }
}

private fun cfDataToByteArray(cfData: platform.CoreFoundation.CFDataRef, length: Int): ByteArray? {
    val rawPtr = CFDataGetBytePtr(cfData) ?: return null
    val bytes = interpretCPointer<ByteVar>(rawPtr.rawValue) ?: return null
    return bytes.readBytes(length)
}

private fun errorDescription(cfError: CFErrorRef?): String {
    if (cfError == null) return "unknown error"
    val cfString = CFErrorCopyDescription(cfError) ?: return "unknown error"
    return (CFBridgingRelease(cfString) as? NSString)?.toString() ?: "unknown error"
}

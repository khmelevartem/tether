@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
@file:Suppress("DEPRECATION") // SecureTransport deprecated macOS 10.15; functional for POC

package com.tubetoast.poc

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeArrayCallBacks
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecCertificateCopyKey
import platform.Security.SecIdentityRef
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecPKCS12Import
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.SecTrustRef
import platform.Security.SSLClose
import platform.Security.SSLContextRef
import platform.Security.SSLCopyPeerTrust
import platform.Security.SSLHandshake
import platform.Security.SSLNewContext
import platform.Security.SSLRead
import platform.Security.SSLSetCertificate
import platform.Security.SSLSetConnection
import platform.Security.SSLSetIOFuncs
import platform.Security.SSLSetSessionOption
import platform.Security.SSLWrite
import platform.Security.errSecSuccess
import platform.Security.errSSLWouldBlock
import platform.Security.kSSLSessionOptionBreakOnServerAuth
import platform.Security.kSecImportExportPassphrase
import platform.Security.kSecImportItemIdentity
import platform.darwin.inet_addr
import platform.posix.AF_INET
import platform.posix.IPPROTO_TCP
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.SO_REUSEADDR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.exit
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.listen
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket

// SPKI DER of jvm.crt — for pinning JVM server from macOS client
private val EXPECTED_JVM_SPKI: ByteArray = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02,
    0x01, 0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03,
    0x42, 0x00, 0x04, 0x81.toByte(), 0xe1.toByte(), 0xde.toByte(), 0x3a, 0x42, 0xf6.toByte(), 0x10, 0xd8.toByte(), 0x55,
    0xe2.toByte(), 0x9b.toByte(), 0x35, 0x14, 0x0b, 0xe4.toByte(), 0x27, 0xe9.toByte(), 0xdc.toByte(), 0xe3.toByte(), 0xa4.toByte(), 0x6f,
    0x39, 0x63, 0x23, 0xde.toByte(), 0x6f, 0xbb.toByte(), 0xdf.toByte(), 0x8d.toByte(), 0xc2.toByte(), 0xf9.toByte(), 0x2d, 0xec.toByte(),
    0xaa.toByte(), 0x6f, 0x0a, 0x09, 0x9b.toByte(), 0xb7.toByte(), 0xee.toByte(), 0xfb.toByte(), 0x36, 0xd0.toByte(), 0x23, 0x6a,
    0x80.toByte(), 0xe4.toByte(), 0x23, 0x84.toByte(), 0x9c.toByte(), 0x23, 0x10, 0x0a, 0xe2.toByte(), 0xec.toByte(), 0x9e.toByte(), 0xa3.toByte(),
    0x80.toByte(), 0xdd.toByte(), 0x6b, 0x0d, 0xf8.toByte(), 0x23, 0xd7.toByte()
)

private val PAYLOAD = ByteArray(100 * 1024 * 1024) { (it % 256).toByte() } // 100 MB

// SecureTransport I/O callbacks (StableRef passes the socket fd as connection context)
private val sslReadCallback = staticCFunction<COpaquePointer?, COpaquePointer?, CPointer<ULongVar>?, Int> {
        connRef, dataPtr, dataLenPtr ->
    val fd = connRef!!.asStableRef<Int>().get()
    val requested = dataLenPtr!!.pointed.value.toInt()
    val result = recv(fd, dataPtr, requested.toULong(), 0)
    when {
        result > 0L -> { dataLenPtr.pointed.value = result.toULong(); 0 }
        result == 0L -> { dataLenPtr.pointed.value = 0u; -9805 } // errSSLClosedGraceful
        else -> { dataLenPtr.pointed.value = 0u; -9844 } // errSSLClosedAbort
    }
}

private val sslWriteCallback = staticCFunction<COpaquePointer?, COpaquePointer?, CPointer<ULongVar>?, Int> {
        connRef, dataPtr, dataLenPtr ->
    val fd = connRef!!.asStableRef<Int>().get()
    val requested = dataLenPtr!!.pointed.value.toInt()
    val result = send(fd, dataPtr, requested.toULong(), 0)
    when {
        result >= 0L -> { dataLenPtr.pointed.value = result.toULong(); 0 }
        else -> { dataLenPtr.pointed.value = 0u; -9844 }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: macos-poc server | client <host> <port>")
        exit(1)
    }
    when (args[0]) {
        "server" -> runServer()
        "client" -> {
            if (args.size < 3) { println("Usage: macos-poc client <host> <port>"); exit(1) }
            exit(runClient(args[1], args[2]))
        }
        else -> { println("Unknown mode: ${args[0]}"); exit(1) }
    }
}

private fun loadAppleP12Bytes(): ByteArray {
    val candidates = listOf("poc-tls-spike/keys/apple.p12", "apple.p12")
    for (path in candidates) {
        val file = fopen(path, "rb") ?: continue
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            val buf = ByteArray(size)
            buf.usePinned { pinned -> fread(pinned.addressOf(0), 1u, size.toULong(), file) }
            println("[server] Loaded apple.p12 from $path ($size bytes)")
            return buf
        } finally {
            fclose(file)
        }
    }
    error("Could not load apple.p12 — run from repo root.")
}

private fun loadAppleIdentity(): SecIdentityRef {
    val p12Bytes = loadAppleP12Bytes()
    return memScoped {
        val cfData: CFDataRef = p12Bytes.usePinned { pinned ->
            CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), p12Bytes.size.toLong())
        } ?: error("CFDataCreate failed")

        val passStr = platform.CoreFoundation.CFStringCreateWithCString(
            kCFAllocatorDefault, "poc", platform.CoreFoundation.kCFStringEncodingUTF8
        ) ?: error("CFStringCreateWithCString failed")

        val keyVar = alloc<COpaquePointerVar>(); keyVar.value = kSecImportExportPassphrase
        val valVar = alloc<COpaquePointerVar>(); valVar.value = passStr

        val options = platform.CoreFoundation.CFDictionaryCreate(
            kCFAllocatorDefault, keyVar.ptr.reinterpret(), valVar.ptr.reinterpret(), 1,
            platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks.ptr,
            platform.CoreFoundation.kCFTypeDictionaryValueCallBacks.ptr
        )
        val itemsVar = alloc<COpaquePointerVar>()
        val status = SecPKCS12Import(cfData.reinterpret(), options?.reinterpret(), itemsVar.ptr.reinterpret())
        CFRelease(cfData); CFRelease(passStr); CFRelease(options)
        if (status != errSecSuccess) error("SecPKCS12Import failed: $status")

        @Suppress("UNCHECKED_CAST")
        val items = itemsVar.value as? platform.CoreFoundation.CFArrayRef ?: error("Null items")
        if (CFArrayGetCount(items) == 0L) error("Empty items from SecPKCS12Import")
        @Suppress("UNCHECKED_CAST")
        val dict = CFArrayGetValueAtIndex(items, 0) as? platform.CoreFoundation.CFDictionaryRef ?: error("Item[0] not dict")
        @Suppress("UNCHECKED_CAST")
        val identity = CFDictionaryGetValue(dict, kSecImportItemIdentity) as? SecIdentityRef ?: error("No identity")
        platform.CoreFoundation.CFRetain(identity)
        CFRelease(items)
        identity
    }
}

// Byte swap (htonl/htons are C macros, not available in K/N)
private fun hostToNetShort(v: Int): UShort = (((v and 0xFF00) ushr 8) or ((v and 0xFF) shl 8)).toUShort()

private fun createServerSocket(port: Int): Int = memScoped {
    val fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
    check(fd >= 0) { "socket() failed: $errno" }

    val optVal = alloc<IntVar>(); optVal.value = 1
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, optVal.ptr, 4u)

    val addr = alloc<sockaddr_in>()
    memset(addr.ptr, 0, sockaddr_in.size.toULong())
    addr.sin_family = AF_INET.toUByte()
    addr.sin_port = hostToNetShort(port)
    addr.sin_addr.s_addr = 0u // INADDR_ANY

    check(bind(fd, addr.ptr.reinterpret(), sockaddr_in.size.toUInt()) == 0) { "bind() failed: $errno" }
    check(listen(fd, 1) == 0) { "listen() failed: $errno" }
    fd
}

private fun createClientSocket(host: String, port: Int): Int = memScoped {
    val fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
    check(fd >= 0) { "socket() failed: $errno" }

    val addr = alloc<sockaddr_in>()
    memset(addr.ptr, 0, sockaddr_in.size.toULong())
    addr.sin_family = AF_INET.toUByte()
    addr.sin_port = hostToNetShort(port)
    addr.sin_addr.s_addr = inet_addr(host)

    check(connect(fd, addr.ptr.reinterpret(), sockaddr_in.size.toUInt()) == 0) { "connect() to $host:$port failed: $errno" }
    fd
}

private fun createSslContext(serverSide: Boolean, fd: Int): Pair<SSLContextRef, StableRef<Int>> {
    val fdRef = StableRef.create(fd)
    // SSLNewContext takes plain Boolean; SSLCreateContext enums not accessible via K/N interop
    val ctx: SSLContextRef = memScoped {
        val ctxVar = alloc<CPointerVar<cnames.structs.SSLContext>>()
        val err = SSLNewContext(serverSide, ctxVar.ptr.reinterpret())
        if (err != 0) error("SSLNewContext failed: $err")
        ctxVar.value ?: error("SSLNewContext returned null")
    }
    SSLSetIOFuncs(ctx, sslReadCallback, sslWriteCallback)
    SSLSetConnection(ctx, fdRef.asCPointer())
    return Pair(ctx, fdRef)
}

private fun sslWriteAll(ctx: SSLContextRef, data: ByteArray): Boolean {
    var offset = 0
    val chunkSize = 65536
    while (offset < data.size) {
        val toWrite = minOf(data.size - offset, chunkSize)
        val written = memScoped {
            val w = alloc<ULongVar>(); w.value = 0u
            val err = data.usePinned { pin ->
                SSLWrite(ctx, pin.addressOf(offset), toWrite.toULong(), w.ptr)
            }
            if (err != 0) { println("[ssl] SSLWrite error $err"); return false }
            w.value.toInt()
        }
        offset += written
    }
    return true
}

private fun sslReadExact(ctx: SSLContextRef, count: Int): ByteArray? {
    val buf = ByteArray(count)
    var offset = 0
    val chunkSize = 65536
    while (offset < count) {
        val remaining = count - offset
        val toRead = minOf(remaining, chunkSize)
        val read = memScoped {
            val r = alloc<ULongVar>(); r.value = 0u
            val err = buf.usePinned { pin ->
                SSLRead(ctx, pin.addressOf(offset), toRead.toULong(), r.ptr)
            }
            if (err != 0 && err != errSSLWouldBlock) { println("[ssl] SSLRead error $err at $offset/$count"); return null }
            r.value.toInt()
        }
        offset += read
    }
    return buf
}

private fun runServer() {
    println("[server] Starting TLS server on :8444 (SecureTransport)...")
    val identity = loadAppleIdentity()

    val serverFd = createServerSocket(8444)
    println("[server] Listening on :8444")
    val clientFd = accept(serverFd, null, null)
    check(clientFd >= 0) { "accept() failed: $errno" }
    close(serverFd)
    println("[server] Accepted connection fd=$clientFd")

    val (ctx, fdRef) = createSslContext(serverSide = true, clientFd)

    // Set TLS identity (certificate + private key)
    val certArray = memScoped {
        val ptr = alloc<COpaquePointerVar>(); ptr.value = identity
        CFArrayCreate(kCFAllocatorDefault, ptr.ptr.reinterpret(), 1, kCFTypeArrayCallBacks.ptr)
    } ?: error("CFArrayCreate for identity failed")
    SSLSetCertificate(ctx, certArray.reinterpret())
    CFRelease(certArray)

    var hs: Int
    do { hs = SSLHandshake(ctx) } while (hs == errSSLWouldBlock)
    if (hs != 0) { println("[server] Handshake failed: $hs"); SSLClose(ctx); close(clientFd); fdRef.dispose(); return }
    println("[server] TLS handshake OK")

    val lenBytes = sslReadExact(ctx, 8) ?: run { SSLClose(ctx); close(clientFd); fdRef.dispose(); return }
    val len = bigEndianBytesToLong(lenBytes)
    println("[server] Expecting $len bytes...")

    val payload = sslReadExact(ctx, len.toInt()) ?: run { SSLClose(ctx); close(clientFd); fdRef.dispose(); return }
    println("[server] Received ${payload.size} bytes")

    val sha256 = sha256Bytes(payload)
    if (sslWriteAll(ctx, sha256 + byteArrayOf(0x00))) println("[server] Sent SHA256 + status OK")

    SSLClose(ctx)
    close(clientFd)
    fdRef.dispose()
    println("[server] Done.")
}

private fun runClient(host: String, port: String): Int {
    println("[client] Connecting to $host:$port...")
    val clientFd = createClientSocket(host, port.toInt())
    println("[client] TCP connected")

    val (ctx, fdRef) = createSslContext(serverSide = false, clientFd)

    // Break after server auth to inspect and pin the certificate manually
    SSLSetSessionOption(ctx, kSSLSessionOptionBreakOnServerAuth, true)

    var hs: Int
    do { hs = SSLHandshake(ctx) } while (hs == errSSLWouldBlock)

    // errSSLServerAuthCompleted = -9841
    if (hs != -9841) {
        println("[client] Unexpected handshake result: $hs (expected -9841 for break-on-server-auth)")
        SSLClose(ctx); close(clientFd); fdRef.dispose()
        return 3
    }
    println("[client] Server auth break — validating SPKI...")

    val pinResult = memScoped {
        val trustVar = alloc<COpaquePointerVar>()
        SSLCopyPeerTrust(ctx, trustVar.ptr.reinterpret())
        @Suppress("UNCHECKED_CAST")
        val trust: SecTrustRef? = trustVar.value as? SecTrustRef

        if (trust == null) { println("[client] SSLCopyPeerTrust null"); return@memScoped 3 }

        val certCount = SecTrustGetCertificateCount(trust)
        if (certCount == 0L) { println("[client] No certs in chain"); return@memScoped 3 }

        @Suppress("DEPRECATION")
        val leafCert = SecTrustGetCertificateAtIndex(trust, 0) ?: run { println("[client] Null leaf cert"); return@memScoped 3 }
        val pubKey = SecCertificateCopyKey(leafCert) ?: run { println("[client] No public key"); return@memScoped 3 }
        val rawKeyData = SecKeyCopyExternalRepresentation(pubKey, null) ?: run { println("[client] SecKeyCopyExternalRepresentation failed"); return@memScoped 3 }

        val rawKeyBytes = cfDataToByteArray(rawKeyData)
        CFRelease(rawKeyData)

        val spkiDer = buildP256SpkiDer(rawKeyBytes)
        if (spkiDer.contentEquals(EXPECTED_JVM_SPKI)) {
            println("[client] SPKI pin OK")
            0
        } else {
            println("[client] SPKI MISMATCH")
            println("[client] Expected: ${EXPECTED_JVM_SPKI.toHex()}")
            println("[client] Got:      ${spkiDer.toHex()}")
            1
        }
    }

    if (pinResult != 0) { SSLClose(ctx); close(clientFd); fdRef.dispose(); return pinResult }

    // Resume handshake after successful pin validation
    do { hs = SSLHandshake(ctx) } while (hs == errSSLWouldBlock)
    if (hs != 0) { println("[client] Handshake resume failed: $hs"); SSLClose(ctx); close(clientFd); fdRef.dispose(); return 3 }
    println("[client] TLS handshake complete — sending 100 MB payload...")

    val toSend = longToBigEndianBytes(PAYLOAD.size.toLong()) + PAYLOAD
    if (!sslWriteAll(ctx, toSend)) { SSLClose(ctx); close(clientFd); fdRef.dispose(); return 3 }
    println("[client] Sent, waiting for SHA256 response...")

    val resp = sslReadExact(ctx, 33) ?: run { SSLClose(ctx); close(clientFd); fdRef.dispose(); return 3 }
    val serverSha256 = resp.copyOfRange(0, 32)
    val status = resp[32]
    val localSha256 = sha256Bytes(PAYLOAD)

    val finalCode = when {
        !serverSha256.contentEquals(localSha256) -> { println("[client] SHA256 MISMATCH"); 2 }
        status != 0x00.toByte() -> { println("[client] Server status != OK: $status"); 2 }
        else -> { println("[client] SUCCESS — SHA256 matched, status OK"); 0 }
    }

    SSLClose(ctx)
    close(clientFd)
    fdRef.dispose()
    println("[client] Exiting with code $finalCode")
    return finalCode
}

private fun cfDataToByteArray(data: CFDataRef): ByteArray {
    val len = CFDataGetLength(data).toInt()
    if (len == 0) return ByteArray(0)
    val src = CFDataGetBytePtr(data) ?: return ByteArray(0)
    val result = ByteArray(len)
    result.usePinned { pin -> memcpy(pin.addressOf(0), src, len.toULong()) }
    return result
}

// Raw EC point: 04 || x || y (65 bytes P-256)
// SPKI DER: SEQUENCE { SEQUENCE { OID ecPublicKey, OID prime256v1 }, BIT STRING { 00 || point } }
private fun buildP256SpkiDer(rawEcPoint: ByteArray): ByteArray {
    val ecOid = byteArrayOf(0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01)
    val p256Oid = byteArrayOf(0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07)
    val algSeq = byteArrayOf(0x30, (ecOid.size + p256Oid.size).toByte()) + ecOid + p256Oid
    val bitStr = byteArrayOf(0x03, (rawEcPoint.size + 1).toByte(), 0x00) + rawEcPoint
    val body = algSeq + bitStr
    return byteArrayOf(0x30, body.size.toByte()) + body
}

private fun bigEndianBytesToLong(bytes: ByteArray): Long {
    var v = 0L
    for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)
    return v
}

private fun longToBigEndianBytes(v: Long): ByteArray {
    val r = ByteArray(8); var rem = v
    for (i in 7 downTo 0) { r[i] = (rem and 0xFF).toByte(); rem = rem ushr 8 }
    return r
}

private fun sha256Bytes(data: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { dPin ->
        result.usePinned { rPin ->
            CC_SHA256(dPin.addressOf(0), data.size.toUInt(), rPin.addressOf(0).reinterpret())
        }
    }
    return result
}

private fun ByteArray.toHex(): String = buildString {
    for (b in this@toHex) {
        val v = b.toInt() and 0xFF
        append("0123456789abcdef"[v shr 4])
        append("0123456789abcdef"[v and 0xF])
    }
}

package com.tubetoast.poc

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.system.exitProcess

// SPKI DER of apple cert (from apple-spki.der) — used to pin Apple server
private val EXPECTED_APPLE_SPKI: ByteArray = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02,
    0x01, 0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03,
    0x42, 0x00, 0x04, 0xce.toByte(), 0xa5.toByte(), 0x2e, 0x7c, 0xed.toByte(), 0x1a, 0xe5.toByte(), 0x23, 0x6c,
    0x02, 0x1b, 0x13, 0xe0.toByte(), 0x7b, 0x67, 0x89.toByte(), 0x86.toByte(), 0xe4.toByte(), 0xd7.toByte(), 0x0c, 0x53,
    0x70, 0xd9.toByte(), 0x1d, 0x28, 0x83.toByte(), 0xfa.toByte(), 0x32, 0xe7.toByte(), 0x37, 0xad.toByte(), 0x18, 0x18,
    0x8f.toByte(), 0x88.toByte(), 0x6c, 0x38, 0x9c.toByte(), 0x07, 0xcb.toByte(), 0x55, 0x9a.toByte(), 0xd6.toByte(), 0x6c, 0x20,
    0xb1.toByte(), 0xac.toByte(), 0x22, 0xe8.toByte(), 0xa9.toByte(), 0x77, 0xac.toByte(), 0x62, 0x7b, 0xa9.toByte(), 0xd6.toByte(), 0x54,
    0xc0.toByte(), 0xa1.toByte(), 0x9f.toByte(), 0x88.toByte(), 0xcd.toByte(), 0xcc.toByte(), 0xb2.toByte()
)

// SPKI DER of jvm cert (from jvm-spki.der) — used so macOS client can pin us
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

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: jvm-poc server | client <host> <port>")
        exitProcess(1)
    }
    when (args[0]) {
        "server" -> runServer()
        "client" -> {
            if (args.size < 3) { println("Usage: jvm-poc client <host> <port>"); exitProcess(1) }
            runClient(args[1], args[2].toInt())
        }
        else -> { println("Unknown mode: ${args[0]}"); exitProcess(1) }
    }
}

private fun loadServerSslContext(): SSLContext {
    val p12Stream: InputStream = checkNotNull(
        object {}.javaClass.classLoader.getResourceAsStream("jvm.p12")
    ) { "jvm.p12 not found in resources" }
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(p12Stream, "poc".toCharArray())
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, "poc".toCharArray())
    return SSLContext.getInstance("TLS").also {
        it.init(kmf.keyManagers, null, null)
    }
}

private fun runServer() {
    println("[server] Loading JVM keystore from jvm.p12...")
    val ctx = loadServerSslContext()
    val ssf = ctx.serverSocketFactory
    val serverSocket = ssf.createServerSocket(8443, 1, java.net.InetAddress.getByName("0.0.0.0"))
    println("[server] Listening on 0.0.0.0:8443 (TLS, IPv4)")
    handleServerConnection(serverSocket.accept())
}

private fun handleServerConnection(socket: Socket) {
    println("[server] Accepted connection from ${socket.remoteSocketAddress}")
    socket.use {
        val ins = it.getInputStream()
        val outs = it.getOutputStream()

        // read 8-byte big-endian length
        val lenBuf = ByteArray(8)
        var read = 0
        while (read < 8) read += ins.read(lenBuf, read, 8 - read)
        val len = java.nio.ByteBuffer.wrap(lenBuf).long
        println("[server] Expecting $len bytes of payload")

        // read payload
        val payload = ByteArray(len.toInt())
        var totalRead = 0
        while (totalRead < len) {
            val r = ins.read(payload, totalRead, (len - totalRead).toInt())
            if (r < 0) error("Unexpected EOF")
            totalRead += r
        }
        println("[server] Received $totalRead bytes")

        val sha256 = MessageDigest.getInstance("SHA-256").digest(payload)
        val status: Byte = 0x00
        outs.write(sha256)
        outs.write(byteArrayOf(status))
        outs.flush()
        println("[server] Sent SHA256 + status OK")
    }
}

private fun runClient(host: String, port: Int) {
    println("[client] Connecting to $host:$port with SPKI pinning...")

    var pinCheckPassed = false

    val trustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val serverCert = chain[0]
            val serverSpki = serverCert.publicKey.encoded
            if (!serverSpki.contentEquals(EXPECTED_APPLE_SPKI)) {
                println("[client] SPKI MISMATCH — pin failed!")
                println("[client] Expected: ${EXPECTED_APPLE_SPKI.toHex()}")
                println("[client] Got:      ${serverSpki.toHex()}")
                throw javax.net.ssl.SSLException("SPKI pin mismatch")
            }
            println("[client] SPKI pin verified OK")
            pinCheckPassed = true
        }
    }

    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf(trustManager), null)
    val factory: SSLSocketFactory = ctx.socketFactory

    try {
        val socket = factory.createSocket(host, port) as SSLSocket
        socket.use {
            it.startHandshake()
            if (!pinCheckPassed) {
                println("[client] Pin check never ran — unexpected")
                exitProcess(3)
            }

            val outs = it.getOutputStream()
            val ins = it.getInputStream()

            val len = PAYLOAD.size.toLong()
            val lenBuf = java.nio.ByteBuffer.allocate(8).putLong(len).array()
            println("[client] Sending ${PAYLOAD.size} bytes...")
            outs.write(lenBuf)
            outs.write(PAYLOAD)
            outs.flush()

            // read 32-byte SHA256 + 1-byte status
            val sha256Buf = ByteArray(32)
            var r = 0
            while (r < 32) r += ins.read(sha256Buf, r, 32 - r)
            val statusBuf = ByteArray(1)
            ins.read(statusBuf)

            val localSha256 = MessageDigest.getInstance("SHA-256").digest(PAYLOAD)
            if (!sha256Buf.contentEquals(localSha256)) {
                println("[client] SHA256 MISMATCH")
                println("[client] Server returned: ${sha256Buf.toHex()}")
                println("[client] Local computed:  ${localSha256.toHex()}")
                exitProcess(2)
            }
            if (statusBuf[0] != 0x00.toByte()) {
                println("[client] Server status not OK: ${statusBuf[0]}")
                exitProcess(2)
            }
            println("[client] SUCCESS — SHA256 matched, status OK")
        }
    } catch (e: javax.net.ssl.SSLException) {
        println("[client] Handshake/TLS error: ${e.message}")
        exitProcess(1)
    } catch (e: Exception) {
        println("[client] Error: ${e.message}")
        exitProcess(3)
    }
    exitProcess(0)
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

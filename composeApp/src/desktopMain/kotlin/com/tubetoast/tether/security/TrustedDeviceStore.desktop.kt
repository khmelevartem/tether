package com.tubetoast.tether.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val defaultConfigDir = File(System.getProperty("user.home"), ".config/tether")
private val log = KydraLog.withTag(default = "TrustedDeviceStore")

actual open class TrustedDeviceStore(
    private val configDir: File = defaultConfigDir,
) {
    private val storageFile = File(configDir, "trusted.json")
    private val lock = Any()
    private val store: MutableMap<String, ByteArray> = loadFromDisk()

    actual open fun isTrusted(deviceId: String): Boolean = synchronized(lock) { store.containsKey(deviceId) }

    actual open fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        synchronized(lock) {
            store[deviceId] = publicKey
            persistToDisk()
        }
    }

    actual open fun getPublicKey(deviceId: String): ByteArray? = synchronized(lock) { store[deviceId] }

    private fun loadFromDisk(): MutableMap<String, ByteArray> {
        if (!storageFile.exists()) return mutableMapOf()
        return try {
            val json = Json.parseToJsonElement(storageFile.readText()) as JsonObject
            json.entries
                .associateTo(mutableMapOf()) { (key, value) ->
                    key to value.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                }
        } catch (e: Exception) {
            log.error { "failed to load trusted device store — ${e.message}" }
            mutableMapOf()
        }
    }

    private fun persistToDisk() {
        configDir.mkdirs()
        val json = buildJsonObject {
            store.forEach { (deviceId, publicKey) ->
                put(deviceId, JsonArray(publicKey.map { JsonPrimitive(it.toInt()) }))
            }
        }
        // Atomic replace: write to a temp file and move into place so a crashed/concurrent writer
        // never leaves trusted.json half-written (which would silently wipe the store on next boot).
        val tmp = File.createTempFile("trusted-", ".tmp", configDir)
        try {
            tmp.writeText(json.toString())
            Files.move(
                tmp.toPath(),
                storageFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}

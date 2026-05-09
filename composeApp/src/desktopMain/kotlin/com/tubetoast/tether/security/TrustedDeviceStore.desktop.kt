package com.tubetoast.tether.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val defaultConfigDir = File(System.getProperty("user.home"), ".config/tether")

actual class TrustedDeviceStore(
    private val configDir: File = defaultConfigDir,
) {
    private val storageFile = File(configDir, "trusted.json")
    private val store: MutableMap<String, ByteArray> = loadFromDisk()

    actual fun isTrusted(deviceId: String): Boolean = store.containsKey(deviceId)

    actual fun saveTrustedKey(deviceId: String, publicKey: ByteArray) {
        store[deviceId] = publicKey
        persistToDisk()
    }

    actual fun getPublicKey(deviceId: String): ByteArray? = store[deviceId]

    private fun loadFromDisk(): MutableMap<String, ByteArray> {
        if (!storageFile.exists()) return mutableMapOf()
        return try {
            val json = Json.parseToJsonElement(storageFile.readText()) as JsonObject
            json.entries
                .associate { (key, value) ->
                    key to value.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                }.toMutableMap()
        } catch (e: Exception) {
            System.err.println("ERROR: failed to load trusted device store — ${e.message}")
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
        storageFile.writeText(json.toString())
    }
}

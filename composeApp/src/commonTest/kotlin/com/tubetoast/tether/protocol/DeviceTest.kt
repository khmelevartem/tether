package com.tubetoast.tether.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun `Device round-trips through JSON`() {
        val original = Device(
            id = "abc-123",
            name = "My Mac",
            host = "192.168.1.10",
            port = 8080
        )
        val decoded = json.decodeFromString<Device>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `Device serializes to expected wire format`() {
        val device = Device(id = "x", name = "n", host = "h", port = 9)
        assertEquals("""{"id":"x","name":"n","host":"h","port":9}""", json.encodeToString(device))
    }
}

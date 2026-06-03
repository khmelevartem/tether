package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.computePinCode
import com.tubetoast.tether.security.deviceIdFromPublicKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "HandshakePairing")

/** [client] is borrowed, not owned — its lifetime is the FileClient that shares the same instance. */
class HandshakePairing(
    private val client: HttpClient,
    private val trustedDeviceStore: TrustedDeviceStore,
    private val ownKeyPair: DeviceKeyPair,
    private val ownDeviceName: suspend () -> String,
    private val confirmationHandler: PairingConfirmationHandler,
) : PeerPairing {
    override suspend fun ensurePaired(device: Device): Boolean {
        val response = try {
            client.post("http://${device.host}:${device.port}/pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(publicKey = ownKeyPair.publicKey, deviceName = ownDeviceName()))
            }
        } catch (e: Exception) {
            log.error { e withMessage "ensurePaired failed → ${device.host}:${device.port}" }
            return false
        }
        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.Forbidden) {
                log.info { "pairing rejected by remote → ${device.host}:${device.port}" }
            } else {
                log.error { "pair request failed with ${response.status} → ${device.host}:${device.port}" }
            }
            return false
        }
        val serverPublicKey = response.body<PairResponse>().publicKey
        val deviceId = deviceIdFromPublicKey(serverPublicKey)
        if (trustedDeviceStore.isTrusted(deviceId)) return true
        val pin = computePinCode(ownKeyPair.publicKey, serverPublicKey)
        if (!confirmationHandler.confirmPairing(pin, device.name)) {
            log.info { "pairing declined by user → ${device.host}:${device.port}" }
            return false
        }
        trustedDeviceStore.saveTrustedKey(deviceId, serverPublicKey)
        return true
    }
}

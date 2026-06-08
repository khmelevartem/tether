package com.tubetoast.tether.discovery

actual class MdnsDiscovery(
    private val delegate: DeviceDiscovery,
) : DeviceDiscovery by delegate

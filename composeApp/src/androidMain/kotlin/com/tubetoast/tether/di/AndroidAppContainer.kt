package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.security.TrustedDeviceStore

class AndroidAppContainer(
    config: AndroidAppConfig,
) : JvmAppContainer(config, TrustedDeviceStore(config.application)) {
    val application = config.application
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(application)
}

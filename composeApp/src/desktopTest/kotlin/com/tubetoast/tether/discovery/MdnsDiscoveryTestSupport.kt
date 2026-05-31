package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.preferences.TempDataStore

internal fun testDiscovery(
    store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
): MdnsDiscovery {
    val temp = TempDataStore()
    return MdnsDiscovery(store, DeviceIdentityStore(temp.dataStore))
}

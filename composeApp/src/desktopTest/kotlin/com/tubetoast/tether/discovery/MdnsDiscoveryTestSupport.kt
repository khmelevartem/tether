package com.tubetoast.tether.discovery

import com.tubetoast.tether.di.desktopMdnsDelegate
import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.preferences.TempDataStore

internal fun testDiscovery(
    store: DiscoveredDevicesStore = DiscoveredDevicesStore(),
): MdnsDiscovery {
    val temp = TempDataStore()
    val identityStore = DeviceIdentityStore(DataStoreFingerprintPersistence(temp.dataStore))
    return MdnsDiscovery(desktopMdnsDelegate(store, identityStore))
}

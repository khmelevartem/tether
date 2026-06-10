package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscoveryJmdns
import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.LinuxFilePicker
import com.tubetoast.tether.transfer.MacFilePicker
import com.tubetoast.tether.transfer.WindowHolder
import com.tubetoast.tether.transfer.WindowsFilePicker
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Selecting a [DesktopPlatform] is the only OS branch in the codebase. */
internal sealed interface DesktopPlatform {
    fun guiFilePicker(windowHolder: WindowHolder): FilePicker

    fun mdnsDelegate(store: DiscoveredDevicesStore, identity: DeviceIdentityStore): DeviceDiscovery

    fun preferencesDir(userHome: String): File

    object MacOs : DesktopPlatform {
        override fun guiFilePicker(windowHolder: WindowHolder): FilePicker = MacFilePicker(windowHolder)

        // JmDNS can't see external Wi-Fi peers on macOS; Bonjour talks to the system DNS-SD daemon.
        override fun mdnsDelegate(store: DiscoveredDevicesStore, identity: DeviceIdentityStore): DeviceDiscovery =
            MdnsDiscoveryBonjour(store, identity)

        override fun preferencesDir(userHome: String): File = File(userHome, ".config/tether")
    }

    object Windows : DesktopPlatform {
        override fun guiFilePicker(windowHolder: WindowHolder): FilePicker = WindowsFilePicker(windowHolder)

        override fun mdnsDelegate(store: DiscoveredDevicesStore, identity: DeviceIdentityStore): DeviceDiscovery =
            MdnsDiscoveryJmdns(store, Dispatchers.IO, identity)

        override fun preferencesDir(userHome: String): File =
            File(System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming", "Tether")
    }

    object Linux : DesktopPlatform {
        override fun guiFilePicker(windowHolder: WindowHolder): FilePicker = LinuxFilePicker(windowHolder)

        override fun mdnsDelegate(store: DiscoveredDevicesStore, identity: DeviceIdentityStore): DeviceDiscovery =
            MdnsDiscoveryJmdns(store, Dispatchers.IO, identity)

        override fun preferencesDir(userHome: String): File = File(userHome, ".config/tether")
    }
}

internal fun desktopPlatformFrom(osName: String): DesktopPlatform = when {
    osName.lowercase().contains("mac") -> DesktopPlatform.MacOs
    osName.lowercase().contains("win") -> DesktopPlatform.Windows
    else -> DesktopPlatform.Linux
}

internal val desktopPlatform: DesktopPlatform by lazy {
    desktopPlatformFrom(System.getProperty("os.name").orEmpty())
}

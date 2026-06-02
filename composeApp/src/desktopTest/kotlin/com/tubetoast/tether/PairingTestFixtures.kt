package com.tubetoast.tether

import com.tubetoast.tether.preferences.TempDataStore
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File
import java.nio.file.Files

class PairingTestFixtures {
    val cleanupPaths = mutableListOf<File>()
    val cleanupTempStores = mutableListOf<TempDataStore>()

    fun newConfigDir(): File =
        Files.createTempDirectory("tether-test").toFile().also(cleanupPaths::add)

    fun newTrustedStore(): TrustedDeviceStore {
        val temp = TempDataStore()
        cleanupTempStores += temp
        return DefaultTrustedDeviceStore(temp.dataStore)
    }

    fun tearDown() {
        cleanupPaths.forEach { it.deleteRecursively() }
        cleanupPaths.clear()
        cleanupTempStores.forEach { it.tearDown() }
        cleanupTempStores.clear()
    }
}

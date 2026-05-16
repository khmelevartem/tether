package com.tubetoast.tether.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

class AndroidTransferLockHolder(
    context: Context,
) {
    // HIGH_PERF (deprecated API 29) is intentional: LOW_LATENCY only activates while the screen
    // is on and the app is foreground, but the bug under fix is screen-off transfers — so the
    // newer constant would silently no-op exactly when we need it.
    @Suppress("DEPRECATION")
    private val wifiLock: WifiManager.WifiLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    private val wakeLock: PowerManager.WakeLock =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    fun acquire() {
        if (!wifiLock.isHeld) wifiLock.acquire()
        // Timeout would bound streaming transfers arbitrarily; release is guaranteed by the tracker.
        if (!wakeLock.isHeld) {
            @SuppressLint("WakelockTimeout")
            wakeLock.acquire()
        }
    }

    fun release() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
    }

    internal val isWifiLockHeld: Boolean get() = wifiLock.isHeld
    internal val isWakeLockHeld: Boolean get() = wakeLock.isHeld
}

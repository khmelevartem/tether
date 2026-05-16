package com.tubetoast.tether.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

class AndroidTransferLockHolder(
    context: Context,
) {
    private val wifiLock: WifiManager.WifiLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tether:transfer")
            .also {
                // Delegate reference counting to DefaultTransferActivityTracker; OS ref-counting
                // would require matching acquire/release pairs that the tracker already manages.
                it.setReferenceCounted(false)
            }

    private val wakeLock: PowerManager.WakeLock =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tether:transfer")
            .also { it.setReferenceCounted(false) }

    fun acquire() {
        if (!wifiLock.isHeld) wifiLock.acquire()
        if (!wakeLock.isHeld) wakeLock.acquire()
    }

    fun release() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
    }
}

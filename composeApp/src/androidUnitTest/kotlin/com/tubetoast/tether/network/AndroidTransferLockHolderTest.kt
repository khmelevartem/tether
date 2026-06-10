package com.tubetoast.tether.network

import android.content.Context
import com.tubetoast.tether.TetherApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class AndroidTransferLockHolderTest {
    private lateinit var holder: AndroidTransferLockHolder
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        holder = AndroidTransferLockHolder(context)
    }

    @Test
    fun `acquire then release toggles both locks`() {
        holder.acquire()
        assertTrue(holder.isWakeLockHeld, "wake lock should be held after acquire")
        assertTrue(holder.isWifiLockHeld, "wifi lock should be held after acquire")
        holder.release()
        assertFalse(holder.isWakeLockHeld, "wake lock should be released after release")
        assertFalse(holder.isWifiLockHeld, "wifi lock should be released after release")
    }

    @Test
    fun `double acquire is idempotent for both locks`() {
        holder.acquire()
        holder.acquire()
        assertTrue(holder.isWakeLockHeld)
        assertTrue(holder.isWifiLockHeld)
        holder.release()
        assertFalse(holder.isWakeLockHeld, "single release should drop wake lock")
        assertFalse(holder.isWifiLockHeld, "single release should drop wifi lock")
    }

    @Test
    fun `release without prior acquire is a no-op`() {
        holder.release()
        assertFalse(holder.isWakeLockHeld)
        assertFalse(holder.isWifiLockHeld)
    }

    @Test
    fun `tracker callbacks drive both locks end-to-end`() = kotlinx.coroutines.test.runTest {
        val tracker = DefaultTransferActivityTracker(
            scope = backgroundScope,
            onFirstEnter = holder::acquire,
            onLastExit = holder::release,
        )
        assertFalse(holder.isWakeLockHeld, "no lock before any transfer")
        assertFalse(holder.isWifiLockHeld, "no lock before any transfer")
        var observedInsideWake = false
        var observedInsideWifi = false
        tracker.withActiveTransfer {
            observedInsideWake = holder.isWakeLockHeld
            observedInsideWifi = holder.isWifiLockHeld
        }
        assertTrue(observedInsideWake, "wake lock should be held inside active transfer")
        assertTrue(observedInsideWifi, "wifi lock should be held inside active transfer")
        assertFalse(holder.isWakeLockHeld, "wake lock should be released after transfer")
        assertFalse(holder.isWifiLockHeld, "wifi lock should be released after transfer")
    }
}

package com.tubetoast.tether.network

import android.content.Context
import com.tubetoast.tether.TetherApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
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

    private fun isWakeLockHeld(): Boolean =
        ShadowPowerManager.getLatestWakeLock()?.isHeld ?: false

    @Test
    fun `acquire then release toggles wake lock`() {
        holder.acquire()
        assertTrue(isWakeLockHeld(), "wake lock should be held after acquire")
        holder.release()
        assertFalse(isWakeLockHeld(), "wake lock should be released after release")
    }

    @Test
    fun `double acquire is idempotent`() {
        holder.acquire()
        holder.acquire()
        assertTrue(isWakeLockHeld())
        holder.release()
        assertFalse(isWakeLockHeld(), "single release should drop the lock")
    }

    @Test
    fun `release without prior acquire is a no-op`() {
        holder.release()
        assertFalse(isWakeLockHeld())
    }
}

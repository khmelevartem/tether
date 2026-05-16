package com.tubetoast.tether.network

import android.app.Service
import android.content.Intent
import com.tubetoast.tether.TetherApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TetherApp::class)
class TetherForegroundServiceTest {
    @Test
    fun `onBind returns non-null binder so bindService check works`() {
        val controller = Robolectric.buildService(TetherForegroundService::class.java).create()
        val binder = controller.get().onBind(Intent())
        assertNotNull(binder)
        assertTrue(binder is TetherForegroundService.LocalBinder)
        controller.destroy()
    }

    @Test
    fun `onStartCommand without stop action returns START_STICKY`() {
        val controller = Robolectric.buildService(TetherForegroundService::class.java).create()
        val result = controller.get().onStartCommand(Intent(), 0, 0)
        assertEquals(Service.START_STICKY, result)
        controller.destroy()
    }

    @Test
    fun `onStartCommand with ACTION_STOP returns START_NOT_STICKY and stops service`() {
        val controller = Robolectric.buildService(TetherForegroundService::class.java).create()
        val stopIntent = Intent().apply { action = ACTION_STOP }
        val result = controller.get().onStartCommand(stopIntent, 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
        controller.destroy()
    }

    @Test
    fun `onDestroy does not throw when server and discovery were never started`() {
        val controller = Robolectric.buildService(TetherForegroundService::class.java).create()
        controller.destroy()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onDestroy releases tracker locks while a transfer is still active`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = Robolectric.buildService(TetherForegroundService::class.java).create()
            val tracker = (controller.get().application as TetherApp).container.transferActivityTracker
            val barrier = CompletableDeferred<Unit>()
            val transfer = async { tracker.withActiveTransfer { barrier.await() } }
            val heldBeforeDestroy = ShadowPowerManager.getLatestWakeLock()?.isHeld == true
            controller.destroy()
            val heldAfterDestroy = ShadowPowerManager.getLatestWakeLock()?.isHeld == true
            barrier.complete(Unit)
            transfer.await()
            assertTrue(heldBeforeDestroy, "wake lock should be held during active transfer")
            assertFalse(heldAfterDestroy, "onDestroy must release locks via tracker.releaseAll()")
        }
}

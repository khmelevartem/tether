package com.tubetoast.tether.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.errorhandler.onDecomposeError
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.config.EphemeralDeviceNamePersistence
import com.tubetoast.tether.peer.FakePeersRepository
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FakeFilePicker
import com.tubetoast.tether.transfer.NoOpTransferActivityTracker
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.fakePeerTransferEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression guard for #421 — Decompose's `childStack` (built inside [RootComponent]'s
 * constructor) asserts it runs on the main thread and reports violations through the
 * process-global [onDecomposeError] hook rather than throwing.
 */
class RootComponentMainThreadTest {
    private lateinit var originalHandler: (Exception) -> Unit

    @BeforeTest
    fun setUp() {
        originalHandler = onDecomposeError
    }

    @AfterTest
    fun tearDown() {
        onDecomposeError = originalHandler
    }

    private fun buildFactory(scope: CoroutineScope): RootComponentFactory =
        RootComponentFactory(
            peersRepository = FakePeersRepository(),
            peerTransferEngineRegistry = fakePeerTransferEngineRegistry(scope),
            pendingFilesRepository = PendingFilesRepository(),
            peerConflictRelay = PeerConflictRelay(),
            filePicker = FakeFilePicker(result = emptyList()),
            fileTransferPreferences = FakeFileTransferPreferences(),
            nameStore = DeviceNameStore(EphemeralDeviceNamePersistence()),
            transferActivityTracker = NoOpTransferActivityTracker,
            ownDeviceType = DeviceType.Desktop,
        )

    private fun defaultContext(): DefaultComponentContext {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    @Test
    fun `constructing off the EDT trips Decompose's main-thread check`() {
        val captured = AtomicReference<Exception?>(null)
        onDecomposeError = { captured.set(it) }

        buildFactory(CoroutineScope(SupervisorJob())).create(defaultContext())

        val violation = assertNotNull(captured.get(), "expected a main-thread violation when built off the EDT")
        assertTrue(violation.message.orEmpty().contains("main thread", ignoreCase = true))
    }

    @Test
    fun `constructing on the Swing EDT raises no main-thread violation`() {
        val captured = AtomicReference<Exception?>(null)
        onDecomposeError = { captured.set(it) }

        SwingUtilities.invokeAndWait {
            buildFactory(CoroutineScope(SupervisorJob())).create(defaultContext())
        }

        assertNull(captured.get(), "expected no main-thread violation when built on the EDT")
    }
}

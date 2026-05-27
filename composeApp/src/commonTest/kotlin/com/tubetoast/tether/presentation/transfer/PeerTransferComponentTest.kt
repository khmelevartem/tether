package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PeerTransferComponentTest {
    private val peer = PeerIdentity("test-peer")

    private fun buildComponent(
        repository: FakePeerTransferRepository = FakePeerTransferRepository(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): PeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return PeerTransferComponent(
            componentContext = DefaultComponentContext(lifecycle),
            peer = peer,
            repository = repository,
            onShowDetailsCallback = {},
            coroutineScope = scope,
        )
    }

    @Test
    fun `state reflects repository emissions`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        repository.setState(peer, PeerTransferState.Idle(peer, expanded = true))
        runCurrent()

        val state = component.state.value
        assertIs<PeerTransferState.Idle>(state)
        assertEquals(true, state.expanded)
    }

    @Test
    fun `startOutbound delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)
        val sources = listOf(FakeFileSource("a.txt", 100L))

        component.startOutbound(sources)

        assertEquals(listOf<Pair<PeerIdentity, List<FileSource>>>(peer to sources), repository.startedOutbound)
    }

    @Test
    fun `onCancel delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.onCancel()

        assertEquals(listOf(peer), repository.cancelledPeers)
    }

    @Test
    fun `onRetry delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.onRetry()

        assertEquals(listOf(peer), repository.retriedPeers)
    }

    @Test
    fun `onRetryFile delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.onRetryFile("file.txt")

        assertEquals(listOf(peer to "file.txt"), repository.retriedFiles)
    }

    @Test
    fun `onCancelFile delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.onCancelFile("file.txt")

        assertEquals(listOf(peer to "file.txt"), repository.cancelledFiles)
    }

    @Test
    fun `onDismiss delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.onDismiss()

        assertEquals(listOf(peer), repository.dismissedPeers)
    }

    @Test
    fun `toggleExpanded delegates to repository`() = runTest {
        val repository = FakePeerTransferRepository()
        val component = buildComponent(repository = repository, scope = backgroundScope)

        component.toggleExpanded()

        assertEquals(listOf(peer), repository.toggledPeers)
    }
}

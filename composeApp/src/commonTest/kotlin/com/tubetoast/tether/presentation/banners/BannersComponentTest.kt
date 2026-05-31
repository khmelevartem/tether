package com.tubetoast.tether.presentation.banners

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BannersComponentTest {
    private fun buildComponent(
        repo: PendingFilesRepository = PendingFilesRepository(),
        coroutineScope: CoroutineScope,
    ): BannersComponent {
        val lifecycle = LifecycleRegistry().also { it.resume() }
        return BannersComponent(
            componentContext = DefaultComponentContext(lifecycle),
            pendingFilesRepository = repo,
            coroutineScope = coroutineScope,
        )
    }

    @Test
    fun `pendingSummary forwards repo summary`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, backgroundScope)

        val summary = PendingFilesSummary(2, 1024L)
        repo.setPending(summary, emptyList())

        assertTrue(component.pendingSummary.value == summary)
    }

    @Test
    fun `onCancelPending clears repo`() = runTest {
        val repo = PendingFilesRepository()
        val component = buildComponent(repo, backgroundScope)

        repo.setPending(PendingFilesSummary(1, 100L), emptyList())
        component.onCancelPending()

        assertNull(repo.pending.value?.summary)
    }

    @Test
    fun `dropFeedback is true during feedback window and false after`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        component.onDropDuringActiveTransfer()
        runCurrent()

        assertTrue(component.dropFeedback.value)
        advanceTimeBy(3_100L)

        assertFalse(component.dropFeedback.value)
    }

    @Test
    fun `second onDropDuringActiveTransfer within window restarts the timer`() = runTest {
        val component = buildComponent(coroutineScope = backgroundScope)

        component.onDropDuringActiveTransfer()
        runCurrent()
        advanceTimeBy(2_500L)
        assertTrue(component.dropFeedback.value)

        component.onDropDuringActiveTransfer()
        runCurrent()
        advanceTimeBy(2_500L)
        assertTrue(component.dropFeedback.value, "flag should still be true — second call restarted the window")

        advanceTimeBy(600L)
        assertFalse(component.dropFeedback.value)
    }
}

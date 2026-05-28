package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.presentation.transfer.TransferRegistry
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class PendingFilesSummary(
    val fileCount: Int,
    val totalBytes: Long,
) {
    companion object {
        val NONE = PendingFilesSummary(0, 0L)
    }
}

class RootComponent(
    componentContext: ComponentContext,
    private val deviceListFactory: (ComponentContext, TransferRegistry) -> DeviceListComponent,
    registryFactory: (onShowDetails: (PeerIdentity) -> Unit) -> TransferRegistry,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
    private val peerPreferencesStore: PeerPreferencesStore? = null,
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    private sealed interface Config {
        data object DeviceList : Config

        data class TransferDetails(
            val peer: PeerIdentity,
        ) : Config
    }

    private val navigation = StackNavigation<Config>()

    val registry: TransferRegistry = registryFactory(::showTransferDetails)

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.DeviceList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private val mutablePendingFiles = MutableValue(PendingFilesSummary.NONE)
    val pendingFiles: Value<PendingFilesSummary> = mutablePendingFiles

    private val mutablePendingSources = MutableValue<List<FileSource>>(emptyList())

    private val mutableDropFeedback = MutableValue(false)
    val dropFeedback: Value<Boolean> = mutableDropFeedback

    private var dropFeedbackJob: Job? = null

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.DeviceList -> Child.DeviceListChild(deviceListFactory(context, registry))
            is Config.TransferDetails -> Child.TransferDetailsChild(
                TransferDetailsComponent(
                    componentContext = context,
                    peerComponent = registry.get(config.peer),
                    onBack = { navigation.pop() },
                ),
            )
        }

    fun setPendingFiles(summary: PendingFilesSummary, sources: List<FileSource>) {
        mutablePendingFiles.value = summary
        mutablePendingSources.value = sources
    }

    fun clearPendingFiles() {
        mutablePendingFiles.value = PendingFilesSummary.NONE
        mutablePendingSources.value = emptyList()
    }

    fun onPeerTapped(peer: PeerIdentity) {
        val sources = mutablePendingSources.value
        if (sources.isEmpty()) return
        registry.get(peer).startOutbound(sources)
        clearPendingFiles()
    }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

    fun observeAutoSend(peer: PeerIdentity): Flow<Boolean> =
        peerPreferencesStore?.observeAutoSend(peer) ?: flowOf(false)

    fun setAutoSend(peer: PeerIdentity, enabled: Boolean) {
        val store = peerPreferencesStore ?: return
        scope.launch { store.setAutoSend(peer, enabled) }
    }

    fun onDropRejectedDuringTransfer() {
        dropFeedbackJob?.cancel()
        dropFeedbackJob = scope.launch {
            mutableDropFeedback.value = true
            delay(DROP_FEEDBACK_DURATION_MS)
            mutableDropFeedback.value = false
        }
    }

    sealed interface Child {
        data class DeviceListChild(
            val component: DeviceListComponent,
        ) : Child

        data class TransferDetailsChild(
            val component: TransferDetailsComponent,
        ) : Child
    }

    private companion object {
        const val DROP_FEEDBACK_DURATION_MS = 3_000L
    }
}

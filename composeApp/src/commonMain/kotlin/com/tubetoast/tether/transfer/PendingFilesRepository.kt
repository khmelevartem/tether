package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot the repository hands out as a single value so summary and sources are always
 * observed together — no consumer can see one without the other.
 */
data class Pending(
    val summary: PendingFilesSummary,
    val sources: List<FileSource>,
)

class PendingFilesRepository {
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun setPending(sources: List<FileSource>) {
        _pending.value = Pending(PendingFilesSummary.from(sources), sources)
    }

    /** Unconditional clear — for paths where the user explicitly dismisses pending. */
    fun clear() {
        _pending.value = null
    }

    /**
     * Use after consuming a specific batch — protects against dropping a fresh setPending
     * that arrived concurrently with the consumer. Pass the exact [Pending] reference read
     * from [pending] earlier; succeeds only if it is still the current snapshot.
     */
    fun clearIfMatches(token: Pending): Boolean = _pending.compareAndSet(token, null)
}

package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PendingFilesRepository {
    private val _summary = MutableStateFlow<PendingFilesSummary?>(null)
    val summary: StateFlow<PendingFilesSummary?> = _summary

    private val _sources = MutableStateFlow<List<FileSource>>(emptyList())
    val sources: StateFlow<List<FileSource>> = _sources

    fun setPending(summary: PendingFilesSummary, sources: List<FileSource>) {
        _summary.update { summary }
        _sources.update { sources }
    }

    /** Unconditional clear — for paths where the user explicitly dismisses pending. */
    fun clear() {
        _summary.update { null }
        _sources.update { emptyList() }
    }

    /**
     * Clear only if [expected] is still the current sources snapshot. Returns true on success.
     * Use after consuming a specific batch — protects against dropping a fresh setPending that
     * arrived concurrently with the consumer.
     */
    fun clearIfMatches(expected: List<FileSource>): Boolean {
        if (!_sources.compareAndSet(expected, emptyList())) return false
        _summary.update { null }
        return true
    }
}

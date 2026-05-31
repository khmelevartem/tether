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

    fun clear() {
        _summary.update { null }
        _sources.update { emptyList() }
    }
}

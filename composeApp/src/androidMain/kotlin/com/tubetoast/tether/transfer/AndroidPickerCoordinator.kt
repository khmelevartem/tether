package com.tubetoast.tether.transfer

import kotlinx.coroutines.CompletableDeferred

/**
 * Retains the in-flight pick deferred across Activity recreation. Lives in AndroidAppContainer.
 * If a new pick starts while one is already in flight, the prior deferred is cancelled.
 */
class AndroidPickerCoordinator {
    @Volatile var inFlight: CompletableDeferred<List<FileSource>>? = null
        private set

    fun begin(): CompletableDeferred<List<FileSource>> {
        inFlight?.cancel()
        val deferred = CompletableDeferred<List<FileSource>>()
        inFlight = deferred
        return deferred
    }

    fun resolve(sources: List<FileSource>) {
        inFlight?.complete(sources)
        inFlight = null
    }
}

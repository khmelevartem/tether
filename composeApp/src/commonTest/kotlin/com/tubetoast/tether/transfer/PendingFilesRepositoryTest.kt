package com.tubetoast.tether.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingFilesRepositoryTest {
    private val first = listOf(FakeFileSource("first.txt", 100L))
    private val second = listOf(FakeFileSource("second.txt", 200L))

    @Test
    fun `clear unconditionally drops pending`() {
        val repo = PendingFilesRepository()
        repo.setPending(first)
        repo.clear()
        assertNull(repo.pending.value)
    }

    @Test
    fun `clearIfMatches returns true and clears when token matches current snapshot`() {
        val repo = PendingFilesRepository()
        repo.setPending(first)
        val token = repo.pending.value!!
        assertTrue(repo.clearIfMatches(token))
        assertNull(repo.pending.value)
    }

    @Test
    fun `clearIfMatches returns false and preserves state when a fresh setPending raced ahead`() {
        val repo = PendingFilesRepository()
        repo.setPending(first)
        val token = repo.pending.value!!
        // Simulate the race: the caller captured `token` but a new share landed before clearIfMatches.
        repo.setPending(second)
        assertFalse(repo.clearIfMatches(token))
        assertEquals(second, repo.pending.value?.sources)
        assertNotNull(repo.pending.value?.summary)
    }

    @Test
    fun `setPending atomically pairs summary and sources`() {
        val repo = PendingFilesRepository()
        repo.setPending(first)
        val snapshot = repo.pending.value
        assertNotNull(snapshot)
        assertEquals(PendingFilesSummary.from(first), snapshot.summary)
        assertEquals(first, snapshot.sources)
    }
}

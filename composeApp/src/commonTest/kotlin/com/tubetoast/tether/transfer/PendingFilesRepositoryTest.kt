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
    fun `clear unconditionally drops sources and summary`() {
        val repo = PendingFilesRepository()
        repo.setPending(PendingFilesSummary(1, 100L), first)
        repo.clear()
        assertEquals(emptyList(), repo.sources.value)
        assertNull(repo.summary.value)
    }

    @Test
    fun `clearIfMatches returns true and clears when snapshot matches`() {
        val repo = PendingFilesRepository()
        repo.setPending(PendingFilesSummary(1, 100L), first)
        assertTrue(repo.clearIfMatches(first))
        assertEquals(emptyList(), repo.sources.value)
        assertNull(repo.summary.value)
    }

    @Test
    fun `clearIfMatches returns false and preserves state when a fresh setPending raced ahead`() {
        val repo = PendingFilesRepository()
        repo.setPending(PendingFilesSummary(1, 100L), first)
        // Simulate the race: the caller captured `first` but a new share landed before clearIfMatches.
        repo.setPending(PendingFilesSummary(1, 200L), second)
        assertFalse(repo.clearIfMatches(first))
        assertEquals(second, repo.sources.value)
        assertNotNull(repo.summary.value)
    }

    @Test
    fun `clearIfMatches against empty fails when something landed since the empty snapshot`() {
        val repo = PendingFilesRepository()
        repo.setPending(PendingFilesSummary(1, 100L), first)
        assertFalse(repo.clearIfMatches(emptyList()))
        assertEquals(first, repo.sources.value)
    }
}

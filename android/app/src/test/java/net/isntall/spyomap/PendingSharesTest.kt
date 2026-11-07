package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSharesTest {
    @Test
    fun drainReturnsAddedItemsInOrderAndEmptiesTheQueue() {
        val q = PendingShares<String>()
        q.add(listOf("a", "b"))
        assertEquals(listOf("a", "b"), q.drain())
        assertEquals(emptyList<String>(), q.drain())
    }

    @Test
    fun theSameShareDeliveredTwiceIsQueuedOnce() {
        val q = PendingShares<String>()
        assertTrue(q.add(listOf("a")))
        assertFalse(q.add(listOf("a")))
        assertEquals(listOf("a"), q.drain())
    }

    @Test
    fun addReportsWhetherAnythingNewWasQueued() {
        val q = PendingShares<String>()
        assertFalse(q.add(emptyList()))
        assertTrue(q.add(listOf("a", "b")))
        assertTrue(q.add(listOf("b", "c")))
        assertEquals(listOf("a", "b", "c"), q.drain())
    }

    @Test
    fun anItemCanBeSharedAgainAfterItWasDrained() {
        val q = PendingShares<String>()
        q.add(listOf("a"))
        q.drain()
        assertTrue(q.add(listOf("a")))
    }
}

package net.isntall.spyomap

/**
 * Photos shared to the app that the page hasn't collected yet. Android can deliver the same share intent twice
 * (seen in the spike), so items already waiting are ignored.
 */
class PendingShares<T> {
    private val items = LinkedHashSet<T>()

    /** Queues new items; returns true if anything was added. */
    @Synchronized
    fun add(new: List<T>): Boolean {
        var added = false
        for (item in new) added = items.add(item) || added
        return added
    }

    @Synchronized
    fun drain(): List<T> {
        val out = items.toList()
        items.clear()
        return out
    }
}

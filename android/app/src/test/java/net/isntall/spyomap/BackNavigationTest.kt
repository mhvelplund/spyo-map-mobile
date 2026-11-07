package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationTest {
    private val calls = mutableListOf<String>()

    private fun press(canGoBack: Boolean) =
        BackNavigation.onBack(canGoBack, goBack = { calls += "back" }, leave = { calls += "leave" })

    @Test
    fun goesBackInPageHistoryWhenPossible() {
        press(canGoBack = true)
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun leavesTheAppAtTheStartOfHistory() {
        press(canGoBack = false)
        assertEquals(listOf("leave"), calls)
    }
}

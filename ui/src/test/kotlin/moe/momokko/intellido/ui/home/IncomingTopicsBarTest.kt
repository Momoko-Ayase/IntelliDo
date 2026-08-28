package moe.momokko.intellido.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class IncomingTopicsBarTest {
    @Test
    fun `bar stays hidden until latest topics arrive and names the Discourse action`() {
        val clicks = AtomicInteger(0)
        val bar = IncomingTopicsBar(Locale.SIMPLIFIED_CHINESE) { clicks.incrementAndGet() }
        assertFalse(bar.isVisible)
        bar.setCount(1)
        assertTrue(bar.isVisible)
        assertEquals("查看 1 个新的或更新的话题", bar.text)
        bar.doClick()
        assertEquals(1, clicks.get())
        bar.setCount(0)
        assertFalse(bar.isVisible)
    }
}

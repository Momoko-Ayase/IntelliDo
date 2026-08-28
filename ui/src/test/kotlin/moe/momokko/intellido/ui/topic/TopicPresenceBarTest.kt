package moe.momokko.intellido.ui.topic

import moe.momokko.intellido.domain.live.LivePresenceUser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.util.Locale
import javax.swing.JLabel

class TopicPresenceBarTest {
    @Test
    fun `reply presence shows the Discourse typing copy`() {
        val bar = TopicPresenceBar(Locale.SIMPLIFIED_CHINESE)
        assertFalse(bar.isVisible)
        bar.showUsers(listOf(LivePresenceUser(2, "helper")))
        assertTrue(bar.isVisible)
        val texts = labels(bar)
        assertTrue(texts.any { it.contains("正在回复") }, texts.toString())
        bar.showUsers(emptyList())
        assertFalse(bar.isVisible)
    }

    private fun labels(root: Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: Component) {
            if (component is JLabel && component.text.isNotBlank()) {
                out += component.text
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }
}

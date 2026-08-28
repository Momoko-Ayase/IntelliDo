package moe.momokko.intellido.ui.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font

class WrapRunPaneTest {
    @Test
    fun `wrapped link paint runs start and stop at the anchor boundary`() {
        val font = Font(Font.SANS_SERIF, Font.PLAIN, 16)
        val href = "https://linux.do/t/topic/188585"
        val pane = WrapRunPane(
            atoms = listOf(
                InlineAtom(text = "恰如去年", font = font, color = Color.WHITE),
                InlineAtom(
                    text = "向左还是向右的路线问题",
                    font = font,
                    color = Color.ORANGE,
                    href = href,
                    underline = true,
                ),
                InlineAtom(text = "）：野蛮生长和压实深耕。", font = font, color = Color.WHITE),
            ),
            onNavigate = { true },
        )
        pane.measureHeight(180)
        val text = pane.layoutText()
        val linkStart = text.indexOf("向左")
        val linkEnd = linkStart + "向左还是向右的路线问题".length

        assertNull(pane.drawnHrefAt(linkStart - 1))
        assertEquals(href, pane.drawnHrefAt(linkStart))
        assertEquals(href, pane.drawnHrefAt(linkEnd - 1))
        assertNull(pane.drawnHrefAt(linkEnd))
    }

    private fun WrapRunPane.drawnHrefAt(index: Int): String? {
        val drawsField = WrapRunPane::class.java.getDeclaredField("draws").apply { isAccessible = true }
        val draws = drawsField.get(this) as List<*>
        val run = draws.firstOrNull { candidate ->
            candidate ?: return@firstOrNull false
            val type = candidate.javaClass
            val start = type.getDeclaredField("start").apply { isAccessible = true }.getInt(candidate)
            val end = type.getDeclaredField("end").apply { isAccessible = true }.getInt(candidate)
            index in start until end
        } ?: return null
        return run.javaClass.getDeclaredField("href").apply { isAccessible = true }.get(run) as String?
    }
}

package moe.momokko.intellido.ui.content

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import javax.swing.Scrollable

class PostCodePane(
    code: String,
    language: String? = null,
) : JBTextArea(code), Scrollable {
    init {
        isEditable = false
        isOpaque = true
        lineWrap = true
        wrapStyleWord = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = JBColor(0xF3F3F3, 0x2B2B2B)
        foreground = JBColor(0x2B2B2B, 0xD6D6D6)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8, 10),
        )
        alignmentX = LEFT_ALIGNMENT
        if (!language.isNullOrBlank()) {
            toolTipText = language
        }
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        visibleRect.height.coerceAtLeast(32)
}

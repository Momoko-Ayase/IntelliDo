package moe.momokko.intellido.ui.topic

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.SwingConstants

class TopicNewPostsBar(
    private val locale: Locale,
    onShow: () -> Unit = {},
) : JBLabel("", SwingConstants.CENTER) {
    init {
        isVisible = false
        isOpaque = true
        background = PILL
        foreground = Color.WHITE
        font = font.deriveFont(Font.PLAIN, 12f)
        border = JBUI.Borders.empty(4, 16)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount >= 1) {
                    onShow()
                }
            }
        })
        getAccessibleContext().accessibleName = IntelliDoStrings.message("topic.newPosts", locale, 0)
    }

    fun doClick() {
        mouseListeners.forEach { listener ->
            listener.mouseClicked(
                MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 2, 1, false),
            )
        }
    }

    fun setCount(count: Int) {
        isVisible = count > 0
        if (count > 0) {
            text = IntelliDoStrings.message("topic.newPosts", locale, count)
            getAccessibleContext().accessibleName = text
        }
    }

    companion object {
        val PILL: JBColor = JBColor(0x3E88C7, 0x3E88C7)
    }
}

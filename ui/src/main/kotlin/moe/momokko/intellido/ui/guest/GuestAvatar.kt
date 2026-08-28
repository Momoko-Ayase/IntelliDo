package moe.momokko.intellido.ui.guest

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.MediaUrls
import java.awt.BorderLayout
import java.awt.Dimension

/**
 * Initials until LINUX DO avatar bytes arrive.
 */
class GuestAvatar(
    username: String,
    private val url: String?,
    private val sizePx: Int = InitialsAvatar.SIZE,
    endGap: Int = 0,
) : JBPanel<GuestAvatar>(BorderLayout()) {
    private var painted: Boolean = false

    init {
        isOpaque = false
        if (endGap > 0) {
            border = JBUI.Borders.empty(0, 0, 0, endGap)
        }
        add(InitialsAvatar(username, sizePx), BorderLayout.NORTH)
        val size = JBUI.scale(sizePx)
        val extra = if (endGap > 0) JBUI.scale(endGap) else 0
        preferredSize = Dimension(size + extra, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        toolTipText = username
        alignmentY = TOP_ALIGNMENT
    }

    override fun addMouseListener(l: java.awt.event.MouseListener) {
        super.addMouseListener(l)
        components.forEach { it.addMouseListener(l) }
    }

    fun apply(bytesByUrl: Map<String, ByteArray>) {
        if (painted) {
            return
        }
        val bytes = url?.let { want ->
            bytesByUrl.entries.firstOrNull { MediaUrls.key(it.key) == MediaUrls.key(want) }?.value
        } ?: return
        painted = true
        removeAll()
        val child = CircleAvatar(bytes, sizePx)
        add(child, BorderLayout.NORTH)
        mouseListeners.forEach { child.addMouseListener(it) }
        revalidate()
        repaint()
    }
}

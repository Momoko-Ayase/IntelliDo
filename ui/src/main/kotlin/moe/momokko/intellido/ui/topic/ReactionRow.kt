package moe.momokko.intellido.ui.topic

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.topic.PostReaction
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.ui.guest.EmojiMark
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Overlapping reaction emojis plus the unique-user count, as on linux.do.
 */
class ReactionRow(
    reactions: List<PostReaction>,
    total: Int,
) : JBPanel<ReactionRow>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)) {
    init {
        isOpaque = false
        val shown = reactions.filter { it.count > 0 }.take(MAX)
        if (shown.isNotEmpty()) {
            add(EmojiStack(shown))
        }
        if (total > 0) {
            val count = JBLabel(DiscourseNumber.compact(total))
            count.foreground = GuestUi.muted
            count.font = GuestUi.metaFont(count.font)
            add(count)
        }
    }

    private class EmojiStack(
        private val reactions: List<PostReaction>,
    ) : JBPanel<EmojiStack>(null) {
        private val marks: List<EmojiMark> = reactions.map { EmojiMark(it.id, 18) }
            .filter { EmojiMark.load(it.shortcode) != null }

        init {
            isOpaque = false
            marks.forEach(::add)
            val size = JBUI.scale(18)
            val width = if (marks.isEmpty()) 0 else size + (marks.size - 1) * JBUI.scale(STEP)
            preferredSize = Dimension(width, size)
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = reactions.joinToString(" · ") { "${it.id} × ${it.count}" }
        }

        override fun doLayout() {
            val size = JBUI.scale(18)
            val step = JBUI.scale(STEP)
            marks.forEachIndexed { index, mark ->
                mark.setBounds(index * step, 0, size, size)
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val size = JBUI.scale(18)
            val step = JBUI.scale(STEP)
            g2.color = com.intellij.ui.JBColor.background()
            marks.forEachIndexed { index, _ ->
                val x = index * step
                g2.fillOval(x, 0, size, size)
            }
        }

        override fun paintChildren(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            super.paintChildren(g2)
        }
    }

    companion object {
        const val MAX: Int = 6
        const val STEP: Int = 14
    }
}

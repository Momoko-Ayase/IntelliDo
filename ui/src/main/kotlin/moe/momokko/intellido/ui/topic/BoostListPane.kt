package moe.momokko.intellido.ui.topic

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.EmojiShortcodes
import moe.momokko.intellido.domain.topic.BoostContent
import moe.momokko.intellido.domain.topic.PostBoost
import moe.momokko.intellido.ui.guest.ChipWrap
import moe.momokko.intellido.ui.guest.EmojiMark
import moe.momokko.intellido.ui.guest.GuestAvatar
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * Read-only discourse-boosts chips under a post. Guests cannot add a Boost.
 */
class BoostListPane(
    boosts: List<PostBoost>,
) : ChipWrap() {
    private val avatars = mutableListOf<GuestAvatar>()

    init {
        border = JBUI.Borders.empty(6, 0, 0, 0)
        isVisible = boosts.isNotEmpty()
        boosts.forEach { boost ->
            val chip = BoostChip(boost)
            avatars += chip.avatar
            add(chip)
        }
    }

    fun applyMedia(bytesByUrl: Map<String, ByteArray>) {
        avatars.forEach { it.apply(bytesByUrl) }
    }

    class BoostChip(
        boost: PostBoost,
    ) : JBPanel<BoostChip>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)) {
        val avatar: GuestAvatar = GuestAvatar(boost.username, boost.avatarUrl(48), 20)

        init {
            isOpaque = false
            border = JBUI.Borders.empty(2, 2, 2, 6)
            add(avatar)
            add(boostLabel(BoostContent.parse(boost.cookedHtml).displayText))
            toolTipText = boost.username
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = height.coerceAtLeast(8).toFloat()
            g2.color = PILL
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
        }

        override fun getMaximumSize(): Dimension = preferredSize
        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            return Dimension(size.width.coerceAtMost(JBUI.scale(280)), size.height.coerceAtLeast(JBUI.scale(24)))
        }

        companion object {
            val PILL: JBColor = JBColor(0xF2F2F2, 0x3A3A3A)
        }
    }

    companion object {
        private val SHORTCODE = Regex(":([a-zA-Z0-9_+-]+):")

        fun boostLabel(display: String): JComponent {
            val glyph = SHORTCODE.replace(display) { match ->
                val name = match.groupValues[1]
                EmojiShortcodes.glyph(name) ?: match.value
            }
            if (glyph == display && SHORTCODE.containsMatchIn(display)) {
                val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
                row.isOpaque = false
                var cursor = 0
                SHORTCODE.findAll(display).forEach { match ->
                    if (match.range.first > cursor) {
                        row.add(chipText(display.substring(cursor, match.range.first)))
                    }
                    val mark = EmojiMark(match.groupValues[1], 14)
                    if (mark.preferredSize.width > 0 && EmojiMark.load(match.groupValues[1]) != null) {
                        row.add(mark)
                    } else {
                        row.add(chipText(match.value))
                    }
                    cursor = match.range.last + 1
                }
                if (cursor < display.length) {
                    row.add(chipText(display.substring(cursor)))
                }
                return row
            }
            return chipText(glyph)
        }

        private fun chipText(text: String): JBLabel {
            val label = JBLabel(text)
            label.font = label.font.deriveFont(Font.PLAIN, 12f)
            label.foreground = UIUtil.getLabelForeground()
            return label
        }
    }
}

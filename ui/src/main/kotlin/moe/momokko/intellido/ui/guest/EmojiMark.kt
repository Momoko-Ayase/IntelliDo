package moe.momokko.intellido.ui.guest

import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.TwemojiAssets
import moe.momokko.intellido.ui.content.InlineMedia
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import javax.swing.ImageIcon
import javax.swing.JComponent

/**
 * Local Twemoji (or already-fetched custom emoji) at a Discourse reaction size.
 */
class EmojiMark(
    val shortcode: String,
    sizePx: Int = SIZE,
    src: String = "",
) : JComponent() {
    private val edge: Int = JBUI.scale(sizePx.coerceAtLeast(8))
    private val image: Image? = load(shortcode, src)

    init {
        isOpaque = false
        preferredSize = Dimension(edge, edge)
        minimumSize = preferredSize
        maximumSize = preferredSize
        toolTipText = shortcode.removeSurrounding(":")
        alignmentY = CENTER_ALIGNMENT
    }

    override fun paintComponent(g: Graphics) {
        val drawn = image ?: return
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val dest = edge.coerceAtMost(minOf(width, height).coerceAtLeast(1))
        val x = (width - dest) / 2
        val y = (height - dest) / 2
        g2.drawImage(drawn, x, y, dest, dest, null)
    }

    companion object {
        const val SIZE: Int = 16

        fun load(shortcode: String, src: String = ""): Image? {
            val bytes = TwemojiAssets.bytes(shortcode, src)
            if (bytes != null) {
                return ImageIcon(bytes).image
            }
            if (src.isNotBlank()) {
                InlineMedia.image(src)?.let { return it }
                InlineMedia.image(InlineMedia.key(src))?.let { return it }
            }
            return null
        }
    }
}

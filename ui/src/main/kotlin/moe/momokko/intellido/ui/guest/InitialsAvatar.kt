package moe.momokko.intellido.ui.guest

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * Native stand-in for Discourse's 45px topic-avatar until LINUX DO media loads in-page.
 */
class InitialsAvatar(
    username: String,
    sizePx: Int = SIZE,
) : JComponent() {
    private val letter: String = username.trim().first().uppercaseChar().toString()
    private val fill: Color = GuestUi.avatarFill(username)
    private val sizePx: Int = sizePx.coerceAtLeast(8)

    init {
        isOpaque = false
        val size = JBUI.scale(this.sizePx)
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        alignmentY = TOP_ALIGNMENT
        toolTipText = username
    }

    override fun paintComponent(g: Graphics) {
        val size = width.coerceAtMost(height).coerceAtLeast(1)
        val superSize = size * 2
        val buffer = java.awt.image.BufferedImage(superSize, superSize, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val bg = buffer.createGraphics()
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        bg.color = fill
        bg.fill(java.awt.geom.Ellipse2D.Double(1.0, 1.0, (superSize - 2).toDouble(), (superSize - 2).toDouble()))
        bg.color = Color.WHITE
        bg.font = font.deriveFont(Font.BOLD, JBUI.scale((this.sizePx * 0.7).toInt().coerceAtLeast(8)).toFloat())
        val fm = bg.fontMetrics
        val x = (superSize - fm.stringWidth(letter)) / 2
        val y = (superSize - fm.height) / 2 + fm.ascent
        bg.drawString(letter, x, y)
        bg.dispose()
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.drawImage(buffer, 0, 0, size, size, null)
    }

    companion object {
        const val SIZE: Int = 45
    }
}

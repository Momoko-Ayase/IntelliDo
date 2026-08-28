package moe.momokko.intellido.ui.guest

import com.intellij.ui.svg.JSvgDocument
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.icon.FaGlyphs
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

class FaMark(
    val iconName: String,
    color: Color,
    sizePx: Int = SIZE,
) : javax.swing.JComponent() {
    private val edge: Int = JBUI.scale(sizePx.coerceAtLeast(8))
    private val image: BufferedImage? = FaSvg.raster(iconName, color, edge)

    init {
        isOpaque = false
        preferredSize = Dimension(edge, edge)
        minimumSize = preferredSize
        maximumSize = preferredSize
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
        const val SIZE: Int = 12
    }
}

object FaSvg {
    private val cache = ConcurrentHashMap<String, BufferedImage>()

    fun raster(name: String, color: Color, size: Int): BufferedImage? {
        val glyph = FaGlyphs.get(name) ?: return null
        val hex = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
        val key = "$name|$hex|$size"
        return cache[key] ?: runCatching {
            val doc = JSvgDocument.create(glyph.svg(hex).toByteArray(Charsets.UTF_8))
            doc.createImage(size, size)
        }.getOrNull()?.also { cache[key] = it }
    }
}

package moe.momokko.intellido.ui.content

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import moe.momokko.intellido.domain.icon.LinuxDoTagIcons
import moe.momokko.intellido.ui.guest.FaSvg
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Paints LINUX DO hashtag chips as HiDPI inline images. Swing HTML 3.2 cannot
 * round-rect a real inline run (the `<a>` is flattened to sibling views).
 */
object HashtagChips {
    val BACKGROUND: JBColor = JBColor(0xF2F2F2, 0x3A3A3A)

    fun register() {
        val height = JBUI.scale(HEIGHT)
        val font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 12f)
        val iconSize = JBUI.scale(12)
        LinuxDoTagIcons.all().forEach { (name, style) ->
            val image = raster(name, style, font, height, iconSize) ?: return@forEach
            InlineMedia.putImage("chip:$name", image)
            InlineMedia.putImage("intellido-media:chip:$name", image)
        }
    }

    private fun raster(
        name: String,
        style: LinuxDoTagIcons.Style,
        font: Font,
        height: Int,
        iconSize: Int,
    ): BufferedImage? {
        val accent = runCatching { Color(Integer.parseUnsignedInt(style.color, 16)) }.getOrNull() ?: return null
        val sys = JBUIScale.sysScale().toDouble().coerceAtLeast(1.0)
        val iconPx = (iconSize * sys).roundToInt().coerceAtLeast(iconSize)
        val icon = FaSvg.raster(style.icon, accent, iconPx)
        val probe = ImageUtil.createImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics()
        probe.font = font
        val fm = probe.fontMetrics
        probe.dispose()
        val padX = JBUI.scale(6)
        val gap = JBUI.scale(4)
        val width = (padX + (if (icon == null) 0 else iconSize + gap) + fm.stringWidth(name) + padX)
            .coerceAtLeast(height)
        val image = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.color = BACKGROUND
        g2.fill(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, height.toFloat(), height.toFloat()))
        var x = padX
        if (icon != null) {
            val iy = ((height - iconSize) / 2).coerceAtLeast(0)
            g2.drawImage(icon, x, iy, iconSize, iconSize, null)
            x += iconSize + gap
        }
        g2.font = font
        g2.color = accent
        g2.drawString(name, x.toFloat(), ((height - fm.height) / 2 + fm.ascent).toFloat())
        g2.dispose()
        return image
    }

    const val HEIGHT: Int = 18
}

package moe.momokko.intellido.ui.guest

import com.intellij.util.ui.JBUI
import moe.momokko.intellido.ui.jcef.GifBytes
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import javax.swing.ImageIcon
import javax.swing.JComponent

/**
 * 45px circular avatar. GIF frames keep animating via [ImageObserver].
 */
class CircleAvatar(
    bytes: ByteArray,
    sizePx: Int = InitialsAvatar.SIZE,
) : JComponent(), ImageObserver {
    private val source: Image = ImageIcon(bytes).image
    private val animated: Boolean = GifBytes.isGif(bytes)

    init {
        isOpaque = false
        val size = JBUI.scale(sizePx.coerceAtLeast(8))
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        alignmentY = TOP_ALIGNMENT
    }

    override fun paintComponent(g: Graphics) {
        val size = width.coerceAtMost(height).coerceAtLeast(1)
        val superSize = size * 2
        val buffer = BufferedImage(superSize, superSize, BufferedImage.TYPE_INT_ARGB)
        val bg = buffer.createGraphics()
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        bg.clip(Ellipse2D.Double(1.0, 1.0, (superSize - 2).toDouble(), (superSize - 2).toDouble()))
        bg.drawImage(source, 0, 0, superSize, superSize, this)
        bg.dispose()
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.drawImage(buffer, 0, 0, size, size, null)
    }

    override fun imageUpdate(img: Image, infoflags: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (infoflags and (ImageObserver.FRAMEBITS or ImageObserver.ALLBITS or ImageObserver.SOMEBITS) != 0) {
            repaint()
        }
        return animated && infoflags and ImageObserver.ALLBITS == 0
    }
}

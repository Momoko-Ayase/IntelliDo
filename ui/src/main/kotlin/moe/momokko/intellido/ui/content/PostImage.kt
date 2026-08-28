package moe.momokko.intellido.ui.content

import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ImageObserver
import javax.swing.ImageIcon
import javax.swing.JComponent

class PostImage(
    private val bytes: ByteArray,
    private val originalUrl: String = "",
    private val loadOriginal: (String, (ByteArray?) -> Unit) -> Unit = { _, done -> done(null) },
) : JComponent(), ImageObserver {
    private val source: Image = ImageIcon(bytes).image
    private var opening: Boolean = false

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        source.getWidth(this)
        source.getHeight(this)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    open()
                }
            }
        })
    }

    private fun open() {
        if (opening) {
            return
        }
        val target = originalUrl.trim()
        if (target.isEmpty()) {
            PostImages.open(this, bytes)
            return
        }
        opening = true
        cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        loadOriginal(target) { original ->
            opening = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            PostImages.open(this, original ?: bytes)
        }
    }

    override fun getPreferredSize(): Dimension {
        val naturalW = source.getWidth(this)
        val naturalH = source.getHeight(this)
        if (naturalW <= 0 || naturalH <= 0) {
            return Dimension(JBUI.scale(120), JBUI.scale(80))
        }
        val maxW = scaledMaxWidth()
        val scale = (maxW.toDouble() / naturalW).coerceAtMost(1.0)
        return Dimension((naturalW * scale).toInt().coerceAtLeast(1), (naturalH * scale).toInt().coerceAtLeast(1))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val naturalW = source.getWidth(this)
        val naturalH = source.getHeight(this)
        if (naturalW <= 0 || naturalH <= 0) {
            return
        }
        val dest = preferredSize
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.drawImage(source, 0, 0, dest.width, dest.height, this)
    }

    override fun imageUpdate(img: Image, infoflags: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (infoflags and (ImageObserver.WIDTH or ImageObserver.HEIGHT or ImageObserver.FRAMEBITS or ImageObserver.ALLBITS) != 0) {
            revalidate()
            repaint()
        }
        return infoflags and ImageObserver.ALLBITS == 0
    }

    private fun scaledMaxWidth(): Int {
        val parentW = parent?.width ?: 0
        val cap = JBUI.scale(MAX_WIDTH)
        return if (parentW > 40) parentW.coerceAtMost(cap) else cap
    }

    companion object {
        const val MAX_WIDTH: Int = 720
    }
}

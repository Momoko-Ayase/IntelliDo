package moe.momokko.intellido.ui.topic

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import javax.swing.JComponent

/**
 * Discourse-style placeholder for a reply that is in the stream but not loaded yet.
 */
class PostSkeleton(locale: Locale) : JComponent() {
    private val accessibleLabel: String = IntelliDoStrings.message("topic.placeholder.replies", locale)

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        val height = JBUI.scale(HEIGHT)
        preferredSize = Dimension(JBUI.scale(640), height)
        minimumSize = Dimension(JBUI.scale(120), height)
        maximumSize = Dimension(Integer.MAX_VALUE, height)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(16, 0, 12, 0),
        )
    }

    override fun addNotify() {
        super.addNotify()
        getAccessibleContext()?.accessibleName = accessibleLabel
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, JBUI.scale(HEIGHT))

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val ins = insets
        val x = ins.left
        val y = ins.top
        val innerH = (height - ins.top - ins.bottom).coerceAtLeast(1)
        val innerW = (width - ins.left - ins.right).coerceAtLeast(1)
        g2.color = FILL
        val avatar = JBUI.scale(45).toDouble()
        g2.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), avatar, avatar))
        val barX = x + avatar + JBUI.scale(12)
        val barMax = (innerW - avatar - JBUI.scale(16)).coerceAtLeast(24.0)
        val barH = JBUI.scale(8).toDouble()
        val arc = JBUI.scale(8).toDouble()
        val rows = floatArrayOf(0.42f, 0.92f, 0.84f, 0.55f)
        rows.forEachIndexed { index, fraction ->
            val top = y + JBUI.scale(4) + index * (barH + JBUI.scale(8))
            if (top + barH <= y + innerH) {
                g2.fill(
                    RoundRectangle2D.Double(barX, top, barMax * fraction, barH, arc, arc),
                )
            }
        }
    }

    companion object {
        const val HEIGHT: Int = 108
        val FILL: JBColor = JBColor(Color(0, 0, 0, 28), Color(255, 255, 255, 26))
    }
}

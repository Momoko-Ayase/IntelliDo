package moe.momokko.intellido.ui.guest

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.Timer

/**
 * Bottom-of-list spinner matching LINUX DO infinite-scroll chrome.
 */
class LoadPulse(accessibleName: String) : JBPanel<LoadPulse>(FlowLayout(FlowLayout.CENTER, 0, JBUI.scale(8))) {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        getAccessibleContext().accessibleName = accessibleName
        add(Mark())
        val height = JBUI.scale(48)
        preferredSize = Dimension(JBUI.scale(120), height)
        minimumSize = Dimension(JBUI.scale(48), height)
        maximumSize = Dimension(Integer.MAX_VALUE, height)
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, JBUI.scale(48))

    private class Mark : JComponent() {
        private var angle: Int = 0
        private val timer = Timer(16) {
            angle = (angle + 8) % 360
            repaint()
        }

        init {
            isOpaque = false
            val size = JBUI.scale(SIZE)
            preferredSize = Dimension(size, size)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun addNotify() {
            super.addNotify()
            if (!timer.isRunning) {
                timer.start()
            }
        }

        override fun removeNotify() {
            timer.stop()
            super.removeNotify()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val stroke = JBUI.scale(2).toFloat()
            val pad = stroke
            val size = width.coerceAtMost(height).toFloat() - pad * 2
            g2.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.color = TRACK
            g2.drawArc(pad.toInt(), pad.toInt(), size.toInt(), size.toInt(), 0, 360)
            g2.color = GuestUi.muted
            g2.drawArc(pad.toInt(), pad.toInt(), size.toInt(), size.toInt(), -angle, 80)
        }
    }

    companion object {
        const val SIZE: Int = 22
        val TRACK: JBColor = JBColor(0xE4E4E4, 0x3A3A3A)
    }
}

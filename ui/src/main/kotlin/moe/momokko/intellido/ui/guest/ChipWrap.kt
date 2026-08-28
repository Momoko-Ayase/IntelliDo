package moe.momokko.intellido.ui.guest

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import javax.swing.JViewport

/**
 * Left-aligned wrapping row used for Boost chips and similar Discourse chrome.
 */
open class ChipWrap(
    private val hgap: Int = JBUI.scale(4),
    private val vgap: Int = JBUI.scale(4),
) : JBPanel<ChipWrap>(null) {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
    }

    override fun doLayout() {
        place(innerWidth()) { child, x, y, w, h ->
            child.setBounds(x, y, w, h)
        }
    }

    override fun getPreferredSize(): Dimension {
        val target = innerWidth().coerceAtLeast(JBUI.scale(80))
        var bottom = 0
        place(target) { _, _, y, _, h -> bottom = maxOf(bottom, y + h) }
        return Dimension(target, (bottom + insets.bottom).coerceAtLeast(JBUI.scale(24)))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    private fun innerWidth(): Int {
        if (width > 0) {
            return width
        }
        val parent = parent
        if (parent is JViewport && parent.extentSize.width > 0) {
            return parent.extentSize.width
        }
        if (parent != null && parent.width > 0) {
            return parent.width
        }
        return JBUI.scale(200)
    }

    private fun place(
        target: Int,
        visit: (Component, Int, Int, Int, Int) -> Unit,
    ) {
        val ins = insets
        val maxW = (target - ins.left - ins.right).coerceAtLeast(JBUI.scale(40))
        var x = ins.left
        var y = ins.top
        var rowH = 0
        components.forEach { child ->
            val d = child.preferredSize
            val right = ins.left + maxW
            if (x > ins.left && x + d.width + JBUI.scale(16) > right) {
                y += rowH + vgap
                x = ins.left
                rowH = 0
            }
            visit(child, x, y, d.width, d.height)
            x += d.width + hgap
            rowH = maxOf(rowH, d.height)
        }
    }
}

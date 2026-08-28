package moe.momokko.intellido.ui.topic

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.Locale
import javax.swing.JButton

class TopicFindBar(
    private val locale: Locale,
    private val onQuery: (String) -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onClose: () -> Unit,
) : JBPanel<TopicFindBar>(BorderLayout()) {
    private val field = JBTextField()
    private val status = JBLabel()

    init {
        isVisible = false
        isOpaque = false
        border = JBUI.Borders.empty(4, 0, 8, 0)
        field.emptyText.text = IntelliDoStrings.message("topic.find", locale)
        field.getAccessibleContext().accessibleName = IntelliDoStrings.message("topic.find", locale)
        field.addActionListener { onQuery(field.text); onNext() }
        val tools = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
        tools.isOpaque = false
        val prev = JButton(IntelliDoStrings.message("topic.find.previous", locale))
        prev.addActionListener { onPrevious() }
        val next = JButton(IntelliDoStrings.message("topic.find.next", locale))
        next.addActionListener { onNext() }
        val close = JButton(IntelliDoStrings.message("browse.close", locale))
        close.addActionListener { onClose() }
        tools.add(prev)
        tools.add(next)
        tools.add(status)
        tools.add(close)
        add(field, BorderLayout.CENTER)
        add(tools, BorderLayout.EAST)
    }

    fun showBar() {
        isVisible = true
        field.requestFocusInWindow()
        field.selectAll()
    }

    fun hideBar() {
        isVisible = false
        field.text = ""
        status.text = ""
    }

    fun query(): String = field.text

    fun setStatus(index: Int, total: Int) {
        status.text = if (total <= 0) {
            IntelliDoStrings.message("topic.find.empty", locale)
        } else {
            IntelliDoStrings.message("topic.find.count", locale, index + 1, total)
        }
    }
}

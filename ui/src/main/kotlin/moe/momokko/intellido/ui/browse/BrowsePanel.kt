package moe.momokko.intellido.ui.browse

import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JComponent
import javax.swing.text.DefaultCaret

class BrowsePanel(
    initialUrl: String,
    private val locale: Locale,
    private val view: BrowseView,
    private val onOpenExternal: (String) -> Unit = {},
) : JBPanel<BrowsePanel>(BorderLayout()) {
    private val urlField = JBTextField()

    init {
        border = JBUI.Borders.empty(6, 8, 6, 8)
        val nav = cluster()
        nav.add(iconButton("browse.back", "circle-left") { view.goBack(); refreshOrigin() })
        nav.add(iconButton("browse.forward", "circle-right") { view.goForward(); refreshOrigin() })
        val actions = cluster()
        actions.add(iconButton("browse.reload", "rotate-right") { view.reload(); refreshOrigin() })
        actions.add(iconButton("browse.openExternal", "square-arrow-up-right") { onOpenExternal(view.currentUrl()) })
        urlField.isEditable = false
        urlField.isOpaque = false
        urlField.border = JBUI.Borders.empty(0, 10, 0, 10)
        urlField.foreground = GuestUi.muted
        urlField.getAccessibleContext().accessibleName = IntelliDoStrings.message("browse.origin", locale)
        (urlField.caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
        val bar = JBPanel<JBPanel<*>>(BorderLayout())
        bar.isOpaque = false
        bar.add(nav, BorderLayout.WEST)
        bar.add(urlField, BorderLayout.CENTER)
        bar.add(actions, BorderLayout.EAST)
        add(bar, BorderLayout.NORTH)
        add(view.component(), BorderLayout.CENTER)
        urlField.text = initialUrl
        view.load(initialUrl)
        refreshOrigin()
    }

    fun displayedUrl(): String = urlField.text

    fun urlField(): JBTextField = urlField

    fun refreshOrigin() {
        val next = view.currentUrl().ifBlank { urlField.text }
        if (next.isNotBlank()) {
            urlField.text = next
        }
    }

    private fun cluster(): JBPanel<*> {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 2, 0))
        row.isOpaque = false
        return row
    }

    private fun iconButton(key: String, icon: String, action: () -> Unit): JComponent {
        val name = IntelliDoStrings.message(key, locale)
        val hit = JBUI.scale(HIT)
        val button = JBPanel<JBPanel<*>>(GridBagLayout())
        button.isOpaque = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.toolTipText = name
        button.getAccessibleContext().accessibleName = name
        button.putClientProperty("intellido.tool", name)
        button.preferredSize = Dimension(hit, hit)
        button.minimumSize = button.preferredSize
        button.maximumSize = button.preferredSize
        button.add(FaMark(icon, GuestUi.muted, ICON))
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount >= 1) {
                    action()
                }
            }
        })
        return button
    }

    companion object {
        const val HIT: Int = 28
        const val ICON: Int = 16
    }
}

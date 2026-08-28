package moe.momokko.intellido.ui.welcome

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.BorderLayout
import java.util.Locale

class WelcomePanel(
    locale: Locale,
) : JBPanel<WelcomePanel>(BorderLayout()) {
    init {
        border = JBUI.Borders.empty(24)
        val content = JBLabel()
        content.text = """
            <html>
            <h2>${escape(IntelliDoStrings.message("welcome.title", locale))}</h2>
            <p>${escape(IntelliDoStrings.message("welcome.unofficial", locale))}</p>
            <p>${escape(IntelliDoStrings.message("welcome.privacy", locale))}</p>
            </html>
        """.trimIndent()
        content.foreground = JBColor.foreground()
        add(content, BorderLayout.NORTH)
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

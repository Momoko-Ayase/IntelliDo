package moe.momokko.intellido.ui.home

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.guest.LoadPulse
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Locale

/**
 * Pinned spinner under Home while the next topic page is in flight.
 */
class HomeLoadPlaceholder(locale: Locale) : JBPanel<HomeLoadPlaceholder>(BorderLayout()) {
    init {
        isOpaque = false
        isVisible = false
        val name = IntelliDoStrings.message("home.loadingMore", locale)
        getAccessibleContext().accessibleName = name
        add(LoadPulse(name), BorderLayout.CENTER)
        val height = JBUI.scale(56)
        preferredSize = Dimension(JBUI.scale(120), height)
        minimumSize = Dimension(JBUI.scale(48), height)
    }
}

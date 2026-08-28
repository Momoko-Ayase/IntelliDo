package moe.momokko.intellido.ui.browse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel

class BrowsePanelTest {
    @Test
    fun `restricted chrome shows origin and never an address bar`() {
        val view = FakeBrowseView("https://connect.linux.do/progress")
        val panel = BrowsePanel(view.url, Locale.SIMPLIFIED_CHINESE, view)
        assertEquals("https://connect.linux.do/progress", panel.displayedUrl())
        assertEquals(
            listOf("circle-left", "circle-right", "rotate-right", "square-arrow-up-right"),
            icons(panel),
        )
        assertEquals(listOf("后退", "前进", "重新载入", "在外部打开"), names(panel))
        assertTrue(panel.urlField().isEditable.not())
        assertTrue(icons(panel).none { it.contains("http") }, icons(panel).toString())
    }

    private fun icons(root: java.awt.Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: java.awt.Component) {
            if (component is moe.momokko.intellido.ui.guest.FaMark) {
                out += component.iconName
            }
            if (component is java.awt.Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private fun names(root: java.awt.Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: java.awt.Component) {
            val name = (component as? javax.swing.JComponent)?.getClientProperty("intellido.tool") as? String
            if (name != null) {
                out += name
            }
            if (component is java.awt.Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private class FakeBrowseView(var url: String) : BrowseView {
        override fun component(): JComponent = JPanel()
        override fun load(url: String) {
            this.url = url
        }
        override fun goBack() = Unit
        override fun goForward() = Unit
        override fun reload() = Unit
        override fun canGoBack(): Boolean = false
        override fun canGoForward(): Boolean = false
        override fun currentUrl(): String = url
        override fun currentOrigin(): String = "https://connect.linux.do"
        override fun dispose() = Unit
    }
}

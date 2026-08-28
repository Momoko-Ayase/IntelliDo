package moe.momokko.intellido.ui.home

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.Locale
import javax.swing.JTextField

class HomeLoadPlaceholderTest {
    @Test
    fun `home list keeps a hidden paging spinner for unloaded topics`() {
        val panel = HomePanel(Locale.SIMPLIFIED_CHINESE)
        val placeholder = find(panel, HomeLoadPlaceholder::class.java) ?: error("home paging placeholder missing")
        assertFalse(placeholder.isVisible)
        assertTrue(placeholder.preferredSize.height >= 32, "height=${placeholder.preferredSize.height}")
        val name = placeholder.getAccessibleContext().accessibleName.orEmpty()
        assertTrue(name.contains("加载"), name)
    }

    @Test
    fun `guest home has no search field and centers the incoming pill`() {
        val panel = HomePanel(Locale.SIMPLIFIED_CHINESE)
        assertNull(find(panel, JTextField::class.java), "Search Everywhere replaces the home search field")
        val bar = find(panel, IncomingTopicsBar::class.java) ?: error("incoming bar missing")
        bar.setCount(27)
        bar.parent?.isVisible = true
        panel.size = Dimension(1000, 600)
        layoutTree(panel)
        assertTrue(bar.isVisible)
        val x = absoluteX(bar, panel)
        assertTrue(x > 200, "incoming pill too far left: x=$x")
        assertTrue(x + bar.width < 800, "incoming pill too far right: x=$x width=${bar.width}")
    }

    private fun <T : Component> find(root: Component, type: Class<T>): T? {
        if (type.isInstance(root)) {
            return type.cast(root)
        }
        if (root is Container) {
            root.components.forEach { child ->
                find(child, type)?.let { return it }
            }
        }
        return null
    }

    private fun layoutTree(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutTree)
        }
    }

    private fun absoluteX(component: Component, root: Component): Int {
        var x = 0
        var current: Component? = component
        while (current != null && current !== root) {
            x += current.x
            current = current.parent
        }
        return x
    }
}

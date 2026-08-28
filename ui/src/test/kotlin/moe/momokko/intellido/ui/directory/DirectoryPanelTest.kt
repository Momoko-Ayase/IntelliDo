package moe.momokko.intellido.ui.directory

import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class DirectoryPanelTest {
    @Test
    fun `categories list uses names and counts instead of a raw dump`() {
        val panel = panel(DirectoryKind.CATEGORIES)
        val rows = panel.displayedRows()
        assertTrue(rows.any { it.contains("公告") && it.contains("2") }, rows.toString())
        assertTrue(rows.any { it.contains("使用指南") }, rows.toString())
    }

    @Test
    fun `members stay clickable as usernames`() {
        val opened = mutableListOf<String>()
        val panel = panel(DirectoryKind.MEMBERS, onOpenUser = { opened += it })
        assertTrue(panel.displayedRows().any { it.contains("system") }, panel.displayedRows().toString())
        panel.activateAt(0)
        assertTrue(opened.contains("system"), opened.toString())
    }

    @Test
    fun `about renders community title`() {
        val panel = panel(DirectoryKind.ABOUT)
        val texts = labels(panel)
        assertTrue(texts.any { it.contains("LINUX DO") }, texts.toString())
        assertTrue(texts.any { it.contains("话题") }, texts.toString())
        assertTrue(texts.any { it.contains("常见问题") }, texts.toString())
    }

    private fun panel(
        kind: DirectoryKind,
        onOpenUser: (String) -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
    ): DirectoryPanel =
        DirectoryPanel(
            kind,
            FakeLinuxDoCommunityClient(),
            Locale.SIMPLIFIED_CHINESE,
            onOpenTopic = { _, _ -> },
            onOpenUser = onOpenUser,
            onOpenUrl = onOpenUrl,
            dispatch = { _, work -> work() },
        )

    private fun labels(root: java.awt.Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: java.awt.Component) {
            if (component is javax.swing.JLabel && component.text.isNotBlank()) {
                out += component.text.replace(Regex("<[^>]+>"), "")
            }
            if (component is java.awt.Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }
}

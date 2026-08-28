package moe.momokko.intellido.ui.home

import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.Locale
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants

class HomeGeometryProbeTest {
    @Test
    fun `stats column is laid out inside the row, not left at 0x0`() {
        val topic = FakeLinuxDoCommunityClient.defaultTopics.first()
        val model = DefaultListModel<HomeTopic>()
        model.addElement(topic)
        val list = JList(model)
        list.size = Dimension(900, 400)
        val row = HomeTopicRenderer(Locale.SIMPLIFIED_CHINESE)
            .getListCellRendererComponent(list, topic, 0, false, false) as Container
        row.setSize(row.preferredSize)
        HomeTopicRenderer.layoutTree(row)

        val views = labels(row).first { it.text.contains("29.7k") }
        val x = absoluteX(views, row)
        assertTrue(views.width > 0, "views column was not laid out: bounds=${views.bounds}")
        assertTrue(
            x + views.width <= row.width + 1,
            "views column overflowed the row: x=$x width=${views.width} row=${row.width}",
        )
        assertTrue(x > 400, "views column should sit on the right, was x=$x")
    }

    @Test
    fun `shrinking the viewport keeps stats inside the visible width`() {
        val topic = FakeLinuxDoCommunityClient.defaultTopics.first()
        val model = DefaultListModel<HomeTopic>()
        model.addElement(topic)
        val list = object : JList<HomeTopic>(model) {
            override fun getScrollableTracksViewportWidth(): Boolean = true
        }
        list.cellRenderer = HomeTopicRenderer(Locale.SIMPLIFIED_CHINESE)
        val scroll = JScrollPane(
            list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        )
        scroll.setSize(480, 400)
        scroll.doLayout()
        (scroll.viewport as JViewport).doLayout()
        list.doLayout()

        val row = list.cellRenderer.getListCellRendererComponent(list, topic, 0, false, false) as Container
        row.setSize(HomeTopicRenderer.rowWidth(list), row.preferredSize.height)
        HomeTopicRenderer.layoutTree(row)
        val views = labels(row).first { it.text.contains("29.7k") }
        val x = absoluteX(views, row)
        assertTrue(views.width > 0, "views=${views.bounds}")
        assertTrue(
            x + views.width <= row.width + 1,
            "views x=$x width=${views.width} row=${row.width} list=${list.width}",
        )
    }

    @Test
    fun `column header stretches and keeps reply label on the right`() {
        val header = HomeTopicRenderer.header(Locale.SIMPLIFIED_CHINESE)
        header.setSize(900, header.preferredSize.height.coerceAtLeast(24))
        HomeTopicRenderer.layoutTree(header)
        val replies = labels(header).first { it.text == "回复" }
        assertTrue(replies.width > 0, "reply bounds=${replies.bounds}")
        val x = absoluteX(replies, header)
        assertTrue(
            x > 400,
            "reply should sit on the right, was x=$x bounds=${replies.bounds} header=${header.size}",
        )
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

    private fun labels(root: Component): List<JLabel> {
        val out = mutableListOf<JLabel>()
        fun walk(component: Component) {
            if (component is JLabel && component.text.isNotBlank()) {
                out += component
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }
}

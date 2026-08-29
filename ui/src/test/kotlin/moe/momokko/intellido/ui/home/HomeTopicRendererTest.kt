package moe.momokko.intellido.ui.home

import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import moe.momokko.intellido.ui.guest.FaMark
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.Locale
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList

class HomeTopicRendererTest {
    @Test
    fun `pinned topic row uses Discourse compact views`() {
        val topic = FakeLinuxDoCommunityClient.defaultTopics.first()
        val texts = render(topic)
        assertTrue(texts.any { it.contains("欢迎使用 IntelliDo") }, texts.toString())
        assertTrue(texts.any { it.contains("29.7k") }, texts.toString())
        assertTrue(texts.any { it.contains("公告") }, texts.toString())
        assertTrue(texts.any { it.contains("intellido") }, texts.toString())
        assertTrue(texts.contains("comments"), texts.toString())
        assertTrue(texts.contains("bullhorn"), texts.toString())
        assertTrue(texts.contains("thumbtack"), texts.toString())
        assertTrue(texts.none { it.contains("📌") }, texts.toString())
    }

    @Test
    fun `restricted subcategory chip keeps the lock and LINUX DO name`() {
        val topic = FakeLinuxDoCommunityClient.defaultTopics.first().copy(
            categoryName = "搞七捻三, Lv1",
            categoryIcon = "droplet",
            categoryRestricted = true,
            tags = listOf("人工智能"),
        )
        val texts = render(topic)
        assertTrue(texts.any { it.contains("搞七捻三, Lv1") }, texts.toString())
        assertTrue(texts.contains("droplet"), texts.toString())
        assertTrue(texts.contains("lock"), texts.toString())
        assertTrue(texts.any { it.contains("人工智能") }, texts.toString())
    }

    @Test
    fun `closed and accepted topics show Discourse status marks`() {
        val topic = FakeLinuxDoCommunityClient.defaultTopics.first().copy(closed = true, acceptedAnswer = true)
        val texts = render(topic)
        assertTrue(texts.contains("lock"), texts.toString())
        assertTrue(texts.contains("check"), texts.toString())
    }

    @Test
    fun `long title stays inside the list width`() {
        val base = FakeLinuxDoCommunityClient.defaultTopics.first()
        val topic = base.copy(title = "很长的标题".repeat(40))
        val model = DefaultListModel<HomeTopic>()
        model.addElement(topic)
        val list = JList(model)
        list.size = Dimension(480, 400)
        val row = HomeTopicRenderer(Locale.SIMPLIFIED_CHINESE)
            .getListCellRendererComponent(list, topic, 0, false, false)
        assertTrue(row.preferredSize.width <= 480, "width=${row.preferredSize.width}")
    }

    @Test
    fun `column header uses LINUX DO list labels`() {
        val header = HomeTopicRenderer.header(Locale.SIMPLIFIED_CHINESE)
        val texts = labels(header)
        assertTrue(texts.contains("话题"), texts.toString())
        assertTrue(texts.contains("回复"), texts.toString())
        assertTrue(texts.contains("浏览"), texts.toString())
        assertTrue(texts.contains("活动"), texts.toString())
    }

    private fun render(topic: HomeTopic): List<String> {
        val model = DefaultListModel<HomeTopic>()
        model.addElement(topic)
        val list = JList(model)
        list.size = Dimension(900, 400)
        val row = HomeTopicRenderer(Locale.SIMPLIFIED_CHINESE)
            .getListCellRendererComponent(list, topic, 0, false, false)
        return labels(row)
    }

    private fun labels(root: Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: Component) {
            if (component is JLabel && component.text.isNotBlank()) {
                out += component.text
            }
            if (component is FaMark) {
                out += component.iconName
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }
}

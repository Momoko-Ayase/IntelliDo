package moe.momokko.intellido.ui.topic

import moe.momokko.intellido.domain.live.LivePresenceUser
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import moe.momokko.intellido.ui.guest.FaMark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.Locale
import javax.swing.JLabel

class TopicPanelTest {
    @Test
    fun `topic chrome shows Discourse header map and post meta`() {
        val thread = FakeLinuxDoCommunityClient().loadTopic(101)
        val texts = labels(TopicPanel(thread, Locale.SIMPLIFIED_CHINESE))
        assertTrue(texts.any { it.contains("欢迎使用 IntelliDo") }, texts.toString())
        assertTrue(texts.contains("系统管理员"), texts.toString())
        assertTrue(texts.contains("系统"), texts.toString())
        assertTrue(texts.contains("29.7k"), texts.toString())
        assertTrue(texts.contains("浏览量"), texts.toString())
        assertTrue(texts.contains("赞"), texts.toString())
        assertTrue(texts.contains("链接"), texts.toString())
        assertTrue(texts.contains("用户"), texts.toString())
        assertTrue(texts.contains("42"), texts.toString())
        assertTrue(texts.contains("3 个回复"), texts.toString())
        assertTrue(texts.contains("前排"), texts.toString())
        assertTrue(texts.contains("前排合影"), texts.toString())
        assertTrue(texts.contains("回复 system") || texts.any { it.contains("回复") && it.contains("system") }, texts.toString())
        assertTrue(texts.contains("thumbtack"), texts.toString())
        assertTrue(texts.contains("heart") || texts.contains("+1"), texts.toString())
        assertTrue(texts.contains("pencil"), texts.toString())
        assertTrue(texts.contains("reply"), texts.toString())
        assertTrue(texts.contains("阅读时间"), texts.toString())
        assertTrue(texts.contains("shield-halved"), texts.toString())
        assertTrue(texts.contains("公告"), texts.toString())
        assertTrue(texts.contains("intellido"), texts.toString())
        assertTrue(texts.any { it.contains("1") && it.contains("/") }, texts.toString())
        assertTrue(texts.contains("1 / 4") || texts.any { it.replace(" ", "") == "1/4" }, texts.toString())
    }

    @Test
    fun `appended posts appear after the first page`() {
        val client = FakeLinuxDoCommunityClient()
        val first = client.loadTopic(101)
        val panel = TopicPanel(first, Locale.SIMPLIFIED_CHINESE)
        val before = labels(panel)
        assertTrue(before.none { it.contains("第三篇") }, before.toString())
        panel.appendPosts(client.loadNextPosts(first))
        val after = labels(panel)
        assertTrue(after.contains("读者"), after.toString())
        assertTrue(after.contains("#3"), after.toString())
        panel.appendPosts(client.loadRemainingPosts(first))
        val actions = labels(panel)
        assertTrue(actions.any { it.contains("全站置顶") }, actions.toString())
        assertTrue(actions.contains("thumbtack"), actions.toString())
        assertTrue(actions.none { it.contains("#4") }, actions.toString())
    }

    @Test
    fun `unloaded replies show skeleton placeholders`() {
        val client = FakeLinuxDoCommunityClient()
        val first = client.loadTopic(101)
        val panel = TopicPanel(first, Locale.SIMPLIFIED_CHINESE)
        assertTrue(skeletons(panel).isNotEmpty(), "expected reply skeletons while stream still has unloaded posts")
        panel.appendPosts(client.loadRemainingPosts(first))
        assertTrue(skeletons(panel).isEmpty(), "skeletons should clear after the stream is fully loaded")
    }

    @Test
    fun `jumping to an unloaded floor asks for that stream index`() {
        val requested = mutableListOf<Int>()
        val client = FakeLinuxDoCommunityClient()
        val first = client.loadTopic(102)
        val panel = TopicPanel(first, Locale.SIMPLIFIED_CHINESE, onNeedAround = { requested += it })
        val last = first.streamIds.lastIndex
        assertTrue(last > 0, "topic 102 fixture must have unloaded replies")
        panel.jumpToStream(last)
        assertEquals(listOf(last), requested)
        assertTrue(skeletons(panel).isNotEmpty(), "jumping into unloaded replies should show placeholders")
    }

    @Test
    fun `an empty fetch past the newest reply clears placeholders and stops asking`() {
        val requested = mutableListOf<Int>()
        val client = FakeLinuxDoCommunityClient()
        val first = client.loadTopic(102)
        val last = first.streamIds.lastIndex
        val panel = TopicPanel(first, Locale.SIMPLIFIED_CHINESE, onNeedAround = { requested += it })
        panel.jumpToStream(last)
        assertTrue(skeletons(panel).isNotEmpty(), requested.toString())
        requested.clear()
        panel.reveal(first, last)
        assertTrue(skeletons(panel).isEmpty(), "empty fetch must drop reply skeletons")
        panel.jumpToStream(last)
        assertTrue(requested.isEmpty(), "must not request posts after the newest reply: $requested")
    }

    @Test
    fun `clicking reply count lists excerpts`() {
        val client = FakeLinuxDoCommunityClient()
        val first = client.loadTopic(101)
        val panel = TopicPanel(
            first,
            Locale.SIMPLIFIED_CHINESE,
            onNeedReplies = { id, done -> done(client.loadPostReplies(id)) },
        )
        panel.size = Dimension(900, 800)
        panel.doLayout()
        val toggle = findLabel(panel) { it.contains("个回复") }
        assertTrue(toggle != null, labels(panel).toString())
        toggle!!.dispatchEvent(
            java.awt.event.MouseEvent(
                toggle,
                java.awt.event.MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(),
                0,
                2,
                2,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1,
            ),
        )
        val texts = labels(panel)
        assertTrue(texts.any { it.contains("助手") || it.contains("读者") || it.contains("楼中楼") || it.contains("第三篇") }, texts.toString())
    }

    @Test
    fun `revised post replaces cooked text in place`() {
        val thread = FakeLinuxDoCommunityClient().loadTopic(101)
        val panel = TopicPanel(thread, Locale.SIMPLIFIED_CHINESE)
        val edited = thread.posts.first().copy(
            cookedHtml = "<p>修订正文</p>",
            plainText = "修订正文",
            version = 3,
        )
        panel.replacePost(edited)
        val posts = postsOf(panel)
        assertTrue(posts.any { it.id == edited.id && it.plainText == "修订正文" }, posts.map { it.plainText }.toString())
    }

    @Test
    fun `reply presence appears below the posts`() {
        val panel = TopicPanel(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE)
        panel.showPresence(listOf(LivePresenceUser(2, "helper")))
        val texts = labels(panel)
        assertTrue(texts.any { it.contains("正在回复") }, texts.toString())
    }

    @Test
    fun `copy link shares the canonical topic url`() {
        val copied = mutableListOf<String>()
        val panel = TopicPanel(
            FakeLinuxDoCommunityClient().loadTopic(101),
            Locale.SIMPLIFIED_CHINESE,
            onCopyLink = { copied += it },
        )
        val texts = labels(panel)
        assertTrue(texts.contains("复制链接"), texts.toString())
        val copy = findLabel(panel) { it == "复制链接" }
        copy!!.dispatchEvent(
            java.awt.event.MouseEvent(
                copy,
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                2,
                2,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1,
            ),
        )
        assertTrue(copied.any { it.contains("https://linux.do/t/") && it.contains("101") }, copied.toString())
    }

    @Test
    fun `find locates loaded posts without moving other chrome`() {
        val panel = TopicPanel(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE)
        panel.find("IntelliDo")
        assertEquals(listOf(1), panel.findHits().map { it.postNumber })
    }

    @Test
    fun `new posts stay behind an indicator instead of stealing the viewport`() {
        val panel = TopicPanel(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE)
        panel.noteIncomingPosts(2)
        assertEquals(2, panel.incomingCount())
        assertTrue(labels(panel).any { it.contains("新帖子") }, labels(panel).toString())
        panel.consumeIncoming()
        assertEquals(0, panel.incomingCount())
    }

    @Test
    fun `timeline occupies the east of the topic panel instead of leaving a gap`() {
        val panel = TopicPanel(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE)
        panel.size = Dimension(1200, 700)
        panel.doLayout()
        val timeline = panel.components.filterIsInstance<TopicTimeline>().single()
        assertTrue(timeline.width >= TopicTimeline.WIDTH, "timeline width=${timeline.width}")
        assertTrue(timeline.x > 700, "timeline should sit on the right, x=${timeline.x}")
        assertTrue(timeline.x + timeline.width <= 1200, "timeline overflows panel: x=${timeline.x} w=${timeline.width}")
        assertTrue(timeline.x + timeline.width >= 1160, "timeline should reach the east edge: x=${timeline.x} w=${timeline.width}")
        assertTrue(timeline.height > 600, "timeline height=${timeline.height}")
    }

    private fun findLabel(root: Component, match: (String) -> Boolean): JLabel? {
        if (root is JLabel && root.text.isNotBlank() && match(root.text)) {
            return root
        }
        if (root is Container) {
            root.components.forEach { child ->
                findLabel(child, match)?.let { return it }
            }
        }
        return null
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
            if (component is moe.momokko.intellido.ui.guest.EmojiMark) {
                out += component.shortcode
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private fun skeletons(root: Component): List<Component> {
        val out = mutableListOf<Component>()
        fun walk(component: Component) {
            if (component is PostSkeleton) {
                out += component
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private fun postsOf(root: Component): List<moe.momokko.intellido.domain.topic.TopicPost> {
        val out = mutableListOf<moe.momokko.intellido.domain.topic.TopicPost>()
        fun walk(component: Component) {
            if (component is javax.swing.JComponent) {
                val post = component.getClientProperty(TopicPanel.POST_KEY) as? moe.momokko.intellido.domain.topic.TopicPost
                if (post != null) {
                    out += post
                }
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }
}

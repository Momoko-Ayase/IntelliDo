package moe.momokko.intellido.ui.home

import moe.momokko.intellido.platform.home.HomeTopicsController
import moe.momokko.intellido.platform.topic.TopicPreviewSession
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeTopicsVerticalSliceTest {
    @Test
    fun `native Home path reads the local fake LINUX DO topic list`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        val titles = controller.load().map { it.title }
        assertEquals("欢迎使用 IntelliDo", titles[0])
        assertEquals("如何阅读话题", titles[1])
        assertEquals("非官方客户端说明", titles[2])
    }

    @Test
    fun `opening a Home topic reads fake posts and reuses the preview tab`() {
        val client = FakeLinuxDoCommunityClient()
        val session = TopicPreviewSession()
        val topics = client.loadHomeTopics()
        val thread = client.loadTopic(topics[0].id)
        session.activate(topics[0].id)
        val snapshot = session.activate(topics[1].id)

        assertTrue(thread.posts.isNotEmpty())
        assertTrue(thread.posts[0].plainText.contains("IntelliDo"))
        assertEquals(listOf(topics[1].id), snapshot.tabs.map { it.topicId })
        assertEquals(topics[1].id, snapshot.previewTopicId)
    }

    @Test
    fun `anonymous fake catalogs are readable without login`() {
        val client = FakeLinuxDoCommunityClient()
        assertEquals(2, client.loadCategories().size)
        assertTrue(client.loadTags().any { it.name == "intellido" })
        assertTrue(client.loadTags().any { it.name == "人工智能" })
        assertTrue(client.loadTags().any { it.name == "公告" })
        assertTrue(client.loadTopic(101).posts.first().cookedHtml.contains("<strong>"))
    }
}

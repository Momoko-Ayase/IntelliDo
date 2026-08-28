package moe.momokko.intellido.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BridgedLinuxDoCommunityClientTest {
    @Test
    fun `bridged client reads fixture JSON without contacting linux do`() {
        val client = BridgedLinuxDoCommunityClient { path ->
            when {
                path.startsWith("/latest.json?page=1") -> resource("discourse/latest-page-1.json")
                path.startsWith("/latest.json") -> resource("discourse/latest.json")
                path.startsWith("/hot.json") -> resource("discourse/latest.json")
                path.startsWith("/top.json") -> resource("discourse/latest.json")
                path.startsWith("/categories.json") -> resource("discourse/categories.json")
                path.startsWith("/t/101.json") -> resource("discourse/topic.json")
                path.startsWith("/t/101/posts.json") -> resource("discourse/topic-posts.json")
                path.startsWith("/u/helper/summary.json") -> resource("discourse/user-summary.json")
                path.startsWith("/u/helper.json") -> resource("discourse/user.json")
                path.startsWith("/site.json") -> resource("discourse/site-user-fields.json")
                else -> error("unexpected path $path")
            }
        }
        assertEquals(listOf("欢迎使用 IntelliDo", "如何阅读话题"), client.loadHomeTopics().map { it.title })
        assertEquals(29_700, client.loadHomeTopics().first().views)
        assertEquals(listOf("欢迎使用 IntelliDo", "如何阅读话题"), client.loadHotTopics().map { it.title })
        assertEquals(listOf("欢迎使用 IntelliDo", "如何阅读话题"), client.loadTopTopics().map { it.title })
        val first = client.loadTopic(101)
        assertEquals(listOf(1001L, 1002L), first.posts.map { it.id })
        assertEquals("公告", first.topic.categoryName)
        assertEquals("F6C344", first.topic.categoryColor)
        val thread = client.loadRemainingPosts(first)
        assertEquals(listOf(1001L, 1002L, 1003L), thread.posts.map { it.id })
        assertEquals("reader", thread.posts.last().username)
        assertEquals("公告", client.loadCategories().first().name)
        assertEquals(listOf("第二页的话题"), client.loadHomeTopics(1).map { it.title })
        val next = client.loadNextPosts(first)
        assertEquals(listOf(1001L, 1002L, 1003L), next.posts.map { it.id })
        assertEquals(listOf(1003L), client.loadTopicPosts(101, listOf(1003L)).map { it.id })
        val around = client.loadPostsAround(first, 2, 2)
        assertEquals(listOf(1001L, 1002L, 1003L), around.posts.map { it.id })
        val profile = client.loadPublicProfile("helper")
        assertEquals("测试城", profile.location)
        assertEquals("IntelliDo 测试", profile.publicFields.single().value)
        assertEquals(9, profile.summary?.daysVisited)
        assertEquals("欢迎使用 IntelliDo", profile.summary?.topics?.single()?.title)
    }

    @Test
    fun `concurrent category loads hit LINUX DO once`() {
        val hits = AtomicInteger(0)
        val client = BridgedLinuxDoCommunityClient { path ->
            when {
                path.startsWith("/categories.json") -> {
                    hits.incrementAndGet()
                    Thread.sleep(40)
                    resource("discourse/categories.json")
                }
                else -> error("unexpected path $path")
            }
        }
        val pool = Executors.newFixedThreadPool(4)
        try {
            (1..4).map { pool.submit<Int> { client.loadCategories().size } }.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, hits.get())
    }

    @Test
    fun `categories json is enough without waiting for site json`() {
        val paths = mutableListOf<String>()
        val client = BridgedLinuxDoCommunityClient { path ->
            paths += path
            when {
                path.startsWith("/categories.json") -> resource("discourse/categories.json")
                path.startsWith("/site.json") -> error("site.json should not block Home")
                else -> error("unexpected path $path")
            }
        }
        assertEquals("公告", client.loadCategories().first().name)
        assertTrue(paths.none { it.contains("site.json") })
    }

    @Test
    fun `home topics still load when categories are forbidden`() {
        val client = BridgedLinuxDoCommunityClient { path ->
            when {
                path.startsWith("/latest.json") -> resource("discourse/latest.json")
                path.startsWith("/categories.json") -> error("LINUX DO returned HTTP 403")
                else -> error("unexpected path $path")
            }
        }
        val topics = client.loadHomeTopics()
        assertEquals(2, topics.size)
        assertEquals("欢迎使用 IntelliDo", topics.first().title)
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }
}

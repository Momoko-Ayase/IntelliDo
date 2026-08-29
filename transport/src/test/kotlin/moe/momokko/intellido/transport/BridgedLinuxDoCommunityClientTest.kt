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
    fun `categories json still works when site json is missing`() {
        val paths = mutableListOf<String>()
        val client = BridgedLinuxDoCommunityClient { path ->
            paths += path
            when {
                path.startsWith("/categories.json") -> resource("discourse/categories.json")
                path.startsWith("/site.json") -> error("site.json unavailable")
                else -> error("unexpected path $path")
            }
        }
        assertEquals("公告", client.loadCategories().first().name)
        assertTrue(paths.any { it.contains("categories.json") })
    }

    @Test
    fun `site json restricted subcategories name Home chips`() {
        val client = BridgedLinuxDoCommunityClient { path ->
            when {
                path.startsWith("/site.json") -> """
                    {
                      "default_archetype": "regular",
                      "categories": [
                        {
                          "id": 42,
                          "name": "搞七捻三",
                          "slug": "gossip",
                          "topic_count": 8,
                          "read_restricted": false,
                          "color": "43A047",
                          "icon": "droplet"
                        },
                        {
                          "id": 421,
                          "name": "搞七捻三, Lv1",
                          "slug": "gossip-lv1",
                          "topic_count": 3,
                          "read_restricted": true,
                          "parent_category_id": 42
                        }
                      ]
                    }
                """.trimIndent()
                path.startsWith("/categories.json") -> """{"category_list":{"categories":[]}}"""
                path.startsWith("/latest.json") -> """
                    {
                      "users": [{ "id": 22, "username": "helper" }],
                      "topic_list": {
                        "topics": [
                          {
                            "id": 501,
                            "title": "手头的 token 有点多",
                            "slug": "tokens",
                            "posts_count": 1,
                            "reply_count": 0,
                            "last_posted_at": "2026-08-29T00:00:00.000Z",
                            "category_id": 421,
                            "tags": ["人工智能"],
                            "posters": [{ "user_id": 22 }]
                          }
                        ]
                      }
                    }
                """.trimIndent()
                else -> error("unexpected path $path")
            }
        }
        val topic = client.loadHomeTopics().single()
        assertEquals("搞七捻三, Lv1", topic.categoryName)
        assertEquals(true, topic.categoryRestricted)
        assertEquals("droplet", topic.categoryIcon)
        assertEquals("43A047", topic.categoryColor)
        assertEquals(listOf("人工智能"), topic.tags)
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

    @Test
    fun `bridged client maps session current without contacting linux do`() {
        val client = BridgedLinuxDoCommunityClient { path ->
            when {
                path.startsWith("/session/current.json") -> resource("discourse/session-current.json")
                path.startsWith("/categories.json") -> resource("discourse/categories.json")
                path.startsWith("/topics/created-by/helper.json") -> resource("discourse/latest.json")
                else -> error("unexpected path $path")
            }
        }
        val session = client.loadCurrentSession() as moe.momokko.intellido.domain.session.MemberSession.SignedIn
        assertEquals("helper", session.username)
        assertEquals("欢迎使用 IntelliDo", client.loadCreatedTopics("helper").first().title)
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }
}

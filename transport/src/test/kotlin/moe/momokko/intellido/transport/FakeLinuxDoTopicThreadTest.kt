package moe.momokko.intellido.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeLinuxDoTopicThreadTest {
    @Test
    fun `fake topic thread returns native posts without contacting linux do`() {
        val client = FakeLinuxDoCommunityClient()
        val thread = client.loadTopic(101)

        assertEquals(101L, thread.topic.id)
        assertEquals("欢迎使用 IntelliDo", thread.topic.title)
        assertEquals(2, thread.posts.size)
        val more = client.loadNextPosts(thread, 1)
        assertEquals(3, more.posts.size)
        assertEquals("reader", more.posts.last().username)
        val all = client.loadRemainingPosts(thread)
        assertEquals("pinned_globally", all.posts.last().actionCode)
        val around = client.loadPostsAround(client.loadTopic(102), 7, 2)
        assertEquals(listOf(1, 7, 8), around.posts.map { it.postNumber })
        assertEquals(1, thread.posts[0].postNumber)
        assertEquals("system", thread.posts[0].username)
        assertTrue(thread.posts[0].plainText.contains("IntelliDo"))
        assertFalse(thread.posts.any { it.plainText.contains("linux.do") })
    }
}

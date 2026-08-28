package moe.momokko.intellido.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeLinuxDoCommunityClientTest {
    @Test
    fun `fake home list returns seeded public topics`() {
        val topics = FakeLinuxDoCommunityClient().loadHomeTopics()

        assertEquals(3, topics.size)
        assertEquals(101L, topics[0].id)
        assertEquals("欢迎使用 IntelliDo", topics[0].title)
        assertEquals("如何阅读话题", topics[1].title)
        assertEquals("非官方客户端说明", topics[2].title)
    }

    @Test
    fun `fake community client does not target the production LINUX DO host`() {
        assertEquals("intellido.test", FakeLinuxDoCommunityClient.LOCAL_ORIGIN)
        assertFalse(FakeLinuxDoCommunityClient.LOCAL_ORIGIN.contains("linux.do"))
        FakeLinuxDoCommunityClient.defaultTopics.forEach { topic ->
            assertFalse(topic.slug.contains("linux.do"))
            assertTrue(topic.title.isNotBlank())
        }
    }
}

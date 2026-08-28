package moe.momokko.intellido.domain.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class HomeTopicTest {
    @Test
    fun `topic keeps Discourse identity fields`() {
        val topic = HomeTopic(
            id = 42,
            title = "欢迎使用 IntelliDo",
            slug = "welcome-to-intellido",
            postsCount = 3,
            replyCount = 2,
            categoryName = "公告",
            authorUsername = "system",
            lastPostedAt = Instant.parse("2026-08-22T00:00:00Z"),
            views = 29_700,
            pinned = true,
            posters = listOf(TopicPoster("system", "/user_avatar/linux.do/system/{size}/1_2.png")),
        )

        assertEquals(42, topic.id)
        assertEquals("欢迎使用 IntelliDo", topic.title)
        assertEquals("welcome-to-intellido", topic.slug)
        assertEquals("公告", topic.categoryName)
        assertEquals("system", topic.authorUsername)
        assertEquals(29_700, topic.views)
        assertEquals(true, topic.pinned)
        assertEquals("system", topic.posters.single().username)
        assertEquals(
            "https://linux.do/user_avatar/linux.do/system/48/1_2.png",
            topic.posters.single().avatarUrl(),
        )
    }

    @Test
    fun `blank title is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeTopic(
                id = 1,
                title = "  ",
                slug = "blank",
                postsCount = 1,
                replyCount = 0,
                categoryName = null,
                authorUsername = "system",
                lastPostedAt = Instant.parse("2026-08-22T00:00:00Z"),
            )
        }
    }
}

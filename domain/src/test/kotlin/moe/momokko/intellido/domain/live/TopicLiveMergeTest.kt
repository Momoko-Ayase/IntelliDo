package moe.momokko.intellido.domain.live

import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicThread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class TopicLiveMergeTest {
    @Test
    fun `created posts append onto the stream in post number order`() {
        val first = post(1001, 1, "op")
        val later = post(1003, 3, "late")
        val thread = TopicThread(topic(), listOf(first, later), listOf(1001, 1003))
        val created = post(1002, 2, "reply")
        val merged = TopicLiveMerge.insert(thread, listOf(created))
        assertEquals(listOf(1001L, 1002L, 1003L), merged.streamIds)
        assertEquals(listOf("op", "reply", "late"), merged.posts.map { it.username })
        assertEquals(3, merged.topic.postsCount)
    }

    @Test
    fun `revised posts replace cooked content in place`() {
        val original = post(1001, 1, "op", cooked = "<p>old</p>")
        val thread = TopicThread(topic(), listOf(original), listOf(1001))
        val edited = original.copy(cookedHtml = "<p>new</p>", plainText = "new", version = 2)
        val merged = TopicLiveMerge.replace(thread, listOf(edited))
        assertEquals(listOf(1001L), merged.streamIds)
        assertEquals("<p>new</p>", merged.posts.single().cookedHtml)
        assertEquals(true, merged.posts.single().edited)
    }

    @Test
    fun `deleted posts stay in the stream but are marked deleted`() {
        val first = post(1001, 1, "op")
        val reply = post(1002, 2, "reply")
        val thread = TopicThread(topic(), listOf(first, reply), listOf(1001, 1002))
        val hidden = TopicLiveMerge.hide(thread, 1002)
        assertEquals(listOf(1001L, 1002L), hidden.streamIds)
        assertEquals(true, hidden.posts.single { it.id == 1002L }.userDeleted)
        val gone = TopicLiveMerge.remove(thread, 1002)
        assertEquals(listOf(1001L), gone.streamIds)
        assertEquals(listOf("op"), gone.posts.map { it.username })
    }

    private fun topic(): HomeTopic = HomeTopic(
        id = 101,
        title = "现场",
        slug = "live",
        postsCount = 2,
        replyCount = 1,
        categoryName = "公告",
        authorUsername = "op",
        lastPostedAt = Instant.EPOCH,
    )

    private fun post(id: Long, number: Int, username: String, cooked: String = "<p>$username</p>"): TopicPost =
        TopicPost(
            id = id,
            postNumber = number,
            username = username,
            cookedHtml = cooked,
            plainText = username,
            createdAt = Instant.EPOCH,
        )
}

package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.live.GuestLiveEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscourseMessageBusTest {
    @Test
    fun `poll url stays on the LINUX DO message bus path`() {
        assertEquals(
            "https://ping.ldstatic.com/message-bus/abc123/poll",
            LinuxDoUrls.messageBusPoll("abc123"),
        )
    }

    @Test
    fun `guest poll form carries channel positions and sequence`() {
        val form = DiscourseMessageBus.pollForm(
            mapOf(
                DiscourseMessageBus.LATEST to -1L,
                DiscourseMessageBus.topic(101) to 7L,
            ),
            seq = 3,
        )
        assertEquals("-1", form[DiscourseMessageBus.LATEST])
        assertEquals("7", form["/topic/101"])
        assertEquals("3", form["__seq"])
    }

    @Test
    fun `poll json becomes guest latest topic post and presence events`() {
        val frames = DiscourseMessageBus.parse(resource("discourse/message-bus-poll.json"))
        assertEquals(5, frames.size)
        val events = DiscourseMessageBus.events(frames)
        assertEquals(
            GuestLiveEvent.LatestTopic(202, categoryId = 5),
            events.filterIsInstance<GuestLiveEvent.LatestTopic>().single(),
        )
        val posts = events.filterIsInstance<GuestLiveEvent.TopicPostChanged>()
        assertEquals(listOf("created", "revised"), posts.map { it.type })
        assertEquals(1003L, posts.first().postId)
        assertEquals("helper", posts.first().username)
        val presence = events.filterIsInstance<GuestLiveEvent.TopicPresence>().single()
        assertEquals(101L, presence.topicId)
        assertEquals("helper", presence.entering.single().username)
        assertEquals("/user_avatar/linux.do/helper/{size}/2.png", presence.entering.single().avatarTemplate)
    }

    @Test
    fun `status frames advance channel positions without becoming events`() {
        val frames = DiscourseMessageBus.parse(resource("discourse/message-bus-poll.json"))
        val events = DiscourseMessageBus.events(frames)
        assertTrue(events.none { it is GuestLiveEvent.LatestTopic && it.topicId == -1L })
        val positions = DiscourseMessageBus.positions(frames, emptyMap())
        assertEquals(4L, positions[DiscourseMessageBus.LATEST])
        assertEquals(9L, positions[DiscourseMessageBus.topic(101)])
        assertEquals(2L, positions[DiscourseMessageBus.replyPresence(101)])
    }

    @Test
    fun `login-only channels are ignored`() {
        val json = """
            [
              {"global_id":1,"message_id":1,"channel":"/notification/9","data":{"unread_notifications":1}},
              {"global_id":2,"message_id":1,"channel":"/user-drafts/9","data":{}},
              {"global_id":3,"message_id":1,"channel":"/new","data":{"message_type":"new_topic","topic_id":1}},
              {"global_id":4,"message_id":1,"channel":"/chat/9","data":{}}
            ]
        """.trimIndent()
        assertEquals(emptyList<GuestLiveEvent>(), DiscourseMessageBus.events(DiscourseMessageBus.parse(json)))
    }

    @Test
    fun `chunked long-poll payloads are split into frames`() {
        val chunked =
            """[] | [{"global_id":1,"message_id":4,"channel":"/latest","data":{"message_type":"latest","topic_id":11}}] | [{"global_id":2,"message_id":5,"channel":"/latest","data":{"message_type":"latest","topic_id":12}}] | [] |"""
        val events = DiscourseMessageBus.events(DiscourseMessageBus.parse(chunked))
        assertEquals(listOf(11L, 12L), events.filterIsInstance<GuestLiveEvent.LatestTopic>().map { it.topicId })
    }

    @Test
    fun `empty long-poll is a valid idle payload`() {
        assertEquals(emptyList<MessageBusFrame>(), DiscourseMessageBus.parse("[]"))
        assertEquals(emptyList<GuestLiveEvent>(), DiscourseMessageBus.events(emptyList()))
    }

    @Test
    fun `deleted and recovered posts are guest live events`() {
        val json = """
            [
              {"global_id":1,"message_id":9,"channel":"/topic/101","data":{"type":"deleted","id":1002,"post_number":2}},
              {"global_id":2,"message_id":10,"channel":"/topic/101","data":{"type":"recovered","id":1002,"post_number":2}},
              {"global_id":3,"message_id":11,"channel":"/topic/101","data":{"type":"destroyed","id":1003,"post_number":3}}
            ]
        """.trimIndent()
        val types = DiscourseMessageBus.events(DiscourseMessageBus.parse(json))
            .filterIsInstance<GuestLiveEvent.TopicPostChanged>()
            .map { it.type }
        assertEquals(listOf("deleted", "recovered", "destroyed"), types)
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }
}

package moe.momokko.intellido.platform.live

import moe.momokko.intellido.domain.live.GuestLiveEvent
import moe.momokko.intellido.transport.DiscourseMessageBus
import moe.momokko.intellido.transport.MessageBusPoller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GuestLiveSessionTest {
    @Test
    fun `session delivers latest and topic events from a scripted poller`() {
        val poller = QueuePoller()
        val received = CopyOnWriteArrayList<GuestLiveEvent>()
        val latch = CountDownLatch(1)
        val session = GuestLiveSession(poller, clientId = "cafef00d", idleSleepMs = 20)
        session.addListener { events ->
            received += events
            latch.countDown()
        }
        session.watchLatest()
        session.watchTopic(101, lastMessageId = 7)
        session.start()
        try {
            val first = poller.requested.poll(2, TimeUnit.SECONDS) ?: error("session never polled")
            assertEquals(-1L, first[DiscourseMessageBus.LATEST])
            assertEquals(7L, first[DiscourseMessageBus.topic(101)])
            assertTrue(first.containsKey(DiscourseMessageBus.replyPresence(101)))
            poller.replies.put(
                """
                [
                  {"global_id":1,"message_id":4,"channel":"/latest","data":{"message_type":"latest","topic_id":202}},
                  {"global_id":2,"message_id":8,"channel":"/topic/101","data":{"type":"created","id":1003,"post_number":3}},
                  {"global_id":3,"message_id":2,"channel":"/presence/discourse-presence/reply/101","data":{"entering_users":[{"id":2,"username":"helper"}]}}
                ]
                """.trimIndent(),
            )
            assertTrue(latch.await(2, TimeUnit.SECONDS), "events=$received")
            assertEquals(202L, received.filterIsInstance<GuestLiveEvent.LatestTopic>().single().topicId)
            assertTrue(received.any { it is GuestLiveEvent.TopicPostChanged && it.created })
            assertTrue(received.any { it is GuestLiveEvent.TopicPresence })
        } finally {
            session.stop()
            poller.replies.put("[]")
        }
    }

    @Test
    fun `chunks arriving mid-poll are delivered before the long poll ends`() {
        val poller = StreamingPoller()
        val received = CopyOnWriteArrayList<Long>()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val session = GuestLiveSession(poller, clientId = "cafef00d", idleSleepMs = 0)
        session.addListener { events ->
            events.filterIsInstance<GuestLiveEvent.LatestTopic>().forEach { event ->
                received += event.topicId
                if (received.size == 1) first.countDown()
                if (received.size >= 2) second.countDown()
            }
        }
        session.watchLatest()
        session.start()
        try {
            assertTrue(poller.started.await(2, TimeUnit.SECONDS))
            poller.emit("""[{"global_id":1,"message_id":4,"channel":"/latest","data":{"message_type":"latest","topic_id":11}}]""")
            assertTrue(first.await(2, TimeUnit.SECONDS), "first=$received")
            assertEquals(listOf(11L), received.toList())
            poller.emit("""[{"global_id":2,"message_id":5,"channel":"/latest","data":{"message_type":"latest","topic_id":12}}]""")
            assertTrue(second.await(2, TimeUnit.SECONDS), "second=$received")
            assertEquals(listOf(11L, 12L), received.toList())
            assertEquals(false, poller.finished.get(), "second event must arrive while the poll is still open")
        } finally {
            poller.finish()
            session.stop()
        }
    }

    @Test
    fun `rewatching an open topic keeps latest subscribed`() {
        val session = GuestLiveSession(QueuePoller(), clientId = "cafef00d", idleSleepMs = 20)
        session.watchLatest()
        session.watchTopic(101, lastMessageId = 7)
        session.watchTopic(101, lastMessageId = 7)
        val channels = session.lastChannels()
        assertEquals(-1L, channels[DiscourseMessageBus.LATEST])
        assertEquals(7L, channels[DiscourseMessageBus.topic(101)])
        assertTrue(channels.containsKey(DiscourseMessageBus.replyPresence(101)))
        session.stop()
    }

    private class QueuePoller : MessageBusPoller {
        val requested = LinkedBlockingQueue<Map<String, Long>>()
        val replies = LinkedBlockingQueue<String>()

        override fun poll(clientId: String, channels: Map<String, Long>, seq: Int): String {
            requested.put(channels)
            return replies.poll(2, TimeUnit.SECONDS) ?: "[]"
        }
    }

    private class StreamingPoller : MessageBusPoller {
        val started = CountDownLatch(1)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        private val chunks = LinkedBlockingQueue<String>()

        override fun poll(clientId: String, channels: Map<String, Long>, seq: Int): String = "[]"

        override fun poll(
            clientId: String,
            channels: Map<String, Long>,
            seq: Int,
            onChunk: (String) -> Unit,
        ): String {
            started.countDown()
            while (true) {
                val chunk = chunks.poll(2, TimeUnit.SECONDS) ?: return "[]"
                if (chunk == "DONE") {
                    finished.set(true)
                    return "[]"
                }
                onChunk(chunk)
            }
        }

        fun emit(json: String) {
            chunks.put(json)
        }

        fun finish() {
            chunks.put("DONE")
        }
    }
}

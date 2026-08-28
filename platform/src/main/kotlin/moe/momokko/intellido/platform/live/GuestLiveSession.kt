package moe.momokko.intellido.platform.live

import moe.momokko.intellido.domain.live.GuestLiveEvent
import moe.momokko.intellido.transport.DiscourseMessageBus
import moe.momokko.intellido.transport.MessageBusPoller
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-polls LINUX DO MessageBus for guest `/latest`, topic posts, and reply presence.
 *
 * [minCycleMs] floors the time between polls: an instant empty reply (a blocked or
 * rate-limiting edge) must not turn the long poll into a request loop.
 */
class GuestLiveSession(
    private val poller: MessageBusPoller,
    private val clientId: String = DiscourseMessageBus.newClientId(),
    private val idleSleepMs: Long = 0L,
    private val minCycleMs: Long = 1_000L,
) {
    private val running = AtomicBoolean(false)
    private val subscribed = LinkedHashSet<String>()
    private val positions = LinkedHashMap<String, Long>()
    private val listeners = CopyOnWriteArrayList<(List<GuestLiveEvent>) -> Unit>()
    private val lock = Any()
    private var worker: Thread? = null

    fun addListener(listener: (List<GuestLiveEvent>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<GuestLiveEvent>) -> Unit) {
        listeners.remove(listener)
    }

    fun watchLatest() {
        val added = synchronized(lock) {
            val wasNew = DiscourseMessageBus.LATEST !in subscribed
            subscribeLocked(DiscourseMessageBus.LATEST, positions[DiscourseMessageBus.LATEST] ?: -1)
            wasNew
        }
        if (added) {
            kick()
        }
    }

    fun watchTopic(topicId: Long, lastMessageId: Long = -1) {
        val added = synchronized(lock) {
            val topic = DiscourseMessageBus.topic(topicId)
            val presence = DiscourseMessageBus.replyPresence(topicId)
            val wasNew = topic !in subscribed
            subscribeLocked(topic, lastMessageId)
            subscribeLocked(presence, positions[presence] ?: -1)
            wasNew
        }
        if (added) {
            kick()
        }
    }

    fun unwatchTopic(topicId: Long) {
        synchronized(lock) {
            subscribed.remove(DiscourseMessageBus.topic(topicId))
            subscribed.remove(DiscourseMessageBus.replyPresence(topicId))
        }
        kick()
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val thread = Thread({ loop() }, "IntelliDo-message-bus")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    private fun handleChunk(chunk: String) {
        val frames = DiscourseMessageBus.parse(chunk)
        if (frames.isEmpty()) {
            return
        }
        synchronized(lock) {
            val next = DiscourseMessageBus.positions(frames, positions)
            positions.clear()
            positions.putAll(next)
        }
        val events = DiscourseMessageBus.events(frames)
        if (events.isNotEmpty()) {
            listeners.forEach { listener -> listener(events) }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    internal fun lastChannels(): Map<String, Long> = synchronized(lock) {
        subscribed.associateWith { positions[it] ?: -1L }
    }

    private fun kick() {
        if (running.get()) {
            worker?.interrupt()
        }
    }

    private fun subscribe(channel: String, lastId: Long) {
        synchronized(lock) {
            subscribeLocked(channel, lastId)
        }
    }

    private fun subscribeLocked(channel: String, lastId: Long) {
        subscribed.add(channel)
        if (lastId >= 0 || channel !in positions) {
            positions[channel] = lastId
        }
    }

    private fun loop() {
        var seq = 0
        var failures = 0
        while (running.get()) {
            try {
                val channels = lastChannels()
                if (channels.isEmpty()) {
                    Thread.sleep(200)
                    continue
                }
                seq += 1
                val startedAt = System.nanoTime()
                poller.poll(clientId, channels, seq) { chunk ->
                    handleChunk(chunk)
                }
                failures = 0
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                val floor = (minCycleMs - elapsedMs).coerceAtLeast(0)
                val settle = maxOf(floor, idleSleepMs)
                if (settle > 0) {
                    Thread.sleep(settle)
                }
            } catch (_: InterruptedException) {
                if (!running.get()) {
                    break
                }
            } catch (_: Exception) {
                failures += 1
                val delay = 1_000L shl failures.coerceAtMost(5)
                try {
                    Thread.sleep(delay.coerceAtMost(30_000L))
                } catch (_: InterruptedException) {
                    if (!running.get()) {
                        break
                    }
                }
            }
        }
    }
}

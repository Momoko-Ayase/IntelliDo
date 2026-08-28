package moe.momokko.intellido.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import moe.momokko.intellido.domain.live.GuestLiveEvent
import moe.momokko.intellido.domain.live.LivePresenceUser

data class MessageBusFrame(
    val channel: String,
    val messageId: Long,
    val globalId: Long,
    val data: JsonObject,
)

/**
 * Discourse MessageBus poll protocol used by LINUX DO for guest live events.
 */
object DiscourseMessageBus {
    const val LATEST: String = "/latest"
    const val STATUS: String = "/__status"
    const val POLL_TIMEOUT_SEC: Long = 40L

    fun topic(topicId: Long): String = "/topic/$topicId"

    fun replyPresence(topicId: Long): String = "/presence/discourse-presence/reply/$topicId"

    fun pollForm(channels: Map<String, Long>, seq: Int): Map<String, String> {
        val form = LinkedHashMap<String, String>()
        channels.forEach { (channel, position) ->
            form[channel] = position.toString()
        }
        form["__seq"] = seq.toString()
        return form
    }

    fun parse(json: String): List<MessageBusFrame> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "[]") {
            return emptyList()
        }
        return splitChunks(trimmed).flatMap { chunk -> parseArray(chunk) }
    }

    internal fun splitChunks(json: String): List<String> =
        json.split(Regex("\\r?\\n\\|\\r?\\n|\\s\\|\\s"))
            .map { chunk -> chunk.trim().trimEnd('|').trim() }
            .filter { chunk -> chunk.startsWith("[") }
    private fun parseArray(json: String): List<MessageBusFrame> {
        val parsed = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptyList()
        if (!parsed.isJsonArray) {
            return emptyList()
        }
        return parsed.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }
            val item = element.asJsonObject
            val channel = item.str("channel") ?: return@mapNotNull null
            val data = item.get("data")
            val obj = when {
                data == null || data.isJsonNull -> JsonObject()
                data.isJsonObject -> data.asJsonObject
                else -> JsonObject()
            }
            MessageBusFrame(
                channel = channel,
                messageId = item.long("message_id") ?: -1,
                globalId = item.long("global_id") ?: -1,
                data = obj,
            )
        }
    }

    fun positions(frames: List<MessageBusFrame>, previous: Map<String, Long>): Map<String, Long> {
        val next = previous.toMutableMap()
        frames.forEach { frame ->
            if (frame.channel == STATUS) {
                frame.data.entrySet().forEach { (channel, value) ->
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                        next[channel] = value.asLong
                    }
                }
            } else if (frame.messageId >= 0) {
                next[frame.channel] = frame.messageId
            }
        }
        return next
    }

    fun events(frames: List<MessageBusFrame>): List<GuestLiveEvent> =
        frames.mapNotNull { frame -> event(frame) }

    fun newClientId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")

    private fun event(frame: MessageBusFrame): GuestLiveEvent? {
        val channel = frame.channel
        return when {
            channel == LATEST -> latest(frame.data)
            channel.startsWith("/topic/") -> topicPost(channel, frame.data)
            channel.startsWith("/presence/discourse-presence/reply/") -> presence(channel, frame.data)
            else -> null
        }
    }

    private fun latest(data: JsonObject): GuestLiveEvent? {
        val type = data.str("message_type")
        if (type != null && type != "latest") {
            return null
        }
        val topicId = data.long("topic_id") ?: return null
        val categoryId = data.obj("payload")?.long("category_id") ?: data.long("category_id")
        return GuestLiveEvent.LatestTopic(topicId, categoryId)
    }

    private fun topicPost(channel: String, data: JsonObject): GuestLiveEvent? {
        val topicId = channel.removePrefix("/topic/").toLongOrNull() ?: return null
        val type = data.str("type") ?: return null
        if (type !in LIVE_POST_TYPES) {
            return null
        }
        val postId = data.long("id") ?: return null
        return GuestLiveEvent.TopicPostChanged(
            topicId = topicId,
            type = type,
            postId = postId,
            postNumber = data.int("post_number") ?: 0,
            username = data.str("username"),
        )
    }

    private fun presence(channel: String, data: JsonObject): GuestLiveEvent? {
        val topicId = channel.substringAfterLast("/").toLongOrNull() ?: return null
        val entering = data.arr("entering_users")?.mapNotNull { element ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }
            val user = element.asJsonObject
            val id = user.long("id") ?: return@mapNotNull null
            val username = user.str("username") ?: return@mapNotNull null
            LivePresenceUser(
                id = id,
                username = username,
                avatarTemplate = user.str("avatar_template"),
                displayName = user.str("name"),
            )
        }.orEmpty()
        val leaving = data.arr("leaving_user_ids")?.mapNotNull { element ->
            if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) element.asLong else null
        }.orEmpty()
        if (entering.isEmpty() && leaving.isEmpty()) {
            return null
        }
        return GuestLiveEvent.TopicPresence(topicId, entering, leaving)
    }

    private val LIVE_POST_TYPES: Set<String> = setOf("created", "revised", "rebaked", "deleted", "recovered", "destroyed")
}

private fun JsonObject.str(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else null }

private fun JsonObject.long(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asLong else null }

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asInt else null }

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arr(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

package moe.momokko.intellido.transport

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LinuxDoUrls {
    const val ORIGIN: String = "https://linux.do"

    fun absolute(path: String): String =
        if (path.startsWith("https://") || path.startsWith("http://")) {
            path
        } else {
            ORIGIN + if (path.startsWith("/")) path else "/$path"
        }

    fun latest(page: Int = 0): String = paged("/latest.json", page)

    fun hot(page: Int = 0): String = paged("/hot.json", page)

    fun top(page: Int = 0): String = paged("/top.json", page)

    fun topic(id: Long): String = "/t/$id.json"

    fun topicPosts(id: Long, postIds: List<Long>): String =
        "/t/$id/posts.json?" + postIds.joinToString("&") { "post_ids[]=$it" }

    fun categories(): String = "/categories.json?include_subcategories=true"

    fun site(): String = "/site.json"

    fun categoryLatest(id: Long, page: Int = 0): String = paged("/c/$id/l/latest.json", page)

    fun tags(): String = "/tags.json"

    fun tag(name: String, page: Int = 0): String = paged("/tag/${encode(name)}.json", page)

    fun groups(): String = "/groups.json"

    fun badges(): String = "/badges.json"

    fun directoryItems(): String = "/directory_items.json?period=all&order=likes_received"

    fun about(): String = "/about.json"

    fun search(query: String): String = "/search.json?q=${encode(query)}"

    fun searchTopic(topicId: Long, query: String): String =
        "/search.json?q=${encode("$query topic:$topicId")}"

    fun faq(): String = "/faq"

    fun guidelines(): String = "/guidelines"

    fun tos(): String = "/tos"

    fun privacy(): String = "/privacy"

    fun group(name: String): String = "/g/${encode(name)}"

    fun user(username: String): String = "/u/${encode(username)}.json"

    fun userSummary(username: String): String = "/u/${encode(username)}/summary.json"

    fun login(): String = "/login"

    /**
     * HTML identity pages where a current_user cookie is not yet a finished
     * IntelliDo sign-in. API paths such as /session/current.json are excluded.
     */
    fun isAuthLocation(url: String): Boolean {
        val path = pathOf(url)?.lowercase() ?: return false
        return path == "/login" || path.startsWith("/login/") ||
            path == "/signup" || path.startsWith("/signup/") ||
            path == "/u/login" ||
            path.startsWith("/session/email-login") ||
            path.startsWith("/session/passkey") ||
            path.startsWith("/auth/")
    }

    private fun pathOf(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return null
        }
        val absolute = if (raw.startsWith("/")) ORIGIN + raw else raw
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()
        if (host != null && host != "linux.do" && host != "www.linux.do") {
            return null
        }
        return uri.path.orEmpty().trimEnd('/').ifEmpty { "/" }
    }

    fun sessionCurrent(): String = "/session/current.json"

    fun sessionCsrf(): String = "/session/csrf"

    fun session(): String = "/session"

    fun createdBy(username: String, page: Int = 0): String =
        paged("/topics/created-by/${encode(username)}.json", page)

    fun postReplies(postId: Long): String = "/posts/$postId/replies.json"

    const val MESSAGE_BUS_ORIGIN: String = "https://ping.ldstatic.com"

    fun messageBusPoll(clientId: String, origin: String = MESSAGE_BUS_ORIGIN): String =
        "${origin.trimEnd('/')}/message-bus/${encode(clientId)}/poll"

    private fun paged(path: String, page: Int): String =
        if (page <= 0) path else "$path?page=$page"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

fun interface LinuxDoJsonFetcher {
    fun get(path: String): String

    fun post(path: String, form: Map<String, String>, timeoutSec: Long = 40L): String {
        throw UnsupportedOperationException("LINUX DO JSON fetcher does not support POST")
    }

    fun delete(path: String, headers: Map<String, String> = emptyMap()): String {
        throw UnsupportedOperationException("LINUX DO JSON fetcher does not support DELETE")
    }

    fun clearCookies() = Unit

    fun postStream(
        path: String,
        form: Map<String, String>,
        timeoutSec: Long,
        onChunk: (String) -> Unit,
    ): String {
        val body = post(path, form, timeoutSec)
        if (body.isNotBlank()) {
            onChunk(body)
        }
        return body
    }
}

fun interface MessageBusPoller {
    fun poll(clientId: String, channels: Map<String, Long>, seq: Int): String

    fun poll(
        clientId: String,
        channels: Map<String, Long>,
        seq: Int,
        onChunk: (String) -> Unit,
    ): String {
        val json = poll(clientId, channels, seq)
        if (json.isNotBlank()) {
            onChunk(json)
        }
        return json
    }
}

class JsonFetcherMessageBusPoller(
    private val fetch: LinuxDoJsonFetcher,
    private val origin: () -> String = { LinuxDoUrls.MESSAGE_BUS_ORIGIN },
) : MessageBusPoller {
    override fun poll(clientId: String, channels: Map<String, Long>, seq: Int): String {
        val chunks = mutableListOf<String>()
        poll(clientId, channels, seq) { chunk -> chunks += chunk }
        return chunks.joinToString(" | ").ifBlank { "[]" }
    }

    override fun poll(
        clientId: String,
        channels: Map<String, Long>,
        seq: Int,
        onChunk: (String) -> Unit,
    ): String =
        fetch.postStream(
            LinuxDoUrls.messageBusPoll(clientId, origin()),
            DiscourseMessageBus.pollForm(channels, seq),
            DiscourseMessageBus.POLL_TIMEOUT_SEC,
            onChunk,
        )
}

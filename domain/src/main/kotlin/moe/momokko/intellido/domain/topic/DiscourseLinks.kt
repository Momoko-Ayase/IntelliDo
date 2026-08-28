package moe.momokko.intellido.domain.topic

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class DiscourseLink {
    data class Topic(val topicId: Long, val postNumber: Int? = null, val slug: String? = null) : DiscourseLink()
    data class Tag(val name: String) : DiscourseLink()
    data class Category(val categoryId: Long) : DiscourseLink()
    data class User(val username: String) : DiscourseLink()
    data class Group(val name: String) : DiscourseLink()
    data class Directory(val page: DirectoryPage) : DiscourseLink()
    data class Page(val path: String) : DiscourseLink()

    enum class DirectoryPage {
        ABOUT,
        CATEGORIES,
        TAGS,
        GROUPS,
        BADGES,
        MEMBERS,
    }
}

object DiscourseLinks {
    fun canonical(topicId: Long, slug: String? = null, postNumber: Int? = null): String {
        val path = when {
            !slug.isNullOrBlank() && postNumber != null -> "/t/$slug/$topicId/$postNumber"
            !slug.isNullOrBlank() -> "/t/$slug/$topicId"
            postNumber != null -> "/t/$topicId/$postNumber"
            else -> "/t/$topicId"
        }
        return "https://linux.do$path"
    }

    fun parse(url: String): DiscourseLink? {
        val path = pathOf(url) ?: return null
        parseTopic(path)?.let { return it }
        parseUser(path)?.let { return it }
        parseCategory(path)?.let { return it }
        parseTag(path)?.let { return it }
        parseGroup(path)?.let { return it }
        parseDirectory(path)?.let { return it }
        parsePage(path)?.let { return it }
        return null
    }

    private fun pathOf(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return null
        }
        if (raw.startsWith("/")) {
            return raw
        }
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "linux.do" && host != "www.linux.do") {
            return null
        }
        return uri.path.ifBlank { "/" }
    }

    private fun parseTopic(path: String): DiscourseLink.Topic? {
        TOPIC_ID_ONLY.find(path)?.let { match ->
            val id = match.groupValues[1].toLong()
            val post = match.groupValues[2].toIntOrNull()
            return DiscourseLink.Topic(id, post)
        }
        TOPIC_WITH_SLUG.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val id = match.groupValues[2].toLong()
            val post = match.groupValues[3].toIntOrNull()
            return DiscourseLink.Topic(id, post, slug.takeUnless { it == "topic" })
        }
        return null
    }

    private fun parseUser(path: String): DiscourseLink.User? {
        val match = USER.find(path) ?: return null
        val username = decode(match.groupValues[1])
        if (username.isBlank()) {
            return null
        }
        return DiscourseLink.User(username)
    }

    private fun parseGroup(path: String): DiscourseLink.Group? {
        val match = GROUP.find(path) ?: return null
        val name = decode(match.groupValues[1])
        if (name.isBlank()) {
            return null
        }
        return DiscourseLink.Group(name)
    }

    private fun parseDirectory(path: String): DiscourseLink.Directory? {
        val clean = path.substringBefore('?').trimEnd('/').lowercase()
        return when (clean) {
            "/about" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.ABOUT)
            "/categories" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.CATEGORIES)
            "/tags" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.TAGS)
            "/groups" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.GROUPS)
            "/badges" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.BADGES)
            "/u", "/directory_items" -> DiscourseLink.Directory(DiscourseLink.DirectoryPage.MEMBERS)
            else -> null
        }
    }

    private fun parsePage(path: String): DiscourseLink.Page? {
        val clean = path.substringBefore('?').trimEnd('/').lowercase()
        return when (clean) {
            "/faq", "/guidelines", "/tos", "/privacy", "/privacy-policy" -> DiscourseLink.Page(clean)
            else -> null
        }
    }

    private fun parseCategory(path: String): DiscourseLink.Category? {
        val match = CATEGORY.find(path) ?: return null
        return DiscourseLink.Category(match.groupValues[1].toLong())
    }

    private fun parseTag(path: String): DiscourseLink.Tag? {
        val match = TAG.find(path) ?: return null
        val name = decode(match.groupValues[1])
        if (name.isBlank()) {
            return null
        }
        return DiscourseLink.Tag(name)
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

    private val TOPIC_ID_ONLY = Regex("""/t/(\d+)(?:/(\d+))?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
    private val TOPIC_WITH_SLUG = Regex("""/t/([^/]+)/(\d+)(?:/(\d+))?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
    private val USER = Regex("""/u/([^/?#]+)""", RegexOption.IGNORE_CASE)
    private val CATEGORY = Regex("""/c/(?:[^/?#]+/)*?(\d+)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""/tag/([^/?#]+)""", RegexOption.IGNORE_CASE)
    private val GROUP = Regex("""/g/([^/?#]+)""", RegexOption.IGNORE_CASE)
}

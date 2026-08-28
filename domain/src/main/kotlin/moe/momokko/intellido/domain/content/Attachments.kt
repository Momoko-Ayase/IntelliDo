package moe.momokko.intellido.domain.content

data class Attachment(
    val url: String,
    val filename: String,
)

/**
 * Guest attachment detection. Download still requires an explicit Save dialog.
 */
object Attachments {
    fun fromCooked(html: String): List<Attachment> {
        val found = linkedMapOf<String, Attachment>()
        ANCHOR.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val classes = CLASS_ATTR.find(attrs)?.groupValues?.get(1).orEmpty()
            if (classes.split(Regex("\\s+")).none { it.equals("attachment", ignoreCase = true) }) {
                return@forEach
            }
            val href = HREF_ATTR.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isEmpty()) {
                return@forEach
            }
            val absolute = absolute(href)
            if (!isAttachmentUrl(absolute)) {
                return@forEach
            }
            found[absolute] = Attachment(absolute, suggestedName(absolute, DiscourseJsonStrip.strip(body)))
        }
        return found.values.toList()
    }

    fun isAttachmentUrl(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        val trusted = host == "linux.do" ||
            host.endsWith(".linux.do") ||
            host == "ldstatic.com" ||
            host.endsWith(".ldstatic.com")
        if (!trusted) {
            return false
        }
        return "/uploads/" in lower || "/short-url/" in lower
    }

    fun suggestedName(url: String, linkText: String = ""): String {
        val fromLink = linkText.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fromLink.isNotEmpty() && '.' in fromLink && '/' !in fromLink && fromLink != url) {
            return fromLink
        }
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val last = path.substringAfterLast('/').substringBefore('?')
        return last.takeIf { it.isNotBlank() && '.' in it } ?: "download"
    }

    private fun absolute(href: String): String =
        when {
            href.startsWith("https://") || href.startsWith("http://") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://linux.do$href"
            else -> href
        }

    private val ANCHOR = Regex("""<a\s([^>]+)>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    private val HREF_ATTR = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val CLASS_ATTR = Regex("""class\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}

private object DiscourseJsonStrip {
    fun strip(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}

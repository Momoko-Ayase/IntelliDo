package moe.momokko.intellido.domain.topic

/**
 * Turns discourse-boosts cooked HTML into a single-line chip label.
 */
object BoostContent {
    data class Parsed(
        val displayText: String,
        val groupingKey: String,
    )

    data class Group(
        val displayText: String,
        val groupingKey: String,
        val boosts: List<PostBoost>,
    ) {
        val count: Int get() = boosts.size
    }

    fun parse(cooked: String): Parsed {
        val display = flatten(cooked)
        return Parsed(
            displayText = display,
            groupingKey = display.ifEmpty { cooked.replace(WHITESPACE, " ").trim() },
        )
    }

    fun groups(boosts: List<PostBoost>): List<Group> {
        val grouped = linkedMapOf<String, MutableList<PostBoost>>()
        boosts.forEach { boost ->
            val key = parse(boost.cookedHtml).groupingKey
            grouped.getOrPut(key) { mutableListOf() }.add(boost)
        }
        return grouped.map { (key, items) ->
            Group(
                displayText = parse(items.first().cookedHtml).displayText,
                groupingKey = key,
                boosts = items.toList(),
            )
        }
    }

    private fun flatten(cooked: String): String {
        val withEmoji = IMG_TAG.replace(cooked) { match ->
            val tag = match.value
            if (!isEmoji(tag)) {
                return@replace " "
            }
            val name = emojiName(tag)
            if (name.isEmpty()) " " else ":$name:"
        }
        val spaced = BLOCK.replace(withEmoji) { " " }
        val stripped = TAG.replace(spaced, "")
        return decode(stripped).replace(WHITESPACE, " ").trim()
    }

    private fun isEmoji(tag: String): Boolean {
        val classes = CLASS.find(tag)?.groupValues?.get(1).orEmpty()
        if (classes.split(Regex("\\s+")).any { it.equals("emoji", ignoreCase = true) }) {
            return true
        }
        val alt = attr(tag, ALT)
        return alt.length >= 3 && alt.startsWith(":") && alt.endsWith(":")
    }

    private fun emojiName(tag: String): String {
        listOf(attr(tag, TITLE), attr(tag, ALT)).forEach { value ->
            val name = value.trim().removeSurrounding(":").trim()
            if (name.isNotEmpty()) {
                return name
            }
        }
        val src = attr(tag, SRC)
        val file = src.substringAfterLast('/').substringBefore('?')
        val base = file.substringBeforeLast('.')
        return base.trim().takeIf { it.isNotEmpty() }.orEmpty()
    }

    private fun attr(tag: String, pattern: Regex): String =
        pattern.find(tag)?.groupValues?.get(1).orEmpty()

    private fun decode(text: String): String =
        text.replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val BLOCK = Regex("</?(p|div|li|ul|ol|blockquote|br)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val CLASS = Regex("""class\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val ALT = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val SRC = Regex("""src\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
}

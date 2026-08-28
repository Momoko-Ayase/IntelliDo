package moe.momokko.intellido.platform.topic

import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.util.Locale

/**
 * Discourse small-action `action_code` values, including `.enabled` / `.disabled`.
 */
object TopicActionLabels {
    fun label(code: String?, locale: Locale, cookedPlain: String = ""): String {
        keys(code).forEach { key ->
            IntelliDoStrings.messageOrNull("topic.action.$key", locale)?.let { return it }
        }
        val cooked = cookedPlain.trim()
        if (cooked.isNotEmpty() && cooked.length < 80) {
            return cooked
        }
        return IntelliDoStrings.message("topic.action.unknown", locale)
    }

    fun icon(code: String?): String? {
        val raw = code?.trim().orEmpty()
        val base = raw.removeSuffix(".enabled").removeSuffix(".disabled")
        return when (base) {
            "pinned_globally", "pinned", "unpinned_globally", "unpinned" -> "thumbtack"
            else -> null
        }
    }

    internal fun keys(code: String?): List<String> {
        val raw = code?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyList()
        }
        val keys = mutableListOf(raw)
        if ('.' !in raw) {
            keys += "$raw.enabled"
        }
        return keys
    }
}

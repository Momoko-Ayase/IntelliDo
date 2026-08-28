package moe.momokko.intellido.platform.topic

import java.util.Locale

/**
 * LINUX DO serves Discourse system badges in the request locale.
 * JCEF's Chromium often asks for English; keep the public card in Chinese.
 */
object DiscourseBadgeLabels {
    fun name(id: Long, raw: String, locale: Locale): String {
        if (!isChinese(locale) || looksChinese(raw)) {
            return raw
        }
        return BY_ID[id] ?: BY_ENGLISH[raw.trim()] ?: raw
    }

    fun description(raw: String, locale: Locale): String {
        if (!isChinese(locale) || raw.isBlank() || looksChinese(raw)) {
            return raw
        }
        POPULAR_LINK.find(raw)?.let { match ->
            return "分享的链接被点击了 ${match.groupValues[1]} 次"
        }
        SOLVED.find(raw)?.let { match ->
            return "有 ${match.groupValues[1]} 条回复被标记为解决方案"
        }
        if (raw.startsWith("Granted global edit")) {
            return "授予全局编辑、置顶、关闭、归档、拆分与合并、更多赞"
        }
        return raw
    }

    private fun isChinese(locale: Locale): Boolean = locale.language == "zh"

    private fun looksChinese(text: String): Boolean = text.any { it in '一'..'鿿' }

    private val BY_ID: Map<Long, String> = mapOf(
        4L to "领导者",
        28L to "热门链接",
        112L to "解决方案机构",
    )

    private val BY_ENGLISH: Map<String, String> = mapOf(
        "Leader" to "领导者",
        "Popular Link" to "热门链接",
        "Solution Institution" to "解决方案机构",
    )

    private val POPULAR_LINK = Regex("""Posted an external link with (\d+) clicks""", RegexOption.IGNORE_CASE)
    private val SOLVED = Regex("""Have (\d+) replies marked as Solutions""", RegexOption.IGNORE_CASE)
}

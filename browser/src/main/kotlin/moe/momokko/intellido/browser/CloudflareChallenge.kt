package moe.momokko.intellido.browser

import java.net.URI

object CloudflareChallenge {
    private val MARKERS: List<String> = listOf(
        "just a moment",
        "checking your browser",
        "verify you are human",
        "attention required",
        "确认您是真人",
        "请完成安全检查",
        "正在验证您是否",
        "人机验证",
        "cf-turnstile",
    )

    private val JSON_KEYS: List<String> = listOf(
        "\"topic_list\"",
        "\"post_stream\"",
        "\"category_list\"",
        "\"directory_items\"",
        "\"badges\"",
        "\"groups\"",
        "\"errors\"",
    )

    data class PageProbe(
        val url: String,
        val ready: Boolean,
        val turnstile: Boolean,
        val text: String,
    )

    const val PROBE_DELIM: String = "::"

    fun parsePageProbe(payload: String): PageProbe {
        val parts = payload.split(PROBE_DELIM, limit = 3)
        if (parts.size >= 2 && parts[0].trim().lowercase() in FLAGS) {
            val flag = parts[0].trim().lowercase()
            return PageProbe(
                url = parts[1].trim(),
                ready = flag == "ready",
                turnstile = flag == "turnstile",
                text = parts.getOrNull(2).orEmpty(),
            )
        }
        return PageProbe(payload.trim().substringBefore('\n'), ready = false, turnstile = false, text = "")
    }

    fun isChallengeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "cdn-cgi" in lower || "/challenge" in lower
    }

    fun isChallenge(httpStatus: Int, body: String): Boolean {
        val lower = body.lowercase()
        return MARKERS.any { it in lower }
    }

    fun isLinuxDoHost(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "linux.do" || host == "www.linux.do"
    }

    fun isCommunityShell(url: String, ready: Boolean, text: String = ""): Boolean =
        !isChallengeUrl(url) && isLinuxDoHost(url) && (ready || looksLikePassedHome(text))

    fun looksLikePassedHome(text: String): Boolean {
        if (text.isBlank() || isChallenge(200, text)) {
            return false
        }
        val home = "Latest" in text || "最新" in text ||
            "Log In" in text || "登录" in text ||
            "社区准则" in text || "真诚" in text ||
            "LINUX DO" in text || "Where possible" in text
        return text.length >= 8 && home
    }

    /**
     * LINUX DO's interstitial is a logo plus Turnstile. Close as soon as the real
     * site is visible. Leftover hidden Turnstile iframes must not keep the dialog open.
     */
    fun dialogMayClose(probe: PageProbe, sawTurnstile: Boolean): Boolean {
        if (isChallengeUrl(probe.url) || !isLinuxDoHost(probe.url)) {
            return false
        }
        if (probe.ready || looksLikePassedHome(probe.text)) {
            return true
        }
        if (probe.turnstile) {
            return false
        }
        return sawTurnstile
    }

    private val FLAGS: Set<String> = setOf("ready", "wait", "turnstile")

    fun looksLikeCommunityJson(body: String): Boolean {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) {
            return false
        }
        return JSON_KEYS.any { it in trimmed }
    }

    fun isExpectedPayload(url: String, json: String): Boolean {
        val u = url.lowercase()
        val trimmed = json.trim()
        if (u.contains("/message-bus/")) {
            return trimmed.startsWith("[") && trimmed.endsWith("]")
        }
        if (!trimmed.startsWith("{")) {
            return false
        }
        return when {
            u.contains("/categories.json") -> "\"category_list\"" in json
            u.contains("/site.json") -> "\"categories\"" in json || "\"default_archetype\"" in json
            u.contains("/latest.json") || u.contains("/l/latest.json") ||
                u.contains("/hot.json") || u.contains("/top.json") -> "\"topic_list\"" in json
            "/tag/" in u && u.endsWith(".json") -> "\"topic_list\"" in json
            u.contains("/posts.json") -> "\"cooked\"" in json || "\"post_stream\"" in json
            Regex("/t/\\d+\\.json").containsMatchIn(u) -> "\"post_stream\"" in json
            u.contains("/tags.json") -> "\"tags\"" in json
            u.contains("/groups.json") -> "\"groups\"" in json
            u.contains("/badges.json") -> "\"badges\"" in json
            u.contains("/about.json") -> "\"about\"" in json
            u.contains("/directory_items") -> "\"directory_items\"" in json
            u.contains("/search.json") -> "\"topics\"" in json || "\"posts\"" in json
            else -> looksLikeCommunityJson(json)
        }
    }
}

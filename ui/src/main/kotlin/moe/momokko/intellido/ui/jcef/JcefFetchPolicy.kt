package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.browser.CloudflareChallenge

/**
 * When a JCEF in-page fetch is empty or slow, that is not a Cloudflare challenge.
 * Opening the challenge dialog on timeouts is what made topic loads appear stuck.
 */
object JcefFetchPolicy {
    const val JSON_GAP_MS: Long = 80
    const val MEDIA_GAP_MS: Long = 0
    /** Document load-end is enough to run fetch(); do not wait for Ember assets. */
    const val ORIGIN_SETTLE_MS: Int = 50
    const val ORIGIN_LOAD_TIMEOUT_SEC: Long = 8
    const val ORIGIN_PROBE_TIMEOUT_SEC: Long = 2
    /** One csrf probe, then the visible challenge. Extra hangs are what made Home look stuck. */
    const val ORIGIN_PROBES: Int = 1
    const val EMPTY_RETRY: Int = 1
    const val EMPTY_RETRY_MS: Long = 250
    const val ACCEPT_LANGUAGE: String = "zh-CN,zh;q=0.9"
    const val DISCOURSE_LOCALE: String = "zh_CN"
    const val JSON_ACCEPT: String = "application/json, text/plain, */*"
    /**
     * Discourse XHR headers. Do not send the IntelliDo product User-Agent here:
     * in-page `fetch()` already carries Chromium's UA, and a custom client token
     * is a Cloudflare bot-management tell.
     */
    const val JSON_FETCH_HEADERS_JS: String =
        "'Accept':'$JSON_ACCEPT'," +
            "'Accept-Language':'$ACCEPT_LANGUAGE'," +
            "'X-Requested-With':'XMLHttpRequest'"
    const val SITE_FETCH_TIMEOUT_SEC: Long = 25

    fun isSiteJson(url: String): Boolean = "/site.json" in url.lowercase()

    fun needsChallengeDialog(payload: String): Boolean {
        if (payload.isBlank()) {
            return false
        }
        return CloudflareChallenge.isChallenge(0, payload)
    }

    fun flattenJson(source: String): String = source.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
}

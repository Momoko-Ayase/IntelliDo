package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.browser.CloudflareChallenge

/**
 * When a JCEF in-page fetch is empty or slow, that is not a Cloudflare challenge.
 * Opening the challenge dialog on timeouts is what made topic loads appear stuck.
 */
object JcefFetchPolicy {
    const val JSON_GAP_MS: Long = 80
    const val MEDIA_GAP_MS: Long = 0
    const val ORIGIN_SETTLE_MS: Int = 200
    const val EMPTY_RETRY: Int = 2
    const val EMPTY_RETRY_MS: Long = 250
    const val ACCEPT_LANGUAGE: String = "zh-CN,zh;q=0.9"
    const val DISCOURSE_LOCALE: String = "zh_CN"

    fun needsChallengeDialog(payload: String): Boolean {
        if (payload.isBlank()) {
            return false
        }
        return CloudflareChallenge.isChallenge(0, payload)
    }

    fun flattenJson(source: String): String = source.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
}

package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.browser.CloudflareChallenge
import moe.momokko.intellido.domain.browse.BrowseDecision
import moe.momokko.intellido.domain.browse.BrowseRouter
import moe.momokko.intellido.domain.browse.TrustedOrigins
import moe.momokko.intellido.domain.content.Attachments
import moe.momokko.intellido.domain.topic.DiscourseLink

/**
 * Shared allowlist for every JCEF surface. Returning [Block] cancels CEF navigation
 * without a side effect; the other variants are handled by the caller.
 *
 * [pinLinuxDo] is for the hidden JSON fetcher: its main frame must stay on linux.do.
 * Visible in-app tabs still honour [BrowseRouter] (including Cloudflare as InApp).
 */
sealed class JcefNav {
    data object Allow : JcefNav()
    data object Block : JcefNav()
    data class Native(val link: DiscourseLink) : JcefNav()
    data class External(val url: String) : JcefNav()
    data class Confirm(val url: String) : JcefNav()
    data class Copy(val url: String) : JcefNav()
    data class Download(val url: String, val name: String) : JcefNav()
}

object JcefNavigation {
    fun decide(
        url: String,
        mainFrame: Boolean,
        pinLinuxDo: Boolean = false,
        nativeStaysInCef: Boolean = false,
        authFlow: Boolean = false,
    ): JcefNav {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return JcefNav.Block
        }
        if (!mainFrame) {
            if (authFlow && BrowseRouter.shouldLoadInCef(raw, mainFrame = false)) {
                return JcefNav.Allow
            }
            if (authFlow && TrustedOrigins.originOf(raw)?.startsWith("https://") == true) {
                return JcefNav.Allow
            }
            return if (BrowseRouter.shouldLoadInCef(raw, mainFrame = false)) JcefNav.Allow else JcefNav.Block
        }
        if (Attachments.isAttachmentUrl(raw)) {
            return JcefNav.Download(raw, Attachments.suggestedName(raw))
        }
        if (authFlow) {
            return decideAuthFlow(raw)
        }
        if (pinLinuxDo && !isPinnedMainFrame(raw)) {
            return when (val decision = BrowseRouter.decide(raw)) {
                is BrowseDecision.External -> JcefNav.External(decision.url)
                is BrowseDecision.Confirm -> JcefNav.Confirm(decision.url)
                is BrowseDecision.CopyOnly -> JcefNav.Copy(decision.url)
                else -> JcefNav.Block
            }
        }
        return when (val decision = BrowseRouter.decide(raw)) {
            is BrowseDecision.Native -> if (nativeStaysInCef) JcefNav.Allow else JcefNav.Native(decision.link)
            is BrowseDecision.InApp -> JcefNav.Allow
            is BrowseDecision.External -> JcefNav.External(decision.url)
            is BrowseDecision.Confirm -> JcefNav.Confirm(decision.url)
            is BrowseDecision.CopyOnly -> JcefNav.Copy(decision.url)
            BrowseDecision.Blocked -> JcefNav.Block
        }
    }

    fun download(url: String, suggestedName: String): JcefNav {
        if (!Attachments.isAttachmentUrl(url)) {
            return JcefNav.Block
        }
        return JcefNav.Download(url, suggestedName.ifBlank { Attachments.suggestedName(url) })
    }

    private fun isPinnedMainFrame(url: String): Boolean =
        CloudflareChallenge.isLinuxDoHost(url)

    /**
     * Login / OAuth stays in the modal for HTTPS. Unrelated schemes still follow
     * the usual confirm / copy / system-browser rules.
     */
    private fun decideAuthFlow(url: String): JcefNav =
        when (val decision = BrowseRouter.decide(url)) {
            is BrowseDecision.Native, is BrowseDecision.InApp -> JcefNav.Allow
            is BrowseDecision.External ->
                if (url.startsWith("https://", ignoreCase = true)) JcefNav.Allow else JcefNav.External(decision.url)
            is BrowseDecision.Confirm -> JcefNav.Confirm(decision.url)
            is BrowseDecision.CopyOnly -> JcefNav.Copy(decision.url)
            BrowseDecision.Blocked -> JcefNav.Block
        }
}

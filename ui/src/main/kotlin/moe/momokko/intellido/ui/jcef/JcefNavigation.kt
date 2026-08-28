package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.browser.CloudflareChallenge
import moe.momokko.intellido.domain.browse.BrowseDecision
import moe.momokko.intellido.domain.browse.BrowseRouter
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
    ): JcefNav {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return JcefNav.Block
        }
        if (!mainFrame) {
            return if (BrowseRouter.shouldLoadInCef(raw, mainFrame = false)) JcefNav.Allow else JcefNav.Block
        }
        if (Attachments.isAttachmentUrl(raw)) {
            return JcefNav.Download(raw, Attachments.suggestedName(raw))
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
}

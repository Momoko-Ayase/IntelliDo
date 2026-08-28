package moe.momokko.intellido.domain.browse

import moe.momokko.intellido.domain.topic.DiscourseLink
import moe.momokko.intellido.domain.topic.DiscourseLinks
import java.net.URI

sealed class BrowseDecision {
    data class Native(val link: DiscourseLink) : BrowseDecision()
    data class InApp(val url: String, val origin: String) : BrowseDecision()
    data class External(val url: String) : BrowseDecision()
    data class Confirm(val url: String) : BrowseDecision()
    data class CopyOnly(val url: String) : BrowseDecision()
    data object Blocked : BrowseDecision()
}

object TrustedOrigins {
    val VISIBLE: Set<String> = setOf(
        "https://linux.do",
        "https://connect.linux.do",
        "https://idcflare.com",
        "https://go.linux.do",
    )

    /**
     * Widget origins that must load inside CEF (Turnstile). They are not
     * trusted-service tabs; they only exist so verification can complete.
     */
    val EMBEDDED: Set<String> = setOf(
        "https://challenges.cloudflare.com",
    )

    fun originOf(url: String): String? {
        val uri = runCatching { URI(absolute(url)) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") {
            return null
        }
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    fun isAllowed(origin: String): Boolean = origin.trimEnd('/') in VISIBLE

    fun isEmbedded(origin: String): Boolean = origin.trimEnd('/') in EMBEDDED

    fun isLoadAllowed(origin: String): Boolean = isAllowed(origin) || isEmbedded(origin)

    fun absolute(url: String): String {
        val raw = url.trim()
        return when {
            raw.startsWith("/") -> "https://linux.do$raw"
            else -> raw
        }
    }
}

/**
 * Routes a URL to native IntelliDo, the allowlisted in-app browser, or the OS.
 */
object BrowseRouter {
    fun decide(url: String): BrowseDecision {
        val raw = url.trim()
        if (raw.isEmpty()) {
            return BrowseDecision.Blocked
        }
        val scheme = schemeOf(raw)
        if (scheme in BLOCKED_SCHEMES) {
            return BrowseDecision.CopyOnly(raw)
        }
        if (scheme == "http" || scheme == "mailto") {
            return BrowseDecision.Confirm(TrustedOrigins.absolute(raw))
        }
        DiscourseLinks.parse(raw)?.let { link ->
            if (link is DiscourseLink.Page) {
                val absolute = TrustedOrigins.absolute(raw)
                val origin = TrustedOrigins.originOf(absolute) ?: return BrowseDecision.External(absolute)
                return if (TrustedOrigins.isAllowed(origin)) {
                    BrowseDecision.InApp(absolute, origin)
                } else {
                    BrowseDecision.External(absolute)
                }
            }
            return BrowseDecision.Native(link)
        }
        val absolute = TrustedOrigins.absolute(raw)
        val origin = TrustedOrigins.originOf(absolute) ?: return BrowseDecision.Blocked
        if (origin.startsWith("http://")) {
            return BrowseDecision.Confirm(absolute)
        }
        if (TrustedOrigins.isLoadAllowed(origin)) {
            return BrowseDecision.InApp(absolute, origin)
        }
        if (absolute.startsWith("https://")) {
            return BrowseDecision.External(absolute)
        }
        return BrowseDecision.Blocked
    }

    /**
     * Whether CEF should continue loading [url]. Subframes never bounce to the
     * system browser: Turnstile lives on challenges.cloudflare.com inside an iframe.
     */
    fun shouldLoadInCef(url: String, mainFrame: Boolean): Boolean {
        val raw = url.trim()
        if (!mainFrame) {
            val scheme = schemeOf(raw)
            if (scheme in FRAME_SCHEMES) {
                return true
            }
            val origin = TrustedOrigins.originOf(raw) ?: return false
            return TrustedOrigins.isLoadAllowed(origin)
        }
        return when (decide(raw)) {
            is BrowseDecision.InApp, is BrowseDecision.Native -> true
            else -> false
        }
    }

    private fun schemeOf(url: String): String? {
        val idx = url.indexOf(':')
        if (idx <= 0) {
            return if (url.startsWith("/")) "https" else null
        }
        return url.substring(0, idx).lowercase()
    }

    private val BLOCKED_SCHEMES: Set<String> = setOf("file", "javascript", "data", "about", "blob")

    private val FRAME_SCHEMES: Set<String> = setOf("about", "blob", "data")
}

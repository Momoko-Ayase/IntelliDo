package moe.momokko.intellido.domain.site

import java.net.URI

data class SiteSettings(
    val longPollingBaseUrl: String = DEFAULT_LONG_POLLING,
) {
    /**
     * The poll URL is fetched from inside the LINUX DO JCEF origin with
     * credentials, so a server-supplied value is only honoured when it is an
     * HTTPS origin on a known LINUX DO host.
     */
    val messageBusOrigin: String
        get() {
            val candidate = longPollingBaseUrl.trim().trimEnd('/')
            return if (isTrustedOrigin(candidate)) candidate else DEFAULT_LONG_POLLING
        }

    companion object {
        const val DEFAULT_LONG_POLLING: String = "https://ping.ldstatic.com"

        fun isTrustedOrigin(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (uri.scheme?.lowercase() != "https") {
                return false
            }
            if (!uri.userInfo.isNullOrEmpty()) {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            return host == "linux.do" ||
                host.endsWith(".linux.do") ||
                host == "ldstatic.com" ||
                host.endsWith(".ldstatic.com")
        }
    }
}

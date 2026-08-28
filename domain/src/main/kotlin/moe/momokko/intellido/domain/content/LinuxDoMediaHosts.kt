package moe.momokko.intellido.domain.content

object LinuxDoMediaHosts {
    fun isTrusted(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "linux.do" ||
            h.endsWith(".linux.do") ||
            h == "ldstatic.com" ||
            h.endsWith(".ldstatic.com") ||
            h == "emoji.discourse-cdn.com"
    }
}

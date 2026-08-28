package moe.momokko.intellido.domain.topic

/**
 * Resolves Discourse `avatar_template` values to absolute LINUX DO URLs.
 */
object LinuxDoAvatar {
    fun url(template: String?, size: Int = 48): String? {
        val path = template?.replace("{size}", size.toString())?.trim().orEmpty()
        if (path.isEmpty()) {
            return null
        }
        return when {
            path.startsWith("https://") || path.startsWith("http://") -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> "https://linux.do$path"
            else -> null
        }
    }
}

package moe.momokko.intellido.domain.topic

/**
 * A short free-text micro-response attached to a post. Boosts are not posts.
 */
data class PostBoost(
    val id: Long,
    val cookedHtml: String,
    val username: String,
    val displayName: String? = null,
    val avatarTemplate: String? = null,
) {
    init {
        require(id > 0) { "boost id must be positive" }
        require(username.isNotBlank()) { "boost username must not be blank" }
    }

    fun avatarUrl(size: Int = 48): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

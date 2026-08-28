package moe.momokko.intellido.domain.topic

/**
 * The post this reply is addressing.
 */
data class ReplyTo(
    val postNumber: Int,
    val username: String,
    val avatarTemplate: String? = null,
) {
    init {
        require(postNumber > 0) { "reply-to post number must be positive" }
        require(username.isNotBlank()) { "reply-to username must not be blank" }
    }

    fun avatarUrl(size: Int = 24): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

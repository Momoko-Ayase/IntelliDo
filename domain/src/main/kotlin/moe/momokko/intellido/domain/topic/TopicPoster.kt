package moe.momokko.intellido.domain.topic

/**
 * A participant shown in a Discourse topic-list poster stack.
 */
data class TopicPoster(
    val username: String,
    val avatarTemplate: String? = null,
) {
    init {
        require(username.isNotBlank()) { "poster username must not be blank" }
    }

    fun avatarUrl(size: Int = 48): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

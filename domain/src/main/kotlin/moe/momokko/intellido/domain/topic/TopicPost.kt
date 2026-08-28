package moe.momokko.intellido.domain.topic

import java.time.Instant

/**
 * A single post in a topic timeline. Native UI renders [cookedHtml] through the
 * allowlisted cooked parser; [plainText] is the accessibility fallback.
 */
data class TopicPost(
    val id: Long,
    val postNumber: Int,
    val username: String,
    val cookedHtml: String,
    val plainText: String,
    val createdAt: Instant,
    val displayName: String? = null,
    val avatarTemplate: String? = null,
    val userTitle: String? = null,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val staff: Boolean = false,
    val postType: Int = 1,
    val actionCode: String? = null,
    val replyTo: ReplyTo? = null,
    val reactions: List<PostReaction> = emptyList(),
    val reactionUsersCount: Int = 0,
    val boosts: List<PostBoost> = emptyList(),
    val wiki: Boolean = false,
    val acceptedAnswer: Boolean = false,
    val hidden: Boolean = false,
    val userDeleted: Boolean = false,
    val updatedAt: Instant? = null,
    val version: Int = 1,
    val flairName: String? = null,
    val flairUrl: String? = null,
    val primaryGroupName: String? = null,
) {
    val isSmallAction: Boolean
        get() = postType == 2 || postType == 3 || !actionCode.isNullOrBlank()

    val edited: Boolean
        get() = version > 1

    val visibleReactionCount: Int
        get() = reactionUsersCount.takeIf { it > 0 } ?: likeCount

    init {
        require(id > 0) { "post id must be positive" }
        require(postNumber > 0) { "postNumber must be positive" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(cookedHtml.isNotBlank() || plainText.isNotBlank() || isSmallAction) {
            "post must have cooked or plain text"
        }
        require(likeCount >= 0) { "likeCount must not be negative" }
        require(replyCount >= 0) { "replyCount must not be negative" }
        require(postType > 0) { "postType must be positive" }
        require(reactionUsersCount >= 0) { "reactionUsersCount must not be negative" }
        require(version > 0) { "version must be positive" }
    }

    fun avatarUrl(size: Int = 90): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

package moe.momokko.intellido.domain.live

/**
 * Guest-visible LINUX DO MessageBus events. Login-only channels (notifications,
 * drafts, Chat, unread) are never represented here.
 */
sealed class GuestLiveEvent {
    data class LatestTopic(
        val topicId: Long,
        val categoryId: Long? = null,
    ) : GuestLiveEvent()

    data class TopicPostChanged(
        val topicId: Long,
        val type: String,
        val postId: Long,
        val postNumber: Int = 0,
        val username: String? = null,
    ) : GuestLiveEvent() {
        val created: Boolean get() = type == "created"
        val edited: Boolean get() = type == "revised" || type == "rebaked"
        val deleted: Boolean get() = type == "deleted"
        val recovered: Boolean get() = type == "recovered"
        val destroyed: Boolean get() = type == "destroyed"
    }

    data class TopicPresence(
        val topicId: Long,
        val entering: List<LivePresenceUser> = emptyList(),
        val leavingIds: List<Long> = emptyList(),
    ) : GuestLiveEvent()
}

data class LivePresenceUser(
    val id: Long,
    val username: String,
    val avatarTemplate: String? = null,
    val displayName: String? = null,
) {
    init {
        require(id > 0) { "presence user id must be positive" }
        require(username.isNotBlank()) { "presence username must not be blank" }
    }
}

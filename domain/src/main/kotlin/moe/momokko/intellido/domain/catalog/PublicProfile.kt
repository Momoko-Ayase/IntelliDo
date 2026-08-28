package moe.momokko.intellido.domain.catalog

import moe.momokko.intellido.domain.topic.LinuxDoAvatar
import java.time.Instant

/**
 * Public LINUX DO member card. Guest-visible fields only.
 */
data class PublicProfile(
    val id: Long,
    val username: String,
    val displayName: String? = null,
    val title: String? = null,
    val bioHtml: String? = null,
    val trustLevel: Int = 0,
    val avatarTemplate: String? = null,
    val createdAt: Instant? = null,
    val badgeCount: Int = 0,
    val location: String? = null,
    val website: String? = null,
    val websiteName: String? = null,
    val lastPostedAt: Instant? = null,
    val lastSeenAt: Instant? = null,
    val profileViews: Int = 0,
    val admin: Boolean = false,
    val moderator: Boolean = false,
    val primaryGroupName: String? = null,
    val flairName: String? = null,
    val flairUrl: String? = null,
    val flairBgColor: String? = null,
    val flairColor: String? = null,
    val publicFields: List<ProfileField> = emptyList(),
    val followerCount: Int = 0,
    val gamificationScore: Int = 0,
    val statusEmoji: String? = null,
    val statusDescription: String? = null,
    val featuredBadges: List<ProfileBadge> = emptyList(),
    val summary: PublicProfileSummary? = null,
) {
    init {
        require(id > 0) { "profile id must be positive" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(trustLevel in 0..4) { "trustLevel must be 0-4" }
        require(badgeCount >= 0) { "badgeCount must not be negative" }
        require(profileViews >= 0) { "profileViews must not be negative" }
        require(followerCount >= 0) { "followerCount must not be negative" }
        require(gamificationScore >= 0) { "gamificationScore must not be negative" }
    }

    fun avatarUrl(size: Int = 120): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

data class ProfileField(
    val name: String,
    val value: String,
) {
    init {
        require(name.isNotBlank()) { "profile field name must not be blank" }
        require(value.isNotBlank()) { "profile field value must not be blank" }
    }
}

data class ProfileBadge(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    val count: Int = 1,
) {
    init {
        require(id > 0) { "badge id must be positive" }
        require(name.isNotBlank()) { "badge name must not be blank" }
        require(count >= 0) { "badge count must not be negative" }
    }
}

data class PublicProfileSummary(
    val canSeeStats: Boolean = true,
    val daysVisited: Int = 0,
    val timeReadSeconds: Int = 0,
    val recentTimeReadSeconds: Int = 0,
    val topicsEntered: Int = 0,
    val postsRead: Int = 0,
    val likesGiven: Int = 0,
    val likesReceived: Int = 0,
    val topicCount: Int = 0,
    val postCount: Int = 0,
    val solvedCount: Int = 0,
    val replies: List<ProfileTopicItem> = emptyList(),
    val topics: List<ProfileTopicItem> = emptyList(),
    val links: List<ProfileLink> = emptyList(),
    val topCategories: List<ProfileCategoryStat> = emptyList(),
    val badges: List<ProfileBadge> = emptyList(),
    val mostRepliedTo: List<ProfilePeer> = emptyList(),
    val mostLikedBy: List<ProfilePeer> = emptyList(),
    val mostLiked: List<ProfilePeer> = emptyList(),
) {
    init {
        require(daysVisited >= 0) { "daysVisited must not be negative" }
        require(timeReadSeconds >= 0) { "timeReadSeconds must not be negative" }
        require(recentTimeReadSeconds >= 0) { "recentTimeReadSeconds must not be negative" }
        require(topicsEntered >= 0) { "topicsEntered must not be negative" }
        require(postsRead >= 0) { "postsRead must not be negative" }
        require(likesGiven >= 0) { "likesGiven must not be negative" }
        require(likesReceived >= 0) { "likesReceived must not be negative" }
        require(topicCount >= 0) { "topicCount must not be negative" }
        require(postCount >= 0) { "postCount must not be negative" }
        require(solvedCount >= 0) { "solvedCount must not be negative" }
    }
}

data class ProfileTopicItem(
    val topicId: Long,
    val title: String,
    val likeCount: Int = 0,
    val createdAt: Instant? = null,
    val postNumber: Int? = null,
    val slug: String? = null,
) {
    init {
        require(topicId > 0) { "topic id must be positive" }
        require(title.isNotBlank()) { "topic title must not be blank" }
        require(likeCount >= 0) { "likeCount must not be negative" }
    }

    fun path(): String {
        val base = slug?.takeIf { it.isNotBlank() }?.let { "/t/$it/$topicId" } ?: "/t/$topicId"
        return postNumber?.let { "$base/$it" } ?: base
    }
}

data class ProfileLink(
    val url: String,
    val title: String? = null,
    val clicks: Int = 0,
    val topicId: Long? = null,
) {
    init {
        require(url.isNotBlank()) { "link url must not be blank" }
        require(clicks >= 0) { "clicks must not be negative" }
    }
}

data class ProfileCategoryStat(
    val id: Long,
    val name: String,
    val color: String? = null,
    val topicCount: Int = 0,
    val postCount: Int = 0,
    val slug: String? = null,
    val icon: String? = null,
) {
    init {
        require(id > 0) { "category id must be positive" }
        require(name.isNotBlank()) { "category name must not be blank" }
        require(topicCount >= 0) { "topicCount must not be negative" }
        require(postCount >= 0) { "postCount must not be negative" }
    }

    fun path(): String = slug?.takeIf { it.isNotBlank() }?.let { "/c/$it/$id" } ?: "/c/$id"
}

data class ProfilePeer(
    val id: Long,
    val username: String,
    val displayName: String? = null,
    val avatarTemplate: String? = null,
    val count: Int = 0,
) {
    init {
        require(id > 0) { "peer id must be positive" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(count >= 0) { "count must not be negative" }
    }

    fun avatarUrl(size: Int = 48): String? = LinuxDoAvatar.url(avatarTemplate, size)
}

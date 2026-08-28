package moe.momokko.intellido.domain.catalog

data class CommunityCategory(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val topicCount: Int,
    val readRestricted: Boolean,
    val color: String? = null,
    val icon: String? = null,
    val parentId: Long? = null,
) {
    init {
        require(id > 0) { "category id must be positive" }
        require(name.isNotBlank()) { "category name must not be blank" }
        require(slug.isNotBlank()) { "category slug must not be blank" }
        require(topicCount >= 0) { "topicCount must not be negative" }
    }
}

data class CommunityTag(
    val name: String,
    val topicCount: Int,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "tag name must not be blank" }
        require(topicCount >= 0) { "topicCount must not be negative" }
    }
}

data class CommunityGroup(
    val id: Long,
    val name: String,
    val fullName: String?,
    val memberCount: Int,
    val publicVisible: Boolean,
    val bioHtml: String? = null,
) {
    init {
        require(id > 0) { "group id must be positive" }
        require(name.isNotBlank()) { "group name must not be blank" }
        require(memberCount >= 0) { "memberCount must not be negative" }
    }
}

data class CommunityBadge(
    val id: Long,
    val name: String,
    val description: String,
    val icon: String? = null,
    val grantCount: Int = 0,
    val badgeType: String? = null,
) {
    init {
        require(id > 0) { "badge id must be positive" }
        require(name.isNotBlank()) { "badge name must not be blank" }
        require(grantCount >= 0) { "grantCount must not be negative" }
    }
}

data class PublicMember(
    val id: Long,
    val username: String,
    val name: String?,
    val trustLevel: Int,
    val avatarTemplate: String? = null,
    val title: String? = null,
    val likesReceived: Int = 0,
) {
    init {
        require(id > 0) { "member id must be positive" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(trustLevel in 0..4) { "trustLevel must be 0-4" }
        require(likesReceived >= 0) { "likesReceived must not be negative" }
    }
}

data class CommunityAbout(
    val title: String,
    val description: String,
    val staffUsernames: List<String>,
    val topicCount: Int = 0,
    val postCount: Int = 0,
    val userCount: Int = 0,
    val likeCount: Int = 0,
    val admins: List<PublicMember> = emptyList(),
    val moderators: List<PublicMember> = emptyList(),
    val faqUrl: String? = null,
    val guidelinesUrl: String? = null,
    val tosUrl: String? = null,
    val privacyUrl: String? = null,
) {
    init {
        require(title.isNotBlank()) { "about title must not be blank" }
        require(topicCount >= 0) { "topicCount must not be negative" }
        require(postCount >= 0) { "postCount must not be negative" }
        require(userCount >= 0) { "userCount must not be negative" }
        require(likeCount >= 0) { "likeCount must not be negative" }
    }
}

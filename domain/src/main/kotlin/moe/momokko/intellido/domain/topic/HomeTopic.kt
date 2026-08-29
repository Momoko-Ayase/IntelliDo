package moe.momokko.intellido.domain.topic

import java.time.Instant

/**
 * A public LINUX DO topic summary for list views such as Home.
 */
data class HomeTopic(
    val id: Long,
    val title: String,
    val slug: String,
    val postsCount: Int,
    val replyCount: Int,
    val categoryName: String?,
    val authorUsername: String,
    val lastPostedAt: Instant,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val tags: List<String> = emptyList(),
    val views: Int = 0,
    val pinned: Boolean = false,
    val posters: List<TopicPoster> = emptyList(),
    val likeCount: Int = 0,
    val participantCount: Int = 0,
    val linkCount: Int = 0,
    val closed: Boolean = false,
    val archived: Boolean = false,
    val acceptedAnswer: Boolean = false,
    val wordCount: Int = 0,
    val createdAt: Instant? = null,
    val categoryRestricted: Boolean = false,
) {
    init {
        require(id > 0) { "topic id must be positive" }
        require(title.isNotBlank()) { "topic title must not be blank" }
        require(slug.isNotBlank()) { "topic slug must not be blank" }
        require(postsCount >= 0) { "postsCount must not be negative" }
        require(replyCount >= 0) { "replyCount must not be negative" }
        require(authorUsername.isNotBlank()) { "author username must not be blank" }
        require(views >= 0) { "views must not be negative" }
        require(likeCount >= 0) { "likeCount must not be negative" }
        require(participantCount >= 0) { "participantCount must not be negative" }
        require(linkCount >= 0) { "linkCount must not be negative" }
        require(wordCount >= 0) { "wordCount must not be negative" }
    }

    fun readingMinutes(): Int {
        if (wordCount <= 0) {
            return 0
        }
        return (wordCount + 79) / 80
    }
}

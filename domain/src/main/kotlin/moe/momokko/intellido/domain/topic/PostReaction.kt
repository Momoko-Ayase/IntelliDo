package moe.momokko.intellido.domain.topic

/**
 * A discourse-reactions emoji attached to a post.
 */
data class PostReaction(
    val id: String,
    val count: Int,
    val type: String = "emoji",
) {
    init {
        require(id.isNotBlank()) { "reaction id must not be blank" }
        require(count >= 0) { "reaction count must not be negative" }
    }
}

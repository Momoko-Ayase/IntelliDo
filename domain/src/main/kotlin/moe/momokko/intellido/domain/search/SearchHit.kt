package moe.momokko.intellido.domain.search

/**
 * A public search result. Guest Search Everywhere never includes private messages or Chat.
 */
data class SearchHit(
    val title: String,
    val blurb: String = "",
    val topicId: Long,
    val postNumber: Int? = null,
    val username: String? = null,
    val slug: String? = null,
) {
    init {
        require(title.isNotBlank()) { "search hit title must not be blank" }
        require(topicId > 0) { "search hit topic id must be positive" }
    }
}

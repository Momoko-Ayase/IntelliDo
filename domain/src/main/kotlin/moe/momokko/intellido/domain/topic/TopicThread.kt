package moe.momokko.intellido.domain.topic

/**
 * A topic plus its posts for native continuous reading.
 */
data class TopicThread(
    val topic: HomeTopic,
    val posts: List<TopicPost>,
    val streamIds: List<Long> = posts.map { it.id },
    val messageBusLastId: Long = -1,
) {
    init {
        require(posts.isNotEmpty()) { "topic thread must include at least the original post" }
        require(posts.all { it.postNumber > 0 }) { "post numbers must be positive" }
    }
}

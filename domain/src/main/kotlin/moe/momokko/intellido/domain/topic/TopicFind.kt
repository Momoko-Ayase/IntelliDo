package moe.momokko.intellido.domain.topic

data class TopicFindHit(
    val postId: Long,
    val postNumber: Int,
    val snippet: String,
)

/**
 * In-topic find over already-loaded posts. Unloaded floors are queried separately
 * through LINUX DO search; this object never persists the needle.
 */
object TopicFind {
    fun search(posts: List<TopicPost>, query: String): List<TopicFindHit> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        return posts.mapNotNull { post ->
            val hay = post.plainText
            val index = hay.indexOf(needle, ignoreCase = true)
            if (index < 0) {
                return@mapNotNull null
            }
            TopicFindHit(post.id, post.postNumber, snippet(hay, index, needle.length))
        }
    }

    private fun snippet(text: String, index: Int, length: Int): String {
        val start = (index - 24).coerceAtLeast(0)
        val end = (index + length + 24).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).trim() + suffix
    }
}

package moe.momokko.intellido.domain.live

import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicThread

/**
 * Applies newly fetched posts onto an already-open topic thread.
 */
object TopicLiveMerge {
    fun insert(thread: TopicThread, posts: List<TopicPost>): TopicThread {
        if (posts.isEmpty()) {
            return thread
        }
        val byId = LinkedHashMap<Long, TopicPost>()
        thread.posts.forEach { byId[it.id] = it }
        posts.forEach { byId[it.id] = it }
        val stream = thread.streamIds.toMutableList()
        posts.sortedBy { it.postNumber }.forEach { post ->
            if (post.id !in stream) {
                val before = stream.indexOfFirst { id ->
                    val existing = byId[id]
                    existing != null && existing.postNumber > post.postNumber
                }
                val after = stream.indexOfLast { id ->
                    val existing = byId[id]
                    existing != null && existing.postNumber < post.postNumber
                }
                when {
                    before >= 0 -> stream.add(before, post.id)
                    after >= 0 -> stream.add(after + 1, post.id)
                    else -> stream.add(post.id)
                }
            }
        }
        val ordered = stream.mapNotNull { byId[it] }.ifEmpty { byId.values.toList() }
        return thread.copy(
            topic = thread.topic.copy(
                postsCount = maxOf(thread.topic.postsCount, stream.size, ordered.size),
                replyCount = maxOf(thread.topic.replyCount, (stream.size - 1).coerceAtLeast(0)),
            ),
            posts = ordered,
            streamIds = stream,
        )
    }

    fun replace(thread: TopicThread, posts: List<TopicPost>): TopicThread {
        if (posts.isEmpty()) {
            return thread
        }
        val updates = posts.associateBy { it.id }
        return thread.copy(posts = thread.posts.map { updates[it.id] ?: it })
    }

    fun hide(thread: TopicThread, postId: Long): TopicThread =
        thread.copy(
            posts = thread.posts.map { post ->
                if (post.id == postId) post.copy(userDeleted = true, hidden = true) else post
            },
        )

    fun remove(thread: TopicThread, postId: Long): TopicThread {
        val posts = thread.posts.filterNot { it.id == postId }
        val stream = thread.streamIds.filterNot { it == postId }
        return thread.copy(
            topic = thread.topic.copy(
                postsCount = maxOf(posts.size, stream.size),
                replyCount = (maxOf(posts.size, stream.size) - 1).coerceAtLeast(0),
            ),
            posts = posts,
            streamIds = stream,
        )
    }
}

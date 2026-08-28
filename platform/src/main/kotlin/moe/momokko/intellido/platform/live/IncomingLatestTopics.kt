package moe.momokko.intellido.platform.live

/**
 * Unique topic ids bumped on `/latest` since the last list refresh.
 */
class IncomingLatestTopics {
    private val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    fun note(topicId: Long) {
        if (topicId > 0) {
            ids.add(topicId)
        }
    }

    fun count(): Int = ids.size

    fun clear() {
        ids.clear()
    }
}

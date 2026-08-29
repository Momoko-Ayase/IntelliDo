package moe.momokko.intellido.platform.topic

data class OpenTopicTab(
    val topicId: Long,
    val pinned: Boolean,
)

data class TopicPreviewSnapshot(
    val tabs: List<OpenTopicTab>,
    val focusedTopicId: Long?,
    val previewTopicId: Long?,
)

/**
 * IDEA-style preview: ordinary activation reuses one unpinned tab.
 * Each topic exists at most once.
 */
class TopicPreviewSession {
    private val tabs: LinkedHashMap<Long, OpenTopicTab> = LinkedHashMap()

    fun activate(topicId: Long, pin: Boolean = false): TopicPreviewSnapshot {
        val existing = tabs[topicId]
        if (existing != null) {
            if (pin && !existing.pinned) {
                tabs[topicId] = existing.copy(pinned = true)
            }
            return snapshot(topicId)
        }
        if (!pin) {
            val preview = tabs.values.firstOrNull { !it.pinned }
            if (preview != null) {
                tabs.remove(preview.topicId)
            }
        }
        tabs[topicId] = OpenTopicTab(topicId, pinned = pin)
        return snapshot(topicId)
    }

    fun pin(topicId: Long): TopicPreviewSnapshot {
        val existing = tabs[topicId] ?: return snapshot(tabs.keys.lastOrNull())
        tabs[topicId] = existing.copy(pinned = true)
        return snapshot(topicId)
    }

    fun close(topicId: Long): TopicPreviewSnapshot {
        tabs.remove(topicId)
        return snapshot(tabs.keys.lastOrNull())
    }

    fun clear() {
        tabs.clear()
    }

    fun snapshot(focusedTopicId: Long? = tabs.keys.lastOrNull()): TopicPreviewSnapshot =
        TopicPreviewSnapshot(
            tabs = tabs.values.toList(),
            focusedTopicId = focusedTopicId,
            previewTopicId = tabs.values.firstOrNull { !it.pinned }?.topicId,
        )
}

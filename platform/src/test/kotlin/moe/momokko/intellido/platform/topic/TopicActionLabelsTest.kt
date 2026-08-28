package moe.momokko.intellido.platform.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class TopicActionLabelsTest {
    private val zh = Locale.SIMPLIFIED_CHINESE

    @Test
    fun `discourse pin codes become 全站置顶 not the unknown fallback`() {
        assertEquals("全站置顶", TopicActionLabels.label("pinned_globally.enabled", zh))
        assertEquals("全站置顶", TopicActionLabels.label("pinned_globally", zh))
        assertEquals("取消全站置顶", TopicActionLabels.label("pinned_globally.disabled", zh))
        assertEquals("thumbtack", TopicActionLabels.icon("pinned_globally.enabled"))
        assertNull(TopicActionLabels.icon("closed.enabled"))
    }

    @Test
    fun `unknown codes use cooked text when it is a short action line`() {
        assertEquals("把分类改到了抽奖", TopicActionLabels.label("custom_mod", zh, "把分类改到了抽奖"))
        assertEquals("更新了话题", TopicActionLabels.label("custom_mod", zh, ""))
    }
}

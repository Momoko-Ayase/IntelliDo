package moe.momokko.intellido.platform.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopicPreviewSessionTest {
    @Test
    fun `ordinary activation reuses the single preview tab`() {
        val session = TopicPreviewSession()
        session.activate(101)
        val snapshot = session.activate(102)

        assertEquals(listOf(102L), snapshot.tabs.map { it.topicId })
        assertFalse(snapshot.tabs.single().pinned)
        assertEquals(102L, snapshot.focusedTopicId)
        assertEquals(102L, snapshot.previewTopicId)
    }

    @Test
    fun `pinning keeps the topic while another preview opens`() {
        val session = TopicPreviewSession()
        session.activate(101)
        session.pin(101)
        val snapshot = session.activate(102)

        assertEquals(listOf(101L, 102L), snapshot.tabs.map { it.topicId })
        assertEquals(true, snapshot.tabs[0].pinned)
        assertEquals(false, snapshot.tabs[1].pinned)
        assertEquals(101L, snapshot.tabs.single { it.pinned }.topicId)
    }

    @Test
    fun `the same topic never opens a second tab`() {
        val session = TopicPreviewSession()
        session.activate(101)
        session.pin(101)
        val snapshot = session.activate(101)

        assertEquals(1, snapshot.tabs.size)
        assertEquals(101L, snapshot.focusedTopicId)
        assertNull(snapshot.previewTopicId)
    }
}

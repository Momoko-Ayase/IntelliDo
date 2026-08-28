package moe.momokko.intellido.ui.topic

import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JComponent

class TopicTimelineTest {
    @Test
    fun `the rail is wide enough for floor and date`() {
        assertTrue(TopicTimeline.WIDTH >= 168, "timeline WIDTH=${TopicTimeline.WIDTH}")
    }

    @Test
    fun `dragging to the top jumps to the oldest post`() {
        val jumped = mutableListOf<Int>()
        val thread = FakeLinuxDoCommunityClient().loadTopic(101)
        val timeline = sized(TopicTimeline(thread, Locale.SIMPLIFIED_CHINESE) { index, _ -> jumped += index })
        val track = trackOf(timeline)
        drag(track, 0)
        assertEquals(0, jumped.last(), jumped.toString())
    }

    @Test
    fun `dragging to the bottom jumps to the newest post not the last loaded one`() {
        val jumped = mutableListOf<Int>()
        val thread = FakeLinuxDoCommunityClient().loadTopic(101)
        val timeline = sized(TopicTimeline(thread, Locale.SIMPLIFIED_CHINESE) { index, _ -> jumped += index })
        val track = trackOf(timeline)
        drag(track, track.height)
        assertEquals(thread.streamIds.lastIndex, jumped.last(), "stream=${thread.streamIds} loaded=${thread.posts.size} jumped=$jumped")
        assertTrue(thread.streamIds.lastIndex > thread.posts.lastIndex, "fixture must have unloaded posts")
    }

    @Test
    fun `the handle follows the mouse down the track`() {
        val timeline = sized(
            TopicTimeline(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE) { _, _ -> },
        )
        val track = trackOf(timeline)
        val handle = track.getComponent(0)
        val start = handle.y
        drag(track, track.height)
        assertTrue(handle.y > start, "handle stayed at y=${handle.y}")
        assertTrue(
            handle.y + handle.height >= track.height - 6,
            "handle y=${handle.y} h=${handle.height} track=${track.height}",
        )
    }

    @Test
    fun `bottom of the rail is the newest reply not the first page`() {
        val thread = FakeLinuxDoCommunityClient().loadTopic(101)
        val newest = TopicTimeline.newestAt(thread)
        val oldest = thread.posts.minBy { it.postNumber }.createdAt
        assertTrue(newest.isAfter(oldest), "newest=$newest oldest=$oldest")
        assertEquals(thread.topic.lastPostedAt, newest)
    }

    @Test
    fun `dragging the floor labels keeps the handle under the pointer`() {
        val timeline = sized(
            TopicTimeline(FakeLinuxDoCommunityClient().loadTopic(101), Locale.SIMPLIFIED_CHINESE) { _, _ -> },
        )
        val track = trackOf(timeline)
        val handle = track.getComponent(0) as JComponent
        val origin = handle.y
        drag(handle, handle.height + 90)
        assertTrue(handle.y > origin, "handle did not follow a drag on the labels y=${handle.y} origin=$origin")
    }

    private fun sized(timeline: TopicTimeline): TopicTimeline {
        timeline.size = Dimension(140, 420)
        timeline.doLayout()
        trackOf(timeline).let { track ->
            if (track.height < 80) {
                track.size = Dimension(120, 300)
                track.doLayout()
            }
        }
        return timeline
    }

    private fun trackOf(root: Component): JComponent {
        fun walk(component: Component): JComponent? {
            if (component.javaClass.simpleName == "Track") {
                return component as JComponent
            }
            if (component is Container) {
                component.components.forEach { child ->
                    walk(child)?.let { return it }
                }
            }
            return null
        }
        return walk(root) ?: error("timeline track missing")
    }

    private fun drag(target: Component, y: Int) {
        val now = System.currentTimeMillis()
        target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_PRESSED, now, MouseEvent.BUTTON1_DOWN_MASK, 8, 4, 1, false, MouseEvent.BUTTON1))
        target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_DRAGGED, now + 10, MouseEvent.BUTTON1_DOWN_MASK, 8, y, 1, false, MouseEvent.BUTTON1))
        target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_RELEASED, now + 20, 0, 8, y, 1, false, MouseEvent.BUTTON1))
        target.invalidate()
        if (target is JComponent) {
            target.doLayout()
        }
        target.repaint()
    }
}

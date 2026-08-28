package moe.momokko.intellido.ui.topic

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicThread
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.time.RelativeTime
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * LINUX DO / Discourse topic-timeline: dates, N / total, drag-to-jump.
 * Top of the rail is the oldest post; the bottom is the newest.
 * Guest reading only — no 热门回复 write path.
 */
class TopicTimeline(
    private val thread: TopicThread,
    private val locale: Locale,
    private val onJump: (streamIndex: Int, load: Boolean) -> Unit,
) : JBPanel<TopicTimeline>(BorderLayout()) {
    private val start = JBLabel()
    private val end = JBLabel()
    private val position = JBLabel("", SwingConstants.LEFT)
    private val ago = JBLabel()
    private val track = Track()
    private val oldestAt: Instant = thread.posts.minByOrNull { it.postNumber }?.createdAt
        ?: thread.topic.lastPostedAt
    private var dragging: Boolean = false

    init {
        isOpaque = false
        val width = JBUI.scale(WIDTH)
        preferredSize = Dimension(width, JBUI.scale(360))
        minimumSize = Dimension(width, JBUI.scale(240))
        maximumSize = Dimension(width, Integer.MAX_VALUE)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0),
            JBUI.Borders.empty(28, 10, 12, 4),
        )
        start.foreground = GuestUi.muted
        start.font = GuestUi.metaFont(start.font)
        end.foreground = GuestUi.muted
        end.font = GuestUi.metaFont(end.font)
        position.foreground = GuestUi.muted
        position.font = position.font.deriveFont(position.font.size2D)
        ago.foreground = GuestUi.muted
        ago.font = GuestUi.metaFont(ago.font)
        start.text = RelativeTime.format(oldestAt, locale = locale)
        end.text = RelativeTime.format(newestAt(thread), locale = locale)
        add(start, BorderLayout.NORTH)
        add(track, BorderLayout.CENTER)
        add(end, BorderLayout.SOUTH)
        preview(0)
    }

    fun sync(visible: TopicPost, streamIndex: Int) {
        if (dragging) {
            return
        }
        val last = (totalCount() - 1).coerceAtLeast(1)
        track.ratio = streamIndex.coerceAtLeast(0).toFloat() / last
        refresh(visible)
        track.revalidate()
        track.repaint()
    }

    private fun totalCount(): Int =
        thread.streamIds.size.coerceAtLeast(thread.posts.size).coerceAtLeast(1)

    private fun displayTotal(): Int =
        thread.topic.postsCount
            .coerceAtLeast(thread.streamIds.size)
            .coerceAtLeast(thread.posts.size)
            .coerceAtLeast(1)

    private fun preview(streamIndex: Int) {
        val id = thread.streamIds.getOrNull(streamIndex)
        val post = thread.posts.firstOrNull { it.id == id }
        val total = displayTotal()
        val number = (post?.postNumber ?: (streamIndex + 1)).coerceIn(1, total)
        position.text = IntelliDoStrings.message("topic.timeline.position", locale, number, total)
        ago.text = RelativeTime.format(post?.createdAt ?: interpolated(streamIndex), locale = locale)
    }

    private fun interpolated(streamIndex: Int): Instant {
        val last = (totalCount() - 1).coerceAtLeast(1)
        val ratio = streamIndex.coerceAtLeast(0).toFloat() / last
        val startMs = oldestAt.toEpochMilli()
        val endMs = newestAt(thread).toEpochMilli()
        return Instant.ofEpochMilli(startMs + ((endMs - startMs) * ratio).toLong())
    }

    private fun refresh(post: TopicPost) {
        val total = displayTotal()
        val number = post.postNumber.coerceIn(1, total)
        position.text = IntelliDoStrings.message("topic.timeline.position", locale, number, total)
        ago.text = RelativeTime.format(post.createdAt, locale = locale)
    }

    private inner class Track : JComponent() {
        var ratio: Float = 0f
        private var grab: Int = JBUI.scale(HANDLE) / 2
        private val handle = JBPanel<JBPanel<*>>()

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(JBUI.scale(WIDTH - 16), JBUI.scale(TRACK))
            handle.layout = BoxLayout(handle, BoxLayout.Y_AXIS)
            handle.isOpaque = false
            handle.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            position.alignmentX = LEFT_ALIGNMENT
            ago.alignmentX = LEFT_ALIGNMENT
            handle.add(position)
            handle.add(ago)
            layout = null
            add(handle)
            val mouse = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    dragging = true
                    val y = pointerY(e)
                    val handleH = JBUI.scale(HANDLE)
                    val top = handleY(ratio)
                    grab = if (y in top until top + handleH) y - top else handleH / 2
                    seek(y, load = false)
                }

                override fun mouseDragged(e: MouseEvent) {
                    dragging = true
                    seek(pointerY(e), load = false)
                }

                override fun mouseReleased(e: MouseEvent) {
                    seek(pointerY(e), load = true)
                    dragging = false
                }
            }
            listen(this, mouse)
        }

        private fun listen(component: JComponent, mouse: MouseAdapter) {
            component.addMouseListener(mouse)
            component.addMouseMotionListener(mouse)
            component.components.filterIsInstance<JComponent>().forEach { child ->
                listen(child, mouse)
            }
        }

        private fun pointerY(e: MouseEvent): Int =
            SwingUtilities.convertPoint(e.component, e.point, this).y

        private fun handleY(value: Float): Int {
            val handleH = JBUI.scale(HANDLE)
            val usable = (height - handleH).coerceAtLeast(0)
            return (usable * value.coerceIn(0f, 1f)).toInt()
        }

        private fun seek(y: Int, load: Boolean) {
            val handleH = JBUI.scale(HANDLE)
            val usable = (height - handleH).coerceAtLeast(1)
            ratio = ((y - grab).toFloat() / usable).coerceIn(0f, 1f)
            doLayout()
            repaint()
            val index = streamIndex(ratio, totalCount())
            preview(index)
            onJump(index, load)
        }

        override fun doLayout() {
            if (componentCount == 0) {
                return
            }
            val handleH = JBUI.scale(HANDLE)
            val y = handleY(ratio).coerceAtLeast(0)
            handle.setBounds(JBUI.scale(10), y, (width - JBUI.scale(10)).coerceAtLeast(1), handleH)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val x = JBUI.scale(4)
            val lineW = JBUI.scale(3)
            g2.color = JBColor.border()
            g2.fillRoundRect(x, 0, lineW, height, lineW, lineW)
            val handleH = JBUI.scale(HANDLE)
            val y = handleY(ratio)
            g2.color = GuestUi.signal
            g2.fillRoundRect(x, y, lineW, handleH, lineW, lineW)
        }
    }

    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(WIDTH), JBUI.scale(360))

    override fun getMinimumSize(): Dimension = Dimension(JBUI.scale(WIDTH), JBUI.scale(240))

    override fun getMaximumSize(): Dimension = Dimension(JBUI.scale(WIDTH), Integer.MAX_VALUE)

    companion object {
        const val WIDTH: Int = 176
        const val TRACK: Int = 220
        const val HANDLE: Int = 48

        fun streamIndex(ratio: Float, total: Int): Int {
            val last = (total - 1).coerceAtLeast(0)
            return (ratio.coerceIn(0f, 1f) * last).toInt().coerceIn(0, last)
        }

        fun newestAt(thread: TopicThread): Instant {
            val fromPosts = thread.posts.maxOfOrNull { it.createdAt }
            return listOfNotNull(thread.topic.lastPostedAt, fromPosts).maxOrNull() ?: Instant.EPOCH
        }
    }
}

package moe.momokko.intellido.ui.topic

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.transport.DiscourseJsonMapper
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.BoxLayout

class ReplyListPane(
    private val post: TopicPost,
    private val locale: Locale,
    private val onJump: (Int) -> Unit,
    private val onLoad: (Long, (List<TopicPost>) -> Unit) -> Unit,
) : JBPanel<ReplyListPane>() {
    private var expanded: Boolean = false
    private var loaded: Boolean = false
    private val list = JBPanel<JBPanel<*>>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        val toggle = JBLabel(IntelliDoStrings.message("topic.replies", locale, post.replyCount))
        toggle.foreground = GuestUi.signal
        toggle.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toggle.font = GuestUi.metaFont(toggle.font)
        toggle.border = JBUI.Borders.empty(4, 0, 8, 12)
        toggle.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) {
                    this@ReplyListPane.toggle()
                }
            }
        })
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        row.add(toggle)
        add(row)
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        list.isOpaque = false
        list.alignmentX = LEFT_ALIGNMENT
        list.isVisible = false
        add(list)
    }

    private fun toggle() {
        if (expanded) {
            expanded = false
            list.isVisible = false
            revalidate()
            return
        }
        expanded = true
        list.isVisible = true
        if (!loaded) {
            loaded = true
            val loading = JBLabel(IntelliDoStrings.message("topic.loading", locale))
            loading.foreground = GuestUi.muted
            loading.alignmentX = LEFT_ALIGNMENT
            list.add(loading)
            onLoad(post.id) { replies ->
                list.removeAll()
                if (replies.isEmpty()) {
                    val empty = JBLabel(IntelliDoStrings.message("topic.replies.empty", locale))
                    empty.foreground = GuestUi.muted
                    empty.alignmentX = LEFT_ALIGNMENT
                    list.add(empty)
                } else {
                    replies.forEach { reply ->
                        list.add(row(reply))
                    }
                }
                revalidate()
                repaint()
            }
        }
        revalidate()
    }

    private fun row(reply: TopicPost): JBPanel<*> {
        val text = DiscourseJsonMapper.stripTags(reply.cookedHtml.ifBlank { reply.plainText })
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        val label = JBLabel("▸ ${reply.username}  $text")
        label.foreground = GuestUi.muted
        label.font = GuestUi.metaFont(label.font)
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.border = JBUI.Borders.empty(2, 0)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onJump(reply.postNumber)
            }
        })
        val wrap = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
        wrap.isOpaque = false
        wrap.alignmentX = LEFT_ALIGNMENT
        wrap.add(label)
        return wrap
    }
}

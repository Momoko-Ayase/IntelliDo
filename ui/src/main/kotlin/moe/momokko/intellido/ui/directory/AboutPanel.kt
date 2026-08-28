package moe.momokko.intellido.ui.directory

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.catalog.CommunityAbout
import moe.momokko.intellido.domain.catalog.PublicMember
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.ui.guest.GuestAvatar
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingConstants

class AboutPanel(
    private val about: CommunityAbout,
    private val locale: Locale,
    private val onOpenUser: (String) -> Unit,
    private val onOpenUrl: (String) -> Unit,
) : JBPanel<AboutPanel>(BorderLayout()) {
    init {
        isOpaque = false
        border = JBUI.Borders.empty(16, 8, 24, 8)
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        val title = JBLabel(about.title)
        title.font = GuestUi.titleFont(title.font).deriveFont(Font.BOLD, 22f)
        title.alignmentX = LEFT_ALIGNMENT
        column.add(title)
        if (about.description.isNotBlank()) {
            val description = JBLabel("<html><body style='width:520px'>${about.description}</body></html>")
            description.foreground = GuestUi.muted
            description.alignmentX = LEFT_ALIGNMENT
            description.border = JBUI.Borders.empty(8, 0, 16, 0)
            column.add(description)
        }
        column.add(stats())
        if (about.admins.isNotEmpty()) {
            column.add(people(IntelliDoStrings.message("directory.about.admins", locale), about.admins))
        }
        if (about.moderators.isNotEmpty()) {
            column.add(people(IntelliDoStrings.message("directory.about.moderators", locale), about.moderators))
        }
        column.add(links())
        column.add(Box.createVerticalGlue())
        val scroll = JBScrollPane(column)
        scroll.border = JBUI.Borders.empty()
        add(scroll, BorderLayout.CENTER)
        getAccessibleContext().accessibleName = IntelliDoStrings.message("directory.about", locale)
    }

    private fun stats(): JComponent {
        val bar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(28), 8))
        bar.isOpaque = false
        bar.alignmentX = LEFT_ALIGNMENT
        bar.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(4, 0, 16, 0),
        )
        bar.add(stat(about.topicCount, "directory.about.topics"))
        bar.add(stat(about.postCount, "directory.about.posts"))
        bar.add(stat(about.userCount, "directory.about.users"))
        bar.add(stat(about.likeCount, "directory.about.likes"))
        return bar
    }

    private fun stat(value: Int, key: String): JComponent {
        val cell = JBPanel<JBPanel<*>>()
        cell.layout = BoxLayout(cell, BoxLayout.Y_AXIS)
        cell.isOpaque = false
        val number = JBLabel(DiscourseNumber.compact(value))
        number.font = number.font.deriveFont(Font.BOLD, 16f)
        number.alignmentX = LEFT_ALIGNMENT
        val caption = JBLabel(IntelliDoStrings.message(key, locale))
        caption.foreground = GuestUi.muted
        caption.font = GuestUi.metaFont(caption.font)
        caption.alignmentX = LEFT_ALIGNMENT
        cell.add(number)
        cell.add(caption)
        return cell
    }

    private fun people(heading: String, members: List<PublicMember>): JComponent {
        val wrap = JBPanel<JBPanel<*>>()
        wrap.layout = BoxLayout(wrap, BoxLayout.Y_AXIS)
        wrap.isOpaque = false
        wrap.alignmentX = LEFT_ALIGNMENT
        wrap.border = JBUI.Borders.empty(12, 0, 8, 0)
        val title = JBLabel(heading)
        title.font = title.font.deriveFont(Font.BOLD, 13f)
        title.alignmentX = LEFT_ALIGNMENT
        wrap.add(title)
        members.forEach { member ->
            wrap.add(person(member))
        }
        return wrap
    }

    private fun person(member: PublicMember): JComponent {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        row.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        row.add(GuestAvatar(member.username, null, 28, 0))
        val name = JBLabel(member.name?.takeIf { it.isNotBlank() } ?: member.username)
        row.add(name)
        val handle = JBLabel("@${member.username}")
        handle.foreground = GuestUi.muted
        handle.font = GuestUi.metaFont(handle.font)
        row.add(handle)
        row.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onOpenUser(member.username)
            }
        })
        row.getAccessibleContext().accessibleName = member.username
        return row
    }

    private fun links(): JComponent {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 12, 8))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        listOf(
            about.faqUrl to "directory.about.faq",
            about.guidelinesUrl to "directory.about.guidelines",
            about.tosUrl to "directory.about.tos",
            about.privacyUrl to "directory.about.privacy",
        ).forEach { (url, key) ->
            if (url.isNullOrBlank()) {
                return@forEach
            }
            val label = JBLabel(IntelliDoStrings.message(key, locale))
            label.foreground = GuestUi.signal
            label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            label.horizontalAlignment = SwingConstants.LEFT
            label.getAccessibleContext().accessibleName = label.text
            label.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    onOpenUrl(url)
                }
            })
            row.add(label)
        }
        return row
    }

    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(640), JBUI.scale(480))
}

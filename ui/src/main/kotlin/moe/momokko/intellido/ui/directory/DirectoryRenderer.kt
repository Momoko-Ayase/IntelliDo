package moe.momokko.intellido.ui.directory

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.ui.guest.GuestAvatar
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.util.Locale
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class DirectoryRenderer(
    private val locale: Locale,
    private val avatars: Map<String, ByteArray> = emptyMap(),
) : ListCellRenderer<DirectoryRow> {
    override fun getListCellRendererComponent(
        list: JList<out DirectoryRow>,
        value: DirectoryRow?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val row = value ?: return JBPanel<JBPanel<*>>()
        val fg = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else UIUtil.getLabelForeground()
        val muted = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else GuestUi.muted
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(10, 8, 10, 8),
        )
        panel.background = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else UIUtil.getListBackground()
        panel.isOpaque = true
        when (row) {
            is DirectoryRow.Topic -> {
                row.topic.categoryName?.let { name ->
                    panel.add(
                        GuestUi.categoryBadge(
                            name,
                            row.topic.categoryColor,
                            row.topic.categoryIcon,
                            restricted = row.topic.categoryRestricted,
                        ),
                        BorderLayout.WEST,
                    )
                }
                val title = JBLabel(row.topic.title)
                title.foreground = fg
                title.font = title.font.deriveFont(Font.PLAIN, 15f)
                panel.add(title, BorderLayout.CENTER)
            }
            is DirectoryRow.Category -> {
                panel.add(
                    GuestUi.categoryBadge(
                        row.category.listLabel(),
                        row.category.color,
                        row.category.icon,
                        restricted = row.category.readRestricted,
                    ),
                    BorderLayout.WEST,
                )
                panel.add(column(row.category.name, row.category.description, fg, muted), BorderLayout.CENTER)
                panel.add(count(row.category.topicCount, muted), BorderLayout.EAST)
            }
            is DirectoryRow.Tag -> {
                panel.add(GuestUi.tagBadge(row.tag.name), BorderLayout.WEST)
                panel.add(column(row.tag.name, row.tag.description, fg, muted), BorderLayout.CENTER)
                panel.add(count(row.tag.topicCount, muted), BorderLayout.EAST)
            }
            is DirectoryRow.Group -> {
                val icon = FaMark("users", muted, 14)
                icon.border = JBUI.Borders.empty(0, 0, 0, 8)
                panel.add(icon, BorderLayout.WEST)
                val subtitle = IntelliDoStrings.message(
                    "directory.group.members",
                    locale,
                    DiscourseNumber.compact(row.group.memberCount),
                )
                panel.add(
                    column(row.group.fullName ?: row.group.name, strip(row.group.bioHtml) ?: subtitle, fg, muted),
                    BorderLayout.CENTER,
                )
            }
            is DirectoryRow.Badge -> {
                val icon = FaMark(row.badge.icon?.takeIf { it.isNotBlank() } ?: "certificate", muted, 14)
                icon.border = JBUI.Borders.empty(0, 0, 0, 8)
                panel.add(icon, BorderLayout.WEST)
                val granted = IntelliDoStrings.message(
                    "directory.badge.granted",
                    locale,
                    DiscourseNumber.compact(row.badge.grantCount),
                )
                val type = row.badge.badgeType?.let { "$it · $granted" } ?: granted
                panel.add(
                    column(
                        row.badge.name,
                        listOfNotNull(row.badge.description.takeIf { it.isNotBlank() }, type).joinToString(" · "),
                        fg,
                        muted,
                    ),
                    BorderLayout.CENTER,
                )
            }
            is DirectoryRow.Member -> {
                val avatar = GuestAvatar(row.member.username, null, 32, 10)
                avatars[row.member.username]?.let { bytes -> avatar.apply(mapOf(row.member.username to bytes)) }
                panel.add(avatar, BorderLayout.WEST)
                val title = row.member.name?.takeIf { it.isNotBlank() } ?: row.member.username
                val handle = buildString {
                    append("@${row.member.username}")
                    row.member.title?.let { append(" · ").append(it) }
                    append(" · ").append(IntelliDoStrings.message("directory.members.trust", locale, row.member.trustLevel))
                }
                panel.add(column(title, handle, fg, muted), BorderLayout.CENTER)
            }
            is DirectoryRow.Message -> {
                val label = JBLabel(row.text)
                label.foreground = muted
                panel.add(label, BorderLayout.WEST)
            }
        }
        return panel
    }

    private fun column(title: String, subtitle: String?, fg: java.awt.Color, muted: java.awt.Color): JBPanel<*> {
        val cell = JBPanel<JBPanel<*>>()
        cell.layout = javax.swing.BoxLayout(cell, javax.swing.BoxLayout.Y_AXIS)
        cell.isOpaque = false
        cell.border = JBUI.Borders.empty(0, 8, 0, 8)
        val heading = JBLabel(title)
        heading.foreground = fg
        heading.font = heading.font.deriveFont(Font.PLAIN, 14f)
        heading.alignmentX = Component.LEFT_ALIGNMENT
        cell.add(heading)
        if (!subtitle.isNullOrBlank()) {
            val meta = JBLabel(subtitle)
            meta.foreground = muted
            meta.font = GuestUi.metaFont(meta.font)
            meta.alignmentX = Component.LEFT_ALIGNMENT
            cell.add(meta)
        }
        return cell
    }

    private fun count(value: Int, muted: java.awt.Color): JBLabel {
        val label = JBLabel(DiscourseNumber.compact(value), SwingConstants.RIGHT)
        label.foreground = muted
        label.font = GuestUi.metaFont(label.font)
        label.preferredSize = Dimension(JBUI.scale(48), JBUI.scale(20))
        return label
    }

    private fun strip(html: String?): String? =
        html?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

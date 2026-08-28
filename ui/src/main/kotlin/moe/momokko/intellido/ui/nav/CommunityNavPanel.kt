package moe.momokko.intellido.ui.nav

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.icon.FaGlyphs
import moe.momokko.intellido.domain.icon.LinuxDoSidebarCategories
import moe.momokko.intellido.domain.icon.LinuxDoTagIcons
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.nav.CommunityNavAction
import moe.momokko.intellido.platform.nav.CommunityNavEntry
import moe.momokko.intellido.platform.nav.CommunityNavModel
import moe.momokko.intellido.ui.guest.GuestUi
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class CommunityNavPanel(
    private val project: Project,
) : JBPanel<CommunityNavPanel>(BorderLayout()) {
    private val runtime = service<IntelliDoRuntime>()
    private val locale = runtime.locale
    private val body = JBPanel<JBPanel<*>>()
    private var categories: List<CommunityCategory> = LinuxDoSidebarCategories.GUEST
    private var tags: List<CommunityTag> = emptyList()
    private var selectedAction: CommunityNavAction? = CommunityNavAction.TOPICS
    private var selectedCategoryId: Long? = null
    private var selectedTag: String? = null
    private val expandedGroups: MutableSet<String> = mutableSetOf()

    init {
        isOpaque = true
        border = JBUI.Borders.empty(8, 6, 12, 6)
        body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
        body.isOpaque = false
        val scroll = JBScrollPane(
            body,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        )
        scroll.border = JBUI.Borders.empty()
        scroll.viewportBorder = JBUI.Borders.empty()
        add(scroll, BorderLayout.CENTER)
        seedExpanded(CommunityNavModel.guest(categories, tags))
        paintEntries()
        loadLiveCatalog()
    }

    /**
     * The seed list fixes which categories appear and in what order. The live
     * catalog overlays ids, colours, icons and topic counts onto the same
     * name/slug — never onto a stale seed id, which would swap icons and
     * click targets between rows.
     */
    private fun loadLiveCatalog() {
        ApplicationManager.getApplication().executeOnPooledThread {
            runtime.awaitCommunity()
            val liveCategories = runCatching { runtime.communityClient.loadCategories() }.getOrDefault(emptyList())
            val liveTags = runCatching { runtime.communityClient.loadTags() }.getOrDefault(emptyList())
            if (liveCategories.isEmpty() && liveTags.isEmpty()) {
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (liveCategories.isNotEmpty()) {
                    categories = liveCategories
                }
                if (liveTags.isNotEmpty()) {
                    tags = liveTags
                }
                paintEntries()
            }
        }
    }

    private fun seedExpanded(entries: List<CommunityNavEntry>) {
        entries.filterIsInstance<CommunityNavEntry.Group>().forEach { group ->
            if (group.expandedByDefault) {
                expandedGroups += group.titleKey
            }
            seedExpanded(group.children)
        }
    }

    private fun paintEntries() {
        body.removeAll()
        CommunityNavModel.guest(categories, tags).forEach { entry ->
            paintEntry(entry, indent = 0)
        }
        body.add(Box.createVerticalGlue())
        body.revalidate()
        body.repaint()
    }

    private fun paintEntry(entry: CommunityNavEntry, indent: Int) {
        when (entry) {
            is CommunityNavEntry.Header -> body.add(header(entry.titleKey))
            is CommunityNavEntry.Action -> body.add(actionRow(entry, indent))
            is CommunityNavEntry.Category -> body.add(categoryRow(entry.category))
            is CommunityNavEntry.Tag -> body.add(tagRow(entry.tag))
            is CommunityNavEntry.Link -> body.add(linkRow(entry, indent))
            is CommunityNavEntry.Group -> {
                body.add(groupRow(entry))
                if (entry.titleKey in expandedGroups) {
                    entry.children.forEach { child -> paintEntry(child, indent + 12) }
                }
            }
        }
    }

    private fun header(titleKey: String): JComponent {
        val label = JBLabel("▾  " + IntelliDoStrings.message(titleKey, locale))
        label.foreground = GuestUi.muted
        label.font = GuestUi.metaFont(label.font)
        label.border = JBUI.Borders.empty(14, 8, 4, 8)
        label.alignmentX = LEFT_ALIGNMENT
        return label
    }

    private fun groupRow(entry: CommunityNavEntry.Group): JComponent {
        val expanded = entry.titleKey in expandedGroups
        return navRow(
            text = IntelliDoStrings.message(entry.titleKey, locale),
            selected = false,
            muted = false,
            mark = null,
            icon = entry.icon,
            trailing = if (expanded) "▾" else "▸",
        ) {
            if (expanded) {
                expandedGroups.remove(entry.titleKey)
            } else {
                expandedGroups.add(entry.titleKey)
            }
            paintEntries()
        }
    }

    private fun actionRow(entry: CommunityNavEntry.Action, indent: Int = 0): JComponent {
        val selected = !entry.needsSignIn && selectedAction == entry.action &&
            selectedCategoryId == null && selectedTag == null
        return navRow(
            text = IntelliDoStrings.message(entry.titleKey, locale),
            selected = selected,
            muted = entry.needsSignIn,
            mark = null,
            icon = entry.icon,
            indent = indent,
        ) {
            if (entry.needsSignIn) {
                Messages.showInfoMessage(
                    IntelliDoStrings.message("signIn.notWired", locale),
                    IntelliDoStrings.message("action.signIn", locale),
                )
                return@navRow
            }
            selectedAction = entry.action
            selectedCategoryId = null
            selectedTag = null
            when (entry.action) {
                CommunityNavAction.TOPICS -> IntelliDoWorkspace.openLatest(project)
                CommunityNavAction.ALL_CATEGORIES ->
                    IntelliDoWorkspace.openDirectory(project, DirectoryKind.CATEGORIES)
                CommunityNavAction.ALL_TAGS, CommunityNavAction.TAGS ->
                    IntelliDoWorkspace.openDirectory(project, DirectoryKind.TAGS)
                CommunityNavAction.GROUPS -> IntelliDoWorkspace.openDirectory(project, DirectoryKind.GROUPS)
                CommunityNavAction.BADGES -> IntelliDoWorkspace.openDirectory(project, DirectoryKind.BADGES)
                CommunityNavAction.MEMBERS -> IntelliDoWorkspace.openDirectory(project, DirectoryKind.MEMBERS)
                CommunityNavAction.ABOUT -> IntelliDoWorkspace.openDirectory(project, DirectoryKind.ABOUT)
                CommunityNavAction.MY_POSTS, CommunityNavAction.MY_MESSAGES -> Unit
            }
            paintEntries()
        }
    }

    private fun categoryRow(category: CommunityCategory): JComponent =
        navRow(
            text = category.name,
            selected = selectedCategoryId == category.id && selectedTag == null,
            muted = false,
            mark = parseColor(category.color),
            icon = category.icon,
            indent = if (category.parentId != null) 12 else 0,
        ) {
            selectedAction = null
            selectedCategoryId = category.id
            selectedTag = null
            IntelliDoWorkspace.openCategoryTopics(project, category.id)
            paintEntries()
        }

    private fun tagRow(tag: CommunityTag): JComponent =
        navRow(
            text = tag.name,
            selected = selectedTag == tag.name,
            muted = false,
            mark = null,
            icon = LinuxDoTagIcons.icon(tag.name) ?: "tag",
        ) {
            selectedAction = null
            selectedCategoryId = null
            selectedTag = tag.name
            IntelliDoWorkspace.openTagTopics(project, tag.name)
            paintEntries()
        }

    private fun linkRow(entry: CommunityNavEntry.Link, indent: Int = 0): JComponent =
        navRow(
            text = IntelliDoStrings.message(entry.titleKey, locale),
            selected = false,
            muted = false,
            mark = null,
            icon = entry.icon,
            indent = indent,
        ) {
            IntelliDoWorkspace.openFromUrl(project, entry.url)
        }

    private fun navRow(
        text: String,
        selected: Boolean,
        muted: Boolean,
        mark: Color?,
        icon: String? = null,
        indent: Int = 0,
        trailing: String? = null,
        onClick: () -> Unit,
    ): JComponent {
        val row = JBPanel<JBPanel<*>>(BorderLayout())
        row.alignmentX = LEFT_ALIGNMENT
        row.isOpaque = selected
        if (selected) {
            row.background = JBColor(0xE8E4DC, 0x3A3A3A)
        }
        row.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        row.border = CompoundBorder(
            MatteBorder(0, JBUI.scale(3), 0, 0, if (selected) GuestUi.signal else Color(0, 0, 0, 0)),
            EmptyBorder(JBUI.scale(5), JBUI.scale(8 + indent), JBUI.scale(5), JBUI.scale(8)),
        )
        val inner = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0))
        inner.isOpaque = false
        val fa = icon?.takeIf { FaGlyphs.get(it) != null }
        when {
            fa != null -> inner.add(FaMark(fa, mark ?: GuestUi.muted, 12))
            mark != null -> inner.add(ColorDot(mark))
        }
        val label = JBLabel(text, SwingConstants.LEFT)
        if (muted) {
            label.foreground = GuestUi.muted
        }
        inner.add(label)
        row.add(inner, BorderLayout.WEST)
        if (trailing != null) {
            val caret = JBLabel(trailing)
            caret.foreground = GuestUi.muted
            caret.border = JBUI.Borders.emptyRight(4)
            row.add(caret, BorderLayout.EAST)
        }
        row.maximumSize = Dimension(Integer.MAX_VALUE, JBUI.scale(32))
        row.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onClick()
            }
        })
        return row
    }

    private fun parseColor(hex: String?): Color? {
        val raw = hex?.trim()?.removePrefix("#") ?: return null
        if (raw.length != 6) {
            return null
        }
        return runCatching { Color(raw.toInt(16)) }.getOrNull()
    }

    private class ColorDot(private val fill: Color) : JComponent() {
        init {
            val size = JBUI.scale(10)
            preferredSize = Dimension(size, size)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = fill
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(3), JBUI.scale(3))
        }
    }
}

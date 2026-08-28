package moe.momokko.intellido.ui.home

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.TopicPoster
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.time.RelativeTime
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JViewport
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class HomeTopicRenderer(
    private val locale: Locale,
    private val avatars: Map<String, ByteArray> = emptyMap(),
) : ListCellRenderer<HomeTopic> {
    override fun getListCellRendererComponent(
        list: JList<out HomeTopic>,
        value: HomeTopic?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val topic = value ?: return JBPanel<JBPanel<*>>()
        val fg = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else UIUtil.getLabelForeground()
        val muted = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else GuestUi.muted
        val rowWidth = rowWidth(list)
        val textWidth = (rowWidth - statsWidth() - JBUI.scale(16))
            .coerceAtLeast(JBUI.scale(80))
        val row = JBPanel<JBPanel<*>>(GridBagLayout())
        row.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 8, 8, 4),
        )
        val stats = statsCell(topic, muted)
        val statsW = statsWidth()
        stats.preferredSize = Dimension(statsW, stats.preferredSize.height)
        stats.minimumSize = Dimension(statsW, 0)
        stats.maximumSize = Dimension(statsW, Integer.MAX_VALUE)
        val titleGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }
        val statsGbc = GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 0.0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.EAST
        }
        row.add(mainCell(topic, fg, textWidth), titleGbc)
        row.add(stats, statsGbc)
        row.background = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else UIUtil.getListBackground()
        row.isOpaque = true
        val height = row.preferredSize.height.coerceAtLeast(JBUI.scale(36))
        row.preferredSize = Dimension(rowWidth, height)
        row.minimumSize = Dimension(0, height)
        row.maximumSize = Dimension(Integer.MAX_VALUE, height)
        // IntelliJ's list UI often paints renderers without validate(); titles still
        // appear because they have an explicit size, but the stats column would stay
        // at 0×0. Lay the row out to the cell width before returning it.
        row.setSize(rowWidth, height)
        layoutTree(row)
        return row
    }

    fun renderTopic(topic: HomeTopic, selected: Boolean, cellWidth: Int): JComponent {
        val fg = if (selected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
        val cell = mainCell(topic, fg, cellWidth.coerceAtLeast(JBUI.scale(80)))
        cell.background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        cell.isOpaque = true
        cell.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 8, 8, 4),
        )
        cell.setSize(cellWidth.coerceAtLeast(JBUI.scale(80)), cell.preferredSize.height.coerceAtLeast(JBUI.scale(36)))
        layoutTree(cell)
        return cell
    }

    fun renderStats(topic: HomeTopic, selected: Boolean): JComponent {
        val muted = if (selected) UIUtil.getListSelectionForeground(true) else GuestUi.muted
        val stats = statsCell(topic, muted)
        stats.background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        stats.isOpaque = true
        stats.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 4, 8, 4),
        )
        val w = statsWidth()
        val h = stats.preferredSize.height.coerceAtLeast(JBUI.scale(24))
        stats.preferredSize = Dimension(w, h)
        stats.minimumSize = Dimension(w, 0)
        stats.maximumSize = Dimension(w, Integer.MAX_VALUE)
        stats.setSize(w, h)
        layoutTree(stats)
        return stats
    }

    private fun mainCell(topic: HomeTopic, fg: Color, textWidth: Int): JBPanel<*> {
        val pinW = if (topic.pinned) JBUI.scale(PIN_COL) else 0
        val titleW = (textWidth - pinW).coerceAtLeast(JBUI.scale(80))
        val title = JBLabel(topic.title)
        title.foreground = fg
        title.font = title.font.deriveFont(Font.PLAIN, 16f)
        title.setAllowAutoWrapping(true)
        title.size = Dimension(titleW, 1)
        val titleHeight = title.preferredSize.height.coerceAtLeast(title.getFontMetrics(title.font).height)
        title.preferredSize = Dimension(titleW, titleHeight)
        title.maximumSize = Dimension(titleW, Integer.MAX_VALUE)
        title.minimumSize = Dimension(0, titleHeight)

        val heading = JBPanel<JBPanel<*>>(BorderLayout())
        heading.isOpaque = false
        heading.alignmentX = Component.LEFT_ALIGNMENT
        if (topic.pinned) {
            val fm = title.getFontMetrics(title.font)
            val pinSize = JBUI.scale(12)
            val top = (title.insets.top + ((fm.ascent - pinSize) / 2)).coerceAtLeast(0)
            heading.add(PinColumn(true, top), BorderLayout.WEST)
        }
        heading.add(title, BorderLayout.CENTER)

        val meta = WrapBar()
        meta.isOpaque = false
        meta.alignmentX = Component.LEFT_ALIGNMENT
        meta.maximumSize = Dimension(textWidth, Integer.MAX_VALUE)
        topic.categoryName?.let { name ->
            meta.add(GuestUi.categoryBadge(name, topic.categoryColor, topic.categoryIcon))
        }
        topic.tags.forEach { tag ->
            meta.add(GuestUi.tagBadge(tag))
        }
        if (topic.closed) {
            meta.add(FaMark("lock", GuestUi.muted, 11))
        }
        if (topic.archived) {
            meta.add(FaMark("box-archive", GuestUi.muted, 11))
        }
        if (topic.acceptedAnswer) {
            meta.add(FaMark("check", HomeTopicRenderer.HEAT, 11))
        }
        meta.size = Dimension(textWidth, 1)

        val cell = JBPanel<JBPanel<*>>()
        cell.layout = BoxLayout(cell, BoxLayout.Y_AXIS)
        cell.isOpaque = false
        cell.add(heading)
        cell.add(meta)
        return cell
    }

    private fun statsCell(topic: HomeTopic, muted: Color): JBPanel<*> {
        val stats = JBPanel<JBPanel<*>>(GridBagLayout())
        stats.isOpaque = false
        val gbc = GridBagConstraints()
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = Insets(0, JBUI.scale(4), 0, JBUI.scale(4))
        gbc.anchor = GridBagConstraints.CENTER
        gbc.gridx = 0
        stats.add(
            PosterStack(
                topic.posters.ifEmpty { listOf(TopicPoster(topic.authorUsername)) },
                avatars,
            ),
            gbc,
        )
        gbc.gridx = 1
        stats.add(stat(DiscourseNumber.compact(topic.replyCount), muted, DiscourseNumber.hotReplies(topic.replyCount)), gbc)
        gbc.gridx = 2
        stats.add(stat(DiscourseNumber.compact(topic.views), muted, DiscourseNumber.hotViews(topic.views)), gbc)
        gbc.gridx = 3
        stats.add(stat(RelativeTime.format(topic.lastPostedAt, locale = locale), muted, hot = false, activity = true), gbc)
        return stats
    }

    private fun stat(text: String, muted: Color, hot: Boolean, activity: Boolean = false): JBLabel {
        val label = JBLabel(text, SwingConstants.RIGHT)
        label.foreground = if (hot) HEAT else muted
        label.font = GuestUi.metaFont(label.font)
        val width = JBUI.scale(if (activity) ACTIVITY_WIDTH else STAT_WIDTH)
        label.preferredSize = Dimension(width, JBUI.scale(24))
        label.minimumSize = label.preferredSize
        label.maximumSize = label.preferredSize
        return label
    }

    companion object {
        const val POSTERS_WIDTH: Int = 154
        const val STAT_WIDTH: Int = 68
        const val ACTIVITY_WIDTH: Int = 72
        const val PIN_COL: Int = 18
        val HEAT: JBColor = JBColor(0xFE7A15, 0xFF9A40)

        fun statsWidth(): Int = JBUI.scale(POSTERS_WIDTH + STAT_WIDTH + STAT_WIDTH + ACTIVITY_WIDTH + 32)

        fun rowWidth(list: JList<*>): Int {
            val parent = list.parent
            val width = if (parent is JViewport && parent.extentSize.width > 0) {
                parent.extentSize.width
            } else {
                list.width
            }
            return width.coerceAtLeast(JBUI.scale(320))
        }

        fun header(locale: Locale, center: JComponent? = null): JBPanel<*> {
            val row = ViewportTrackingPanel()
            row.isOpaque = false
            row.border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 8, 8, 4),
            )
            val topic = JBLabel(IntelliDoStrings.message("home.column.topic", locale))
            topic.foreground = GuestUi.muted
            topic.font = GuestUi.metaFont(topic.font)
            val titleGbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            }
            if (center != null) {
                val wrap = JBPanel<JBPanel<*>>(BorderLayout())
                wrap.isOpaque = false
                wrap.add(topic, BorderLayout.WEST)
                val centerWrap = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, 0, 0))
                centerWrap.isOpaque = false
                centerWrap.add(center)
                wrap.add(centerWrap, BorderLayout.CENTER)
                row.add(wrap, titleGbc)
            } else {
                row.add(topic, titleGbc)
            }
            val stats = JBPanel<JBPanel<*>>(GridBagLayout())
            stats.isOpaque = false
            val gbc = GridBagConstraints()
            gbc.gridy = 0
            gbc.insets = Insets(0, JBUI.scale(4), 0, JBUI.scale(4))
            gbc.anchor = GridBagConstraints.CENTER
            gbc.gridx = 0
            stats.add(headerGap(JBUI.scale(POSTERS_WIDTH)), gbc)
            gbc.gridx = 1
            stats.add(headerLabel("home.column.replies", locale), gbc)
            gbc.gridx = 2
            stats.add(headerLabel("home.column.views", locale), gbc)
            gbc.gridx = 3
            stats.add(headerLabel("home.column.activity", locale, activity = true), gbc)
            val statsW = statsWidth()
            stats.preferredSize = Dimension(statsW, JBUI.scale(16))
            stats.minimumSize = Dimension(statsW, JBUI.scale(16))
            stats.maximumSize = Dimension(statsW, Integer.MAX_VALUE)
            val statsGbc = GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                weightx = 0.0
                fill = GridBagConstraints.NONE
                anchor = GridBagConstraints.EAST
            }
            row.add(stats, statsGbc)
            return row
        }

        internal fun layoutTree(component: Component) {
            if (component is Container) {
                component.doLayout()
                component.components.forEach { layoutTree(it) }
            }
        }

        private fun headerLabel(key: String, locale: Locale, activity: Boolean = false): JBLabel {
            val label = JBLabel(IntelliDoStrings.message(key, locale), SwingConstants.RIGHT)
            label.foreground = GuestUi.muted
            label.font = GuestUi.metaFont(label.font)
            val width = JBUI.scale(if (activity) ACTIVITY_WIDTH else STAT_WIDTH)
            label.preferredSize = Dimension(width, JBUI.scale(16))
            return label
        }

        private fun headerGap(width: Int): Component = Box.createRigidArea(Dimension(width, 1))
    }

    /**
     * Column-header view of a JScrollPane is not stretched to the viewport unless
     * it is Scrollable and tracks the viewport width. Without that the stats labels
     * sit in a 429px preferred-size strip and get clipped or packed off-screen.
     */
    private class ViewportTrackingPanel : JBPanel<ViewportTrackingPanel>(GridBagLayout()), javax.swing.Scrollable {
        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = true

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int): Int =
            JBUI.scale(16)

        override fun getScrollableBlockIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int): Int =
            JBUI.scale(16)

        override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
            super.setBounds(x, y, w, h)
            HomeTopicRenderer.layoutTree(this)
        }
    }

    private class PinColumn(
        pinned: Boolean,
        private val topInset: Int,
    ) : JBPanel<PinColumn>(null) {
        init {
            isOpaque = false
            val width = JBUI.scale(PIN_COL)
            preferredSize = Dimension(width, JBUI.scale(16))
            minimumSize = Dimension(width, 0)
            maximumSize = Dimension(width, Integer.MAX_VALUE)
            if (pinned) {
                add(FaMark("thumbtack", GuestUi.muted, 12))
            }
        }

        override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(PIN_COL), JBUI.scale(16))

        override fun getMinimumSize(): Dimension = Dimension(JBUI.scale(PIN_COL), 0)

        override fun getMaximumSize(): Dimension = Dimension(JBUI.scale(PIN_COL), Integer.MAX_VALUE)

        override fun doLayout() {
            if (componentCount == 0) {
                return
            }
            val pin = getComponent(0)
            val size = JBUI.scale(12)
            pin.setBounds(0, topInset, size, size)
        }
    }

    private class WrapBar : JBPanel<WrapBar>(null) {
        private val hgap: Int = JBUI.scale(8)

        override fun doLayout() {
            place(width.coerceAtLeast(JBUI.scale(80))) { child, x, y, w, h ->
                child.setBounds(x, y, w, h)
            }
        }

        override fun getPreferredSize(): Dimension {
            val target = when {
                width > 0 -> width
                maximumSize.width in 1 until Integer.MAX_VALUE -> maximumSize.width
                else -> JBUI.scale(200)
            }
            var bottom = 0
            place(target) { _, _, y, _, h -> bottom = maxOf(bottom, y + h) }
            return Dimension(target, bottom + insets.bottom)
        }

        private fun place(
            target: Int,
            visit: (Component, Int, Int, Int, Int) -> Unit,
        ) {
            val ins = insets
            val maxW = (target - ins.left - ins.right).coerceAtLeast(JBUI.scale(40))
            var x = ins.left
            var y = ins.top
            var rowH = 0
            components.forEach { child ->
                val d = child.preferredSize
                if (x > ins.left && x + d.width - ins.left > maxW) {
                    y += rowH + 2
                    x = ins.left
                    rowH = 0
                }
                visit(child, x, y, d.width, d.height)
                x += d.width + hgap
                rowH = maxOf(rowH, d.height)
            }
        }
    }

    private class PosterStack(
        posters: List<TopicPoster>,
        private val avatars: Map<String, ByteArray>,
    ) : JBPanel<PosterStack>(null) {
        private val shown: List<TopicPoster> = posters.take(MAX)
        private val portraits: Map<String, java.awt.Image> = shown.mapNotNull { poster ->
            val bytes = avatars[poster.username] ?: return@mapNotNull null
            poster.username to javax.swing.ImageIcon(bytes).image
        }.toMap()

        init {
            isOpaque = false
            val size = JBUI.scale(AVATAR)
            preferredSize = Dimension(JBUI.scale(POSTERS_WIDTH), size)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val size = JBUI.scale(AVATAR)
            val step = JBUI.scale(STEP)
            val used = if (shown.isEmpty()) 0 else size + (shown.size - 1) * step
            val origin = (width - used).coerceAtLeast(0)
            shown.forEachIndexed { index, poster ->
                val x = origin + index * step
                val portrait = portraits[poster.username]
                if (portrait != null) {
                    val clip = g2.clip
                    g2.clip = java.awt.geom.Ellipse2D.Float(x.toFloat(), 0f, size.toFloat(), size.toFloat())
                    g2.drawImage(portrait, x, 0, size, size, null)
                    g2.clip = clip
                } else {
                    g2.color = GuestUi.avatarFill(poster.username)
                    g2.fillOval(x, 0, size, size)
                    g2.color = Color.WHITE
                    val letter = poster.username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    g2.font = font.deriveFont(Font.BOLD, JBUI.scale(11).toFloat())
                    val fm = g2.fontMetrics
                    g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2, (size - fm.height) / 2 + fm.ascent)
                }
            }
        }

        companion object {
            const val AVATAR: Int = 24
            const val STEP: Int = 16
            const val MAX: Int = 5
        }
    }
}

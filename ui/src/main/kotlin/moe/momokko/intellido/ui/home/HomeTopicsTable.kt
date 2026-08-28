package moe.momokko.intellido.ui.home

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import javax.swing.JTable
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * Home topic list as a two-column table. JList + WideSelectionListUI sizes cells
 * to a cached preferred width, so the stats column never made it into the clip.
 * A table column with a fixed width is laid out against the viewport instead.
 */
class HomeTopicsTable(
    private val locale: Locale,
    avatars: Map<String, ByteArray>,
) : JTable() {
    private val topics = mutableListOf<HomeTopic>()
    private val renderer = HomeTopicRenderer(locale, avatars)
    private val topicsModel = Model()

    init {
        model = topicsModel
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowSelectionAllowed = true
        columnSelectionAllowed = false
        tableHeader.reorderingAllowed = false
        tableHeader.resizingAllowed = false
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        fillsViewportHeight = true
        runCatching {
            accessibleContext.accessibleName = IntelliDoStrings.message("home.list.accessibleName", locale)
        }
        val statsW = HomeTopicRenderer.statsWidth()
        columnModel.getColumn(0).cellRenderer = TopicRenderer()
        columnModel.getColumn(1).cellRenderer = StatsRenderer()
        columnModel.getColumn(1).minWidth = statsW
        columnModel.getColumn(1).maxWidth = statsW
        columnModel.getColumn(1).preferredWidth = statsW
        columnModel.getColumn(0).headerRenderer = HeaderRenderer(IntelliDoStrings.message("home.column.topic", locale), left = true)
        columnModel.getColumn(1).headerRenderer = StatsHeaderRenderer()
        rowHeight = JBUI.scale(54)
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(event: java.awt.event.ComponentEvent) {
                pinStatsColumn()
            }
        })
        pinStatsColumn()
    }

    internal fun pinStatsColumn() {
        if (columnCount < 2) {
            return
        }
        val statsW = HomeTopicRenderer.statsWidth()
        val stats = columnModel.getColumn(1)
        stats.minWidth = statsW
        stats.maxWidth = statsW
        stats.preferredWidth = statsW
        stats.width = statsW
        val available = (parent as? javax.swing.JViewport)?.extentSize?.width
            ?.takeIf { it > 0 }
            ?: width
        val rest = (available - statsW).coerceAtLeast(JBUI.scale(80))
        columnModel.getColumn(0).minWidth = JBUI.scale(80)
        columnModel.getColumn(0).maxWidth = Integer.MAX_VALUE
        columnModel.getColumn(0).preferredWidth = rest
        columnModel.getColumn(0).width = rest
    }

    fun titles(): List<String> = topics.map { it.title }

    fun topicCount(): Int = topics.size

    fun hasTopics(): Boolean = topics.isNotEmpty()

    fun at(index: Int): HomeTopic = topics[index]

    fun replace(next: List<HomeTopic>) {
        topics.clear()
        topics.addAll(next)
        topicsModel.fireTableDataChanged()
        pinStatsColumn()
        refreshRowHeights()
    }

    fun append(extra: List<HomeTopic>) {
        if (extra.isEmpty()) {
            return
        }
        val start = topics.size
        topics.addAll(extra)
        topicsModel.fireTableRowsInserted(start, topics.size - 1)
        refreshRowHeights()
    }

    fun showLoading() = Unit

    fun showFailed() = Unit

    fun selectedIndex(): Int = selectedRow

    fun indexAt(point: java.awt.Point): Int = rowAtPoint(point)

    private fun refreshRowHeights() {
        for (row in 0 until rowCount) {
            val topic = columnModel.getColumn(0).cellRenderer
                .getTableCellRendererComponent(this, topics[row], false, false, row, 0)
            setRowHeight(row, topic.preferredSize.height.coerceAtLeast(JBUI.scale(36)))
        }
    }

    private inner class Model : AbstractTableModel() {
        override fun getRowCount(): Int = topics.size

        override fun getColumnCount(): Int = 2

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = topics[rowIndex]

        override fun getColumnClass(columnIndex: Int): Class<*> = HomeTopic::class.java
    }

    private inner class TopicRenderer : TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val topic = value as? HomeTopic ?: return javax.swing.JPanel()
            val width = table.columnModel.getColumn(0).width
            return renderer.renderTopic(topic, isSelected, width)
        }
    }

    private inner class StatsRenderer : TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val topic = value as? HomeTopic ?: return javax.swing.JPanel()
            return renderer.renderStats(topic, isSelected)
        }
    }

    private inner class HeaderRenderer(
        private val text: String,
        private val left: Boolean,
    ) : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val label = super.getTableCellRendererComponent(table, text, false, false, row, column) as JComponent
            foreground = GuestUi.muted
            font = GuestUi.metaFont(font)
            horizontalAlignment = if (left) LEFT else RIGHT
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 8, 8, 4),
            )
            background = table.tableHeader.background
            return label
        }
    }

    private inner class StatsHeaderRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val replies = IntelliDoStrings.message("home.column.replies", locale)
            val views = IntelliDoStrings.message("home.column.views", locale)
            val activity = IntelliDoStrings.message("home.column.activity", locale)
            val label = super.getTableCellRendererComponent(
                table,
                "$replies    $views    $activity",
                false,
                false,
                row,
                column,
            ) as JComponent
            foreground = GuestUi.muted
            font = GuestUi.metaFont(font)
            horizontalAlignment = RIGHT
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(com.intellij.ui.JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(8, 8, 8, 4),
            )
            background = table.tableHeader.background
            return label
        }
    }

    companion object {
        fun wrap(table: HomeTopicsTable): JBScrollPane {
            val scroll = JBScrollPane(
                table,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            )
            scroll.border = JBUI.Borders.empty()
            scroll.minimumSize = Dimension(JBUI.scale(240), JBUI.scale(120))
            scroll.viewport.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(event: java.awt.event.ComponentEvent) {
                    table.pinStatsColumn()
                }
            })
            return scroll
        }
    }
}

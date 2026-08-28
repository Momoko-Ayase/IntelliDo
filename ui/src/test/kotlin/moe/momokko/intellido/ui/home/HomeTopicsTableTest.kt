package moe.momokko.intellido.ui.home

import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class HomeTopicsTableTest {
    @Test
    fun `stats column keeps a fixed width on the right`() {
        val table = HomeTopicsTable(Locale.SIMPLIFIED_CHINESE, emptyMap())
        val scroll = HomeTopicsTable.wrap(table)
        scroll.setSize(900, 400)
        scroll.doLayout()
        scroll.viewport.doLayout()
        table.replace(FakeLinuxDoCommunityClient.defaultTopics)
        table.setSize(scroll.viewport.extentSize.width.coerceAtLeast(1), 400)
        table.pinStatsColumn()
        table.doLayout()
        val statsW = table.columnModel.getColumn(1).width
        val topicW = table.columnModel.getColumn(0).width
        assertTrue(statsW >= HomeTopicRenderer.statsWidth() - 1, "statsW=$statsW")
        assertTrue(topicW + statsW <= 900 + 2, "topicW=$topicW statsW=$statsW")
        assertTrue(statsW > 100, "stats column collapsed: statsW=$statsW")
    }
}

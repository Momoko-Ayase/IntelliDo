package moe.momokko.intellido.platform.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class DiscourseBadgeLabelsTest {
    private val zh = Locale.SIMPLIFIED_CHINESE

    @Test
    fun `english system badge names become LINUX DO chinese`() {
        assertEquals("领导者", DiscourseBadgeLabels.name(4, "Leader", zh))
        assertEquals("热门链接", DiscourseBadgeLabels.name(28, "Popular Link", zh))
        assertEquals("解决方案机构", DiscourseBadgeLabels.name(112, "Solution Institution", zh))
        assertEquals("龙行龘龘", DiscourseBadgeLabels.name(103, "龙行龘龘", zh))
        assertEquals("Leader", DiscourseBadgeLabels.name(4, "Leader", Locale.ENGLISH))
    }

    @Test
    fun `english system badge descriptions keep interpolated counts`() {
        assertEquals(
            "授予全局编辑、置顶、关闭、归档、拆分与合并、更多赞",
            DiscourseBadgeLabels.description("Granted global edit, pin, close, archive, split and merge, more likes", zh),
        )
        assertEquals(
            "分享的链接被点击了 50 次",
            DiscourseBadgeLabels.description("Posted an external link with 50 clicks", zh),
        )
        assertEquals(
            "有 150 条回复被标记为解决方案",
            DiscourseBadgeLabels.description("Have 150 replies marked as Solutions", zh),
        )
        assertEquals("龙行龘龘，前程朤朤。", DiscourseBadgeLabels.description("龙行龘龘，前程朤朤。", zh))
    }
}

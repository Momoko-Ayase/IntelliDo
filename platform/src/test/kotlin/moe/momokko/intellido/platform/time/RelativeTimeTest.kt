package moe.momokko.intellido.platform.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

class RelativeTimeTest {
    private val now = Instant.parse("2026-08-24T12:00:00Z")
    private val zh = Locale.SIMPLIFIED_CHINESE

    @Test
    fun `formats recent activity the way Discourse lists do`() {
        assertEquals("刚刚", RelativeTime.format(now.minusSeconds(10), now, zh))
        assertEquals("5分钟", RelativeTime.format(now.minusSeconds(5 * 60), now, zh))
        assertEquals("3小时", RelativeTime.format(now.minusSeconds(3 * 3600), now, zh))
        assertEquals("昨天", RelativeTime.format(now.minusSeconds(26 * 3600), now, zh))
        assertEquals("2025年8月7日", RelativeTime.format(Instant.parse("2025-08-07T00:00:00Z"), now, zh))
    }

    @Test
    fun `duration tiny matches LINUX DO profile stats`() {
        assertEquals("4 个月", RelativeTime.durationTiny(11_099_902, zh))
        assertEquals("5 天", RelativeTime.durationTiny(428_119, zh))
        assertEquals("1 小时", RelativeTime.durationTiny(3600, zh))
        assertEquals("1 分钟", RelativeTime.durationTiny(60, zh))
        assertEquals("刚刚", RelativeTime.durationTiny(10, zh))
        assertEquals("1 年", RelativeTime.durationTiny(365 * 86400, zh))
        assertEquals("4mo", RelativeTime.durationTiny(11_099_902, Locale.ENGLISH))
    }
}

package moe.momokko.intellido.platform.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscourseNumberTest {
    @Test
    fun `compacts the way LINUX DO topic lists do`() {
        assertEquals("0", DiscourseNumber.compact(0))
        assertEquals("4", DiscourseNumber.compact(4))
        assertEquals("777", DiscourseNumber.compact(777))
        assertEquals("1.0k", DiscourseNumber.compact(1_000))
        assertEquals("5.0k", DiscourseNumber.compact(5_033))
        assertEquals("6.0k", DiscourseNumber.compact(6_000))
        assertEquals("7.7k", DiscourseNumber.compact(7_687))
        assertEquals("29.7k", DiscourseNumber.compact(29_700))
        assertEquals("369k", DiscourseNumber.compact(369_000))
        assertEquals("488k", DiscourseNumber.compact(488_000))
        assertEquals("602k", DiscourseNumber.compact(602_863))
        assertEquals("1.6M", DiscourseNumber.compact(1_607_233))
        assertEquals("21.0k", DiscourseNumber.compact(21_018))
    }

    @Test
    fun `heatmap matches LINUX DO orange stats`() {
        assertTrue(DiscourseNumber.hotReplies(240))
        assertTrue(DiscourseNumber.hotReplies(777))
        assertFalse(DiscourseNumber.hotReplies(4))
        assertTrue(DiscourseNumber.hotViews(6_000))
        assertTrue(DiscourseNumber.hotViews(29_700))
        assertFalse(DiscourseNumber.hotViews(113))
        assertFalse(DiscourseNumber.hotViews(85))
    }
}

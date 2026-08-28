package moe.momokko.intellido.domain.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoostContentTest {
    @Test
    fun `plain paragraph becomes a single line of text`() {
        val parsed = BoostContent.parse("<p>前排合影</p>")
        assertEquals("前排合影", parsed.displayText)
        assertEquals("前排合影", parsed.groupingKey)
    }

    @Test
    fun `emoji images become shortcodes and stay grouped together`() {
        val cooked =
            """<p>我也只能哭死 <img src="/images/emoji/twemoji/rofl.png?v=15" title=":rofl:" class="emoji" alt=":rofl:"></p>"""
        val parsed = BoostContent.parse(cooked)
        assertEquals("我也只能哭死 :rofl:", parsed.displayText)
        assertEquals("我也只能哭死 :rofl:", parsed.groupingKey)
    }

    @Test
    fun `adjacent emoji images keep their shortcodes without extra spaces`() {
        val cooked =
            """<p><img class="emoji" title="smile" src="/images/emoji/twitter/smile.png?v=12"><img class="emoji" title="heart" src="/images/emoji/twitter/heart.png?v=12"></p>"""
        assertEquals(":smile::heart:", BoostContent.parse(cooked).displayText)
    }

    @Test
    fun `groups identical cooked content in first-seen order`() {
        val boosts = listOf(
            boost(1, "<p>前排合影</p>", "reader"),
            boost(2, "<p>哦？</p>", "helper"),
            boost(3, "<p>前排合影</p>", "guest"),
            boost(4, "<p>前排合影</p>", "system"),
        )
        val groups = BoostContent.groups(boosts)
        assertEquals(2, groups.size)
        assertEquals("前排合影", groups[0].displayText)
        assertEquals(3, groups[0].count)
        assertEquals(listOf("reader", "guest", "system"), groups[0].boosts.map { it.username })
        assertEquals("哦？", groups[1].displayText)
        assertEquals(1, groups[1].count)
    }

    @Test
    fun `empty cooked still yields a grouping key from the raw html`() {
        val parsed = BoostContent.parse("   ")
        assertEquals("", parsed.displayText)
        assertEquals("", parsed.groupingKey)
    }

    private fun boost(id: Long, cooked: String, username: String): PostBoost =
        PostBoost(id = id, cookedHtml = cooked, username = username)
}

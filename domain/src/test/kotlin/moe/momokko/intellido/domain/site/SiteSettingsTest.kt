package moe.momokko.intellido.domain.site

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SiteSettingsTest {
    @Test
    fun `known linux do long polling hosts are honoured`() {
        assertEquals("https://ping.ldstatic.com", SiteSettings("https://ping.ldstatic.com/").messageBusOrigin)
        assertEquals("https://linux.do", SiteSettings("https://linux.do").messageBusOrigin)
        assertTrue(SiteSettings.isTrustedOrigin("https://cdn.ldstatic.com"))
    }

    @Test
    fun `an untrusted or plaintext origin falls back to the default`() {
        listOf(
            "http://ping.ldstatic.com",
            "https://evil.example",
            "https://ldstatic.com.evil.example",
            "https://user@ping.ldstatic.com",
            "not a url",
            "",
        ).forEach { candidate ->
            assertEquals(
                SiteSettings.DEFAULT_LONG_POLLING,
                SiteSettings(candidate).messageBusOrigin,
                "candidate=$candidate",
            )
            assertFalse(SiteSettings.isTrustedOrigin(candidate), "candidate=$candidate")
        }
    }

    @Test
    fun `a quote bearing origin never reaches the poll url`() {
        val hostile = "https://ping.ldstatic.com'+alert(1)+'"
        assertEquals(SiteSettings.DEFAULT_LONG_POLLING, SiteSettings(hostile).messageBusOrigin)
    }
}

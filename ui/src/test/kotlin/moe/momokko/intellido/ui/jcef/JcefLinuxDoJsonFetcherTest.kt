package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefLinuxDoJsonFetcherTest {
    @Test
    fun `json is extracted from chromium wrapped source`() {
        val source = "<html><body><pre>{\"topic_list\":{\"topics\":[]}}</pre></body></html>"
        assertEquals("{\"topic_list\":{\"topics\":[]}}", JcefLinuxDoJsonFetcher.extractJson(source))
    }

    @Test
    fun `challenge html without json is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            JcefLinuxDoJsonFetcher.extractJson("<html><body>Just a moment...</body></html>")
        }
    }

    @Test
    fun `tagged payloads keep json after the generation prefix`() {
        val json = "{\"id\":1}\n{\"more\":true}"
        assertEquals(json, JcefLinuxDoJsonFetcher.parseTagged("7|$json", 7))
        assertNull(JcefLinuxDoJsonFetcher.parseTagged("6|$json", 7))
    }

    @Test
    fun `message bus arrays are extracted as json`() {
        val source = "<html><body><pre>[{\"channel\":\"/latest\"}]</pre></body></html>"
        assertEquals("[{\"channel\":\"/latest\"}]", JcefLinuxDoJsonFetcher.extractJson(source))
        assertEquals("[]", JcefLinuxDoJsonFetcher.extractJson("[]"))
    }

    @Test
    fun `http is not treated as an already absolute community path`() {
        assertEquals(
            "https://linux.do/http-only",
            moe.momokko.intellido.transport.LinuxDoUrls.absolute("http-only"),
        )
        assertEquals(
            "https://ping.ldstatic.com/message-bus/x/poll",
            moe.momokko.intellido.transport.LinuxDoUrls.absolute("https://ping.ldstatic.com/message-bus/x/poll"),
        )
    }

    @Test
    fun `stream frames keep chunks separate from json bodies`() {
        val chunk = JcefLinuxDoJsonFetcher.parseStreamFrame("3|C|[]")
        assertEquals(3, chunk?.gen)
        assertEquals(JcefLinuxDoJsonFetcher.StreamKind.CHUNK, chunk?.kind)
        assertEquals("[]", chunk?.data)
        val done = JcefLinuxDoJsonFetcher.parseStreamFrame("3|D|")
        assertEquals(JcefLinuxDoJsonFetcher.StreamKind.DONE, done?.kind)
        val body = JcefLinuxDoJsonFetcher.parseStreamFrame("7|{\"id\":1}")
        assertEquals(JcefLinuxDoJsonFetcher.StreamKind.BODY, body?.kind)
        assertEquals("{\"id\":1}", body?.data)
    }

    @Test
    fun `in-page fetch asks Discourse for chinese`() {
        assertEquals("zh-CN,zh;q=0.9", JcefFetchPolicy.ACCEPT_LANGUAGE)
        assertEquals("zh_CN", JcefFetchPolicy.DISCOURSE_LOCALE)
        assertTrue(JcefFetchPolicy.JSON_FETCH_HEADERS_JS.contains("Accept-Language"))
        assertTrue(JcefFetchPolicy.JSON_FETCH_HEADERS_JS.contains("application/json"))
    }
}

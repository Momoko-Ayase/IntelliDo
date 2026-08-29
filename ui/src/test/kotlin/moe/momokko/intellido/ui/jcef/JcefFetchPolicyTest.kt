package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefFetchPolicyTest {
    @Test
    fun `blank or timed-out payloads do not open the challenge dialog`() {
        assertFalse(JcefFetchPolicy.needsChallengeDialog(""))
        assertFalse(JcefFetchPolicy.needsChallengeDialog("   "))
        assertFalse(JcefFetchPolicy.needsChallengeDialog("{\"post_stream\":{}}"))
        assertTrue(JcefFetchPolicy.needsChallengeDialog("<html>Just a moment...</html>"))
        assertTrue(JcefFetchPolicy.needsChallengeDialog("<html>确认您是真人</html>"))
    }

    @Test
    fun `pretty printed json stays valid after flattening newlines`() {
        val pretty = "{\n  \"post_stream\": {\n    \"posts\": []\n  }\n}"
        val flat = JcefFetchPolicy.flattenJson(pretty)
        assertFalse('\n' in flat)
        assertTrue(flat.contains("\"post_stream\""))
        assertEquals('{', flat.first())
        assertEquals('}', flat.last())
    }

    @Test
    fun `origin is ready after the document, not the full Ember asset waterfall`() {
        assertTrue(JcefFetchPolicy.ORIGIN_SETTLE_MS <= 80)
        assertTrue(JcefFetchPolicy.ORIGIN_LOAD_TIMEOUT_SEC <= 10L)
        assertTrue(JcefFetchPolicy.ORIGIN_PROBE_TIMEOUT_SEC <= 2L)
        assertEquals(1, JcefFetchPolicy.ORIGIN_PROBES)
        assertTrue(JcefFetchPolicy.JSON_FETCH_HEADERS_JS.contains("application/json"))
        assertTrue(JcefFetchPolicy.JSON_FETCH_HEADERS_JS.contains("X-Requested-With"))
        assertFalse(JcefFetchPolicy.JSON_FETCH_HEADERS_JS.contains("IntelliDo"))
        assertTrue(JcefFetchPolicy.isSiteJson("https://linux.do/site.json"))
        assertFalse(JcefFetchPolicy.isSiteJson("https://linux.do/latest.json"))
    }
}

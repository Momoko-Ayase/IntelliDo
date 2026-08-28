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
}

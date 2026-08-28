package moe.momokko.intellido.connect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class ConnectProgressFormatterTest {
    @Test
    fun `summary comparison is labeled as an estimate not official Connect status`() {
        val estimate = ConnectProgress.Estimate(
            trustLevel = 0,
            source = "LINUX DO Summary",
            missingMetrics = listOf("visits"),
        )
        val zh = Locale.SIMPLIFIED_CHINESE
        assertEquals("估算", ConnectProgressFormatter.label(estimate, zh))
        assertFalse(ConnectProgressFormatter.isAuthoritative(estimate))
        assertTrue(
            ConnectProgressFormatter.isAuthoritative(
                ConnectProgress.Official(trustLevel = 2, summary = "from Connect"),
            ),
        )
        assertEquals("Connect", ConnectProgressFormatter.label(ConnectProgress.Official(2, "from Connect"), zh))
    }
}

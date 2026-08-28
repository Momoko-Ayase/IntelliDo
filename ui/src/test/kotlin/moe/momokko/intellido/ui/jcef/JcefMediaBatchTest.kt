package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefMediaBatchTest {
    @Test
    fun `batch payload round trips url to bytes without newlines`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38)
        val payload = JcefMediaBatch.encode(
            mapOf(
                "https://linux.do/a.png" to png,
                "https://cdn.ldstatic.com/b.gif" to gif,
            ),
        )
        assertFalse('\n' in payload)
        val decoded = JcefMediaBatch.decode(payload)
        assertEquals(2, decoded.size)
        assertArrayEquals(png, decoded["https://linux.do/a.png"])
        assertArrayEquals(gif, decoded["https://cdn.ldstatic.com/b.gif"])
    }

    @Test
    fun `empty or oversized slots are skipped`() {
        val payload = JcefMediaBatch.encode(
            mapOf(
                "https://linux.do/ok.png" to byteArrayOf(1, 2, 3),
                "https://linux.do/empty.png" to byteArrayOf(),
            ),
        )
        val decoded = JcefMediaBatch.decode(payload)
        assertEquals(setOf("https://linux.do/ok.png"), decoded.keys)
        assertTrue(JcefMediaBatch.decode("").isEmpty())
    }
}

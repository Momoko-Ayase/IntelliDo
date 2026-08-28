package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwemojiAssetsTest {
    @Test
    fun `bundled twemoji pngs cover discourse shortcodes`() {
        val tada = TwemojiAssets.bytes("tada")
        assertNotNull(tada)
        assertEquals(0x89.toByte(), tada!![0])
        assertTrue(tada.size > 32)
        assertTrue(TwemojiAssets.has(":heart:"))
        assertTrue(TwemojiAssets.has("", "https://linux.do/images/emoji/twemoji/slight_smile.png?v=12"))
        assertNull(TwemojiAssets.bytes("intellido_custom"))
    }
}

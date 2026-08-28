package moe.momokko.intellido.ui.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class SafeImagesTest {
    @Test
    fun `tiny png decodes`() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        assertEquals(1 to 1, SafeImages.bounds(png))
        assertNotNull(SafeImages.decode(png))
    }

    @Test
    fun `png bomb header is rejected before decode`() {
        val header = ByteArray(24)
        header[0] = 0x89.toByte()
        header[1] = 0x50
        header[16] = 0x00
        header[17] = 0x00
        header[18] = 0xEA.toByte()
        header[19] = 0x60
        header[20] = 0x00
        header[21] = 0x00
        header[22] = 0xEA.toByte()
        header[23] = 0x60
        assertEquals(60000 to 60000, SafeImages.bounds(header))
        assertNull(SafeImages.decode(header))
    }
}

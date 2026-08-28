package moe.momokko.intellido.ui.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

class PostImagesTest {
    @Test
    fun `magic bytes pick the IntelliJ image file extension`() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        assertEquals("png", PostImages.extension(png))
        assertEquals("gif", PostImages.extension("GIF89a".toByteArray() + ByteArray(8)))
        assertEquals("jpg", PostImages.extension(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals("png", PostImages.extension(byteArrayOf(1, 2, 3)))
    }
}

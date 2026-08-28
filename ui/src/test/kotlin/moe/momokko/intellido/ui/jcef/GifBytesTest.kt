package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GifBytesTest {
    @Test
    fun `gif magic is recognized and jpeg is not`() {
        assertTrue(GifBytes.isGif("GIF89a....".toByteArray()))
        assertTrue(GifBytes.isGif("GIF87a....".toByteArray()))
        assertFalse(GifBytes.isGif(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertFalse(GifBytes.isGif(byteArrayOf(1, 2, 3)))
    }
}

package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefDataUrlTest {
    @Test
    fun `image data urls round trip`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = JcefDataUrl.encode(bytes)
        assertTrue(encoded.startsWith("data:image/jpeg;base64,"))
        assertArrayEquals(bytes, JcefDataUrl.decode(encoded))
    }

    @Test
    fun `non image payloads are rejected`() {
        assertNull(JcefDataUrl.decode("loaded"))
        assertNull(JcefDataUrl.decode("data:text/html,abc"))
        assertNull(JcefDataUrl.decode("<html>Just a moment</html>"))
    }
}

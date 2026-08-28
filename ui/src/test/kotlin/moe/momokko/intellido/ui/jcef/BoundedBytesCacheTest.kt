package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BoundedBytesCacheTest {
    @Test
    fun `evicts oldest entries when the byte cap is exceeded`() {
        val cache = BoundedBytesCache(maxEntries = 8, maxBytes = 8)
        cache.put("a", byteArrayOf(1, 2, 3, 4, 5))
        cache.put("b", byteArrayOf(6, 7, 8, 9, 10))
        assertNull(cache.get("a"))
        assertArrayEquals(byteArrayOf(6, 7, 8, 9, 10), cache.get("b"))
    }
}

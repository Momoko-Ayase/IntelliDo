package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HashedFileCacheTest {
    @Test
    fun `avatar bytes persist across cache instances`(@TempDir dir: Path) {
        val url = "https://linux.do/user_avatar/linux.do/system/90/1_2.png"
        val bytes = byteArrayOf(9, 8, 7, 6)
        HashedFileCache(dir).write(url, bytes)
        assertArrayEquals(bytes, HashedFileCache(dir).read(url))
    }

    @Test
    fun `unknown keys miss`(@TempDir dir: Path) {
        assertNull(HashedFileCache(dir).read("https://linux.do/missing.png"))
    }
}

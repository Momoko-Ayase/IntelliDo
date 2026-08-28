package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaUrlsTest {
    @Test
    fun `query strings do not split the same upload`() {
        val a = "https://cdn3.ldstatic.com/original/3X/e/4/x.png?v=15"
        val b = "https://cdn3.ldstatic.com/original/3X/e/4/x.png"
        assertEquals(MediaUrls.key(a), MediaUrls.key(b))
    }

    @Test
    fun `optimized discourse uploads become original files`() {
        val preview =
            "https://linux.do/uploads/default/optimized/4X/7/5/c/75cdc_2_690x270.png"
        assertEquals(
            "https://linux.do/uploads/default/original/4X/7/5/c/75cdc.png",
            MediaUrls.original(preview),
        )
        val already = "https://cdn3.ldstatic.com/original/3X/e/4/x.png?v=15"
        assertEquals("https://cdn3.ldstatic.com/original/3X/e/4/x.png", MediaUrls.original(already))
    }
}

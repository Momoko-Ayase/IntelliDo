package moe.momokko.intellido.domain.icon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaGlyphsTest {
    @Test
    fun `linux do category icons resolve to Font Awesome paths`() {
        val comments = FaGlyphs.get("comments")
        assertNotNull(comments)
        assertEquals("0 0 640 512", comments!!.viewBox)
        assertTrue(comments.path.startsWith("M208 352"))
        assertTrue(FaGlyphs.get("code")!!.path.contains("M392.8"))
        assertTrue(FaGlyphs.get("droplet")!!.path.contains("M192 512"))
        assertNotNull(FaGlyphs.get("layer-group"))
        assertNotNull(FaGlyphs.get("lightbulb"))
        assertNotNull(FaGlyphs.get("thumbtack"))
        assertNotNull(FaGlyphs.get("heart"))
        assertNotNull(FaGlyphs.get("shield-halved"))
        assertNotNull(FaGlyphs.get("fire"))
        assertNotNull(FaGlyphs.get("location-dot"))
        assertNotNull(FaGlyphs.get("seedling"))
        assertNotNull(FaGlyphs.get("rss"))
        assertNotNull(FaGlyphs.get("calendar"))
        assertNotNull(FaGlyphs.get("ellipsis-vertical"))
        assertNotNull(FaGlyphs.get("chart-simple"))
        assertNotNull(FaGlyphs.get("filter"))
        assertNotNull(FaGlyphs.get("youtube"))
        assertNotNull(FaGlyphs.get("zhihu"))
        assertNull(FaGlyphs.get("not-an-icon"))
    }

    @Test
    fun `glyph svg uses the fill color and original path`() {
        val svg = FaGlyphs.get("bullhorn")!!.svg("#00aeff")
        assertTrue(svg.contains("viewBox=\"0 0 512 512\""))
        assertTrue(svg.contains("width=\"512\""))
        assertTrue(svg.contains("height=\"512\""))
        assertTrue(svg.contains("fill=\"#00aeff\""))
        assertTrue(svg.contains("M480 32"))
    }
}

package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EmojiShortcodesTest {
    @Test
    fun `discourse shortcodes become unicode glyphs`() {
        assertEquals("🙂", EmojiShortcodes.glyph(":slight_smile:"))
        assertEquals("👍", EmojiShortcodes.glyph("+1"))
        assertEquals("❤️", EmojiShortcodes.glyph(":heart:"))
        assertEquals("🎉", EmojiShortcodes.glyph("tada"))
    }

    @Test
    fun `emoji file names are recognized from linux do urls`() {
        assertEquals(
            "🙂",
            EmojiShortcodes.glyph(
                alt = ":slight_smile:",
                src = "https://linux.do/images/emoji/twitter/slight_smile.png?v=12",
            ),
        )
    }

    @Test
    fun `custom emoji without a glyph stays null`() {
        assertNull(EmojiShortcodes.glyph(":intellido_custom:"))
        assertNull(EmojiShortcodes.glyph("not-an-emoji"))
    }

    @Test
    fun `cldr face names used by linux do still become glyphs`() {
        assertEquals("🥳", EmojiShortcodes.glyph(":partying_face:"))
        assertEquals("🥲", EmojiShortcodes.glyph("smiling_face_with_tear"))
        assertEquals("😯", EmojiShortcodes.glyph(":hushed_face:"))
        assertEquals("☺️", EmojiShortcodes.glyph("smiling_face"))
        assertEquals("🫠", EmojiShortcodes.glyph(":melting_face:"))
    }

    @Test
    fun `vendored discourse-emojis database covers names beyond the old hand list`() {
        assertEquals("🧩", EmojiShortcodes.glyph(":jigsaw:"))
        assertEquals("🧪", EmojiShortcodes.glyph("test_tube"))
        assertNull(EmojiShortcodes.glyph(":intellido_custom:"))
    }
}

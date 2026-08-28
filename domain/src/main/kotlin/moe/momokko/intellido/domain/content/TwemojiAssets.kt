package moe.momokko.intellido.domain.content

/**
 * Complete local Twemoji 72×72 PNGs vendored from discourse-emojis.
 */
object TwemojiAssets {
    fun bytes(alt: String, src: String = ""): ByteArray? {
        val png = EmojiShortcodes.pngName(alt, src) ?: return null
        return read(png) ?: srcName(src)?.let { read(it) }
    }

    fun has(alt: String, src: String = ""): Boolean = bytes(alt, src) != null

    private fun srcName(src: String): String? {
        if (src.isBlank()) {
            return null
        }
        val file = src.substringAfterLast('/').substringBefore('?')
        return file.takeIf { it.endsWith(".png", ignoreCase = true) }
    }

    private fun read(png: String): ByteArray? {
        val path = "/vendor/twemoji/${png.trim().trimStart('/')}"
        val stream = TwemojiAssets::class.java.getResourceAsStream(path) ?: return null
        return stream.use { it.readBytes() }.takeIf { it.size >= 32 && it[0] == 0x89.toByte() }
    }
}

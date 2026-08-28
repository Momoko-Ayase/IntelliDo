package moe.momokko.intellido.domain.content

/**
 * Discourse / Twemoji shortcodes from the vendored [discourse-emojis](https://github.com/discourse/discourse-emojis) database.
 */
object EmojiShortcodes {
    fun glyph(alt: String, src: String = ""): String? {
        val key = shortcode(alt, src) ?: return null
        table[key]?.glyph?.let { return it }
        if (key.endsWith("_face")) {
            table[key.removeSuffix("_face")]?.glyph?.let { return it }
        }
        table[key.replace("-", "_")]?.glyph?.let { return it }
        return null
    }

    fun pngName(alt: String, src: String = ""): String? {
        val key = shortcode(alt, src) ?: return null
        table[key]?.png?.let { return it }
        table[key.replace("-", "_")]?.png?.let { return it }
        return "$key.png".takeIf { key.isNotEmpty() }
    }

    fun shortcode(alt: String, src: String = ""): String? =
        normalize(alt) ?: nameFromSrc(src)

    fun known(alt: String, src: String = ""): Boolean =
        shortcode(alt, src)?.let { table.containsKey(it) || table.containsKey(it.replace("-", "_")) } == true

    private fun normalize(value: String): String? {
        val trimmed = value.trim().removeSurrounding(":").trim().lowercase()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    private fun nameFromSrc(src: String): String? {
        if (src.isBlank()) {
            return null
        }
        val file = src.substringAfterLast('/').substringBefore('?')
        val name = file.substringBeforeLast('.')
        return name.takeIf { it.isNotBlank() }?.lowercase()
    }

    private data class Entry(val glyph: String, val png: String)

    private val table: Map<String, Entry> by lazy { load() }

    private fun load(): Map<String, Entry> {
        val stream = EmojiShortcodes::class.java.getResourceAsStream("/vendor/emoji/shortcodes.tsv")
            ?: return emptyMap()
        return stream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) {
                    null
                } else {
                    parts[0].lowercase() to Entry(parts[1], parts[2])
                }
            }.toMap()
        }
    }
}

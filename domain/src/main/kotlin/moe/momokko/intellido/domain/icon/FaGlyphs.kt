package moe.momokko.intellido.domain.icon

data class FaGlyph(
    val name: String,
    val viewBox: String,
    val path: String,
) {
    fun svg(fill: String): String {
        val box = viewBox.trim().split(Regex("\\s+"))
        val w = box.getOrElse(2) { "16" }
        val h = box.getOrElse(3) { "16" }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox" width="$w" height="$h"><path fill="$fill" d="$path"/></svg>"""
    }
}

object FaGlyphs {
    fun get(name: String): FaGlyph? {
        val key = name.trim().lowercase()
            .removePrefix("fa-solid ")
            .removePrefix("fa-regular ")
            .removePrefix("fa-brands ")
            .removePrefix("fa-solid-")
            .removePrefix("fa-regular-")
            .removePrefix("fa-brands-")
            .removePrefix("fa-")
            .substringAfterLast(' ')
            .trim()
        if (key.isEmpty()) {
            return null
        }
        return table[key] ?: table[key.replace("_", "-")] ?: table[key.replace("-", "_")]
    }

    private val table: Map<String, FaGlyph> by lazy { load() }

    private fun load(): Map<String, FaGlyph> {
        val stream = FaGlyphs::class.java.getResourceAsStream("/vendor/fontawesome/glyphs.tsv")
            ?: FaGlyphs::class.java.getResourceAsStream("/fa-glyphs.tsv")
            ?: return emptyMap()
        return stream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size < 3) {
                    null
                } else {
                    parts[0] to FaGlyph(parts[0], parts[1], parts[2])
                }
            }.toMap()
        }
    }
}

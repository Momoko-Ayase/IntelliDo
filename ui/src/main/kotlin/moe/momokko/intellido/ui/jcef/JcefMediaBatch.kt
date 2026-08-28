package moe.momokko.intellido.ui.jcef

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Compact in-page media batch: several data URLs in one JBCefJSQuery payload.
 * Record separators are ASCII unit/record separators so Chromium's newline
 * truncation cannot split the callback.
 */
object JcefMediaBatch {
    val UNIT: String = 0x1F.toChar().toString()
    val RECORD: String = 0x1E.toChar().toString()
    const val MAX_FILE_BYTES: Int = 350_000
    const val CHUNK: Int = 8

    fun encode(files: Map<String, ByteArray>): String =
        files.filterValues { it.isNotEmpty() }.entries.joinToString(RECORD) { (url, bytes) ->
            val dataUrl = when {
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() ->
                    JcefDataUrl.encode(bytes, "image/png")
                bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() ->
                    JcefDataUrl.encode(bytes, "image/gif")
                else -> JcefDataUrl.encode(bytes, "image/jpeg")
            }
            encodeUrl(url) + UNIT + dataUrl
        }

    fun decode(payload: String): Map<String, ByteArray> {
        if (payload.isBlank()) {
            return emptyMap()
        }
        val out = linkedMapOf<String, ByteArray>()
        payload.split(RECORD).forEach { record ->
            val sep = record.indexOf(UNIT)
            if (sep <= 0) {
                return@forEach
            }
            val url = decodeUrl(record.substring(0, sep))
            val bytes = JcefDataUrl.decode(record.substring(sep + UNIT.length)) ?: return@forEach
            if (bytes.isNotEmpty()) {
                out[url] = bytes
            }
        }
        return out
    }

    fun encodeUrl(url: String): String =
        URLEncoder.encode(url, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decodeUrl(raw: String): String =
        URLDecoder.decode(raw, StandardCharsets.UTF_8)
}

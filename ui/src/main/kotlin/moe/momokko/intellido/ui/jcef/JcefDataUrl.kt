package moe.momokko.intellido.ui.jcef

import java.util.Base64

object JcefDataUrl {
    fun decode(dataUrl: String): ByteArray? {
        val trimmed = dataUrl.trim()
        if (!trimmed.startsWith("data:image") || !trimmed.contains(",")) {
            return null
        }
        val payload = trimmed.substringAfter(',')
        if (payload.isEmpty()) {
            return null
        }
        return runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
    }

    fun encode(bytes: ByteArray, mime: String = "image/jpeg"): String =
        "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"

    fun isImage(dataUrl: String): Boolean = dataUrl.trim().startsWith("data:image")
}

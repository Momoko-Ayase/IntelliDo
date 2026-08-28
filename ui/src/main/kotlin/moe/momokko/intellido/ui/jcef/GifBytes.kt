package moe.momokko.intellido.ui.jcef

object GifBytes {
    fun isGif(bytes: ByteArray): Boolean {
        if (bytes.size < 6) {
            return false
        }
        val header = bytes.decodeToString(0, 6)
        return header == "GIF89a" || header == "GIF87a"
    }
}

package moe.momokko.intellido.ui.content

import java.awt.Image
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/**
 * Decode community images with an explicit pixel budget. ImageIcon on a PNG bomb
 * will allocate width*height*4 on the EDT.
 */
object SafeImages {
    const val MAX_EDGE: Int = 8192
    const val MAX_PIXELS: Int = 8_000_000

    fun decode(bytes: ByteArray, maxEdge: Int = MAX_EDGE, maxPixels: Int = MAX_PIXELS): Image? {
        if (bytes.isEmpty()) {
            return null
        }
        val bounds = bounds(bytes) ?: return runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()?.takeIf { image ->
            val width = image.width
            val height = image.height
            width in 1..maxEdge && height in 1..maxEdge && width.toLong() * height <= maxPixels
        }
        if (bounds.first !in 1..maxEdge || bounds.second !in 1..maxEdge) {
            return null
        }
        if (bounds.first.toLong() * bounds.second > maxPixels) {
            return null
        }
        return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: runCatching { ImageIcon(bytes).image }.getOrNull()
    }

    fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size >= 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
            val width = readBeInt(bytes, 16)
            val height = readBeInt(bytes, 20)
            return width to height
        }
        if (bytes.size >= 10 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) {
            val width = readLeShort(bytes, 6)
            val height = readLeShort(bytes, 8)
            return width to height
        }
        return null
    }

    private fun readBeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readLeShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

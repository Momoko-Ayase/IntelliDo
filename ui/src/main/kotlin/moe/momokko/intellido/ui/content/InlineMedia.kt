package moe.momokko.intellido.ui.content

import moe.momokko.intellido.domain.content.MediaUrls
import java.awt.Image
import java.util.concurrent.ConcurrentHashMap
import javax.swing.ImageIcon

/**
 * In-process images for cooked HTML. Swing's HTMLEditorKit cannot paint `data:` URLs,
 * so emoji (twemoji + custom) are registered here and referenced as `intellido-media:` keys.
 */
object InlineMedia {
    const val SCHEME: String = "intellido-media:"

    private val images = ConcurrentHashMap<String, Image>()

    fun key(url: String): String = SCHEME + MediaUrls.key(url)

    fun put(url: String, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        putImage(url, ImageIcon(bytes).image)
    }

    fun putImage(url: String, image: Image) {
        images[key(url)] = image
        images[url] = image
        images[MediaUrls.key(url)] = image
    }

    fun image(src: String?): Image? {
        val value = src?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }
        images[value]?.let { return it }
        if (value.startsWith(SCHEME)) {
            return images[value]
        }
        return images[key(value)] ?: images[MediaUrls.key(value)]
    }
}

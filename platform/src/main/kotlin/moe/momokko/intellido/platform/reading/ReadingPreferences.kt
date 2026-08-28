package moe.momokko.intellido.platform.reading

import moe.momokko.intellido.platform.i18n.LocalPreferenceStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReadingStyle(
    val fontSize: Int,
    val lineHeight: Float,
    val maxWidth: Int,
) {
    fun clamped(): ReadingStyle = copy(
        fontSize = fontSize.coerceIn(MIN_FONT, MAX_FONT),
        lineHeight = ((lineHeight * 20f).roundToInt() / 20f).coerceIn(MIN_LINE, MAX_LINE),
        maxWidth = clampWidth(maxWidth),
    )

    companion object {
        val DEFAULT: ReadingStyle = ReadingStyle(15, 1.9f, 0)
        const val MIN_FONT: Int = 12
        const val MAX_FONT: Int = 22
        const val MIN_LINE: Float = 1.5f
        const val MAX_LINE: Float = 2.4f
        val WIDTH_CHOICES: List<Int> = listOf(0, 720, 900, 1100, 1400)

        private fun clampWidth(width: Int): Int {
            if (width <= 0) {
                return 0
            }
            return WIDTH_CHOICES.filter { it > 0 }.minByOrNull { abs(it - width) } ?: width.coerceIn(600, 2000)
        }
    }
}

object ReadingPreferences {
    const val FONT_SIZE: String = "reading.fontSize"
    const val LINE_HEIGHT: String = "reading.lineHeight"
    const val MAX_WIDTH: String = "reading.maxWidth"

    fun load(store: LocalPreferenceStore): ReadingStyle =
        ReadingStyle(
            fontSize = store.get(FONT_SIZE)?.toIntOrNull() ?: ReadingStyle.DEFAULT.fontSize,
            lineHeight = store.get(LINE_HEIGHT)?.toFloatOrNull() ?: ReadingStyle.DEFAULT.lineHeight,
            maxWidth = store.get(MAX_WIDTH)?.toIntOrNull() ?: ReadingStyle.DEFAULT.maxWidth,
        ).clamped()

    fun save(store: LocalPreferenceStore, style: ReadingStyle) {
        val value = style.clamped()
        store.set(FONT_SIZE, value.fontSize.toString())
        store.set(LINE_HEIGHT, value.lineHeight.toString())
        store.set(MAX_WIDTH, value.maxWidth.toString())
    }
}

object ReadingAppearance {
    @Volatile
    var current: ReadingStyle = ReadingStyle.DEFAULT
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun replace(style: ReadingStyle) {
        current = style.clamped()
        listeners.forEach { listener -> listener() }
    }

    fun listen(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }
}

package moe.momokko.intellido.platform.topic

import kotlin.math.roundToInt

/**
 * Compact counts and heatmap thresholds as shown on LINUX DO's topic list.
 */
object DiscourseNumber {
    fun compact(value: Int): String {
        if (value < 1_000) {
            return value.toString()
        }
        if (value >= 1_000_000) {
            val tenths = (value / 100_000.0).roundToInt()
            val whole = tenths / 10
            val frac = tenths % 10
            return if (frac == 0) "${whole}M" else "$whole.${frac}M"
        }
        if (value < 100_000) {
            val tenths = (value / 100.0).roundToInt()
            val whole = tenths / 10
            val frac = tenths % 10
            return "$whole.${frac}k"
        }
        return "${value / 1_000}k"
    }

    fun hotReplies(value: Int): Boolean = value >= 100

    fun hotViews(value: Int): Boolean = value >= 1_000
}

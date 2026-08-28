package moe.momokko.intellido.platform.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RelativeTime {
    fun format(instant: Instant, now: Instant = Instant.now(), locale: Locale = Locale.SIMPLIFIED_CHINESE): String {
        val delta = Duration.between(instant, now)
        if (delta.isNegative || delta.seconds < 60) {
            return if (locale.language == "zh") "刚刚" else "now"
        }
        val minutes = delta.toMinutes()
        if (minutes < 60) {
            return if (locale.language == "zh") "${minutes}分钟" else "${minutes}m"
        }
        val hours = delta.toHours()
        if (hours < 24) {
            return if (locale.language == "zh") "${hours}小时" else "${hours}h"
        }
        val days = delta.toDays()
        if (days == 1L) {
            return if (locale.language == "zh") "昨天" else "yesterday"
        }
        if (days < 7) {
            return if (locale.language == "zh") "${days}天" else "${days}d"
        }
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val nowDate = now.atZone(ZoneId.systemDefault()).toLocalDate()
        return if (locale.language == "zh") {
            if (date.year == nowDate.year) {
                "${date.monthValue}月${date.dayOfMonth}日"
            } else {
                "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
            }
        } else {
            date.format(DateTimeFormatter.ofPattern("MMM d", locale))
        }
    }

    fun calendarDate(instant: Instant, locale: Locale = Locale.SIMPLIFIED_CHINESE): String {
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return if (locale.language == "zh") {
            "${date.year} 年 ${date.monthValue}月 ${date.dayOfMonth} 日"
        } else {
            date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        }
    }

    fun monthYear(instant: Instant, locale: Locale = Locale.SIMPLIFIED_CHINESE): String {
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return if (locale.language == "zh") {
            "${date.year} 年 ${date.monthValue}月"
        } else {
            date.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
        }
    }

    fun durationTiny(seconds: Long, locale: Locale = Locale.SIMPLIFIED_CHINESE): String {
        if (seconds <= 0) {
            return if (locale.language == "zh") "刚刚" else "now"
        }
        val zh = locale.language == "zh"
        val days = seconds / 86_400.0
        if (days >= 365) {
            val count = kotlin.math.round(days / 365.0).toInt().coerceAtLeast(1)
            return if (zh) "$count 年" else "${count}y"
        }
        if (days >= 31) {
            val count = kotlin.math.round(days / 30.0).toInt().coerceAtLeast(1)
            return if (zh) "$count 个月" else "${count}mo"
        }
        if (days >= 1) {
            val count = kotlin.math.round(days).toInt().coerceAtLeast(1)
            return if (zh) "$count 天" else "${count}d"
        }
        val hours = seconds / 3_600.0
        if (hours >= 1) {
            val count = kotlin.math.round(hours).toInt().coerceAtLeast(1)
            return if (zh) "$count 小时" else "${count}h"
        }
        val minutes = seconds / 60.0
        if (minutes >= 1) {
            val count = kotlin.math.round(minutes).toInt().coerceAtLeast(1)
            return if (zh) "$count 分钟" else "${count}m"
        }
        return if (zh) "刚刚" else "now"
    }
}

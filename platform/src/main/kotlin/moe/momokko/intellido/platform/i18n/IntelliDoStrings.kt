package moe.momokko.intellido.platform.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

object IntelliDoStrings {
    const val BUNDLE_NAME: String = "messages.IntelliDoBundle"

    fun message(key: String, locale: Locale, vararg args: Any): String {
        val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale)
        val pattern = bundle.getString(key)
        return if (args.isEmpty()) pattern else MessageFormat.format(pattern, *args)
    }

    fun messageOrNull(key: String, locale: Locale, vararg args: Any): String? =
        runCatching { message(key, locale, *args) }.getOrNull()
}

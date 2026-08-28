package moe.momokko.intellido.platform.i18n

import java.util.Locale

/**
 * First launch is Simplified Chinese on every OS. Later launches keep the saved choice.
 */
class IntelliDoLocale(
    private val store: LocalPreferenceStore,
) {
    fun resolve(operatingSystemLocale: Locale): Locale {
        val saved = store.get(PREFERENCE_KEY)
        if (!saved.isNullOrBlank()) {
            return Locale.forLanguageTag(saved)
        }
        store.set(PREFERENCE_KEY, SIMPLIFIED_CHINESE.toLanguageTag())
        return SIMPLIFIED_CHINESE
    }

    companion object {
        const val PREFERENCE_KEY: String = "ui.locale"
        val SIMPLIFIED_CHINESE: Locale = Locale.SIMPLIFIED_CHINESE
    }
}

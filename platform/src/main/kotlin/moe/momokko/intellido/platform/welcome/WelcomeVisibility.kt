package moe.momokko.intellido.platform.welcome

import moe.momokko.intellido.platform.i18n.LocalPreferenceStore

/**
 * First launch may show a closeable native welcome page. Closing it persists locally
 * so later launches start on Home only.
 */
class WelcomeVisibility(
    private val store: LocalPreferenceStore,
) {
    fun shouldShow(): Boolean = store.get(PREFERENCE_KEY) != DISMISSED_VALUE

    fun dismiss() {
        store.set(PREFERENCE_KEY, DISMISSED_VALUE)
    }

    companion object {
        const val PREFERENCE_KEY: String = "welcome.dismissed"
        const val DISMISSED_VALUE: String = "true"
    }
}

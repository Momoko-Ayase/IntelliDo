package moe.momokko.intellido.platform.welcome

import moe.momokko.intellido.platform.i18n.InMemoryLocalPreferenceStore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeVisibilityTest {
    @Test
    fun `first launch shows the closeable welcome page`() {
        val visibility = WelcomeVisibility(InMemoryLocalPreferenceStore())
        assertTrue(visibility.shouldShow())
    }

    @Test
    fun `closing welcome does not show it on later launches`() {
        val store = InMemoryLocalPreferenceStore()
        val firstLaunch = WelcomeVisibility(store)
        firstLaunch.dismiss()

        val laterLaunch = WelcomeVisibility(store)
        assertFalse(laterLaunch.shouldShow())
    }
}

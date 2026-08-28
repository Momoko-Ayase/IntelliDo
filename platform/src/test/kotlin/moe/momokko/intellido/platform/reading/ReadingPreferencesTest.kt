package moe.momokko.intellido.platform.reading

import moe.momokko.intellido.platform.i18n.InMemoryLocalPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingPreferencesTest {
    @Test
    fun `defaults are large enough to read`() {
        val style = ReadingPreferences.load(InMemoryLocalPreferenceStore())
        assertEquals(15, style.fontSize)
        assertEquals(1.9f, style.lineHeight)
        assertEquals(0, style.maxWidth)
    }

    @Test
    fun `values round trip through the local store and stay clamped`() {
        val store = InMemoryLocalPreferenceStore()
        ReadingPreferences.save(store, ReadingStyle(fontSize = 99, lineHeight = 0.2f, maxWidth = 900))
        val style = ReadingPreferences.load(store)
        assertEquals(22, style.fontSize)
        assertEquals(1.5f, style.lineHeight)
        assertEquals(900, style.maxWidth)
        ReadingPreferences.save(store, ReadingStyle(fontSize = 18, lineHeight = 2.0f, maxWidth = 0))
        assertEquals(ReadingStyle(18, 2.0f, 0), ReadingPreferences.load(store))
    }
}

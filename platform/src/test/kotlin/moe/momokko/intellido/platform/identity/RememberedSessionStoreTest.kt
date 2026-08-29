package moe.momokko.intellido.platform.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RememberedSessionStoreTest {
    @Test
    fun `in-memory store remembers a single username`() {
        val store = InMemoryRememberedSessionStore()
        assertNull(store.username())
        assertTrue(store.remember("helper"))
        assertEquals("helper", store.username())
        store.clear()
        assertNull(store.username())
    }
}

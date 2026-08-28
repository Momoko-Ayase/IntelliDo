package moe.momokko.intellido.ui.workspace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalVirtualFilesTest {
    @Test
    fun `known files skip vfs refresh`() {
        var refreshed = false
        val found = LocalVirtualFiles.locate("existing") {
            refreshed = true
            "created"
        }
        assertEquals("existing", found)
        assertFalse(refreshed)
    }

    @Test
    fun `missing files go through refresh`() {
        assertEquals("created", LocalVirtualFiles.locate(null) { "created" })
        assertNull(LocalVirtualFiles.locate(null) { null })
    }
}

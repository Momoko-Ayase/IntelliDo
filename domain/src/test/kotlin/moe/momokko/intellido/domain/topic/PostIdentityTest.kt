package moe.momokko.intellido.domain.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PostIdentityTest {
    @Test
    fun `display name is primary and username is secondary`() {
        val names = PostIdentity.names("helper", "助手")
        assertEquals("助手", names.primary)
        assertEquals("helper", names.secondary)
        assertEquals("系统", PostIdentity.names("system", "系统").primary)
        assertEquals("system", PostIdentity.names("system", "系统").secondary)
    }

    @Test
    fun `matching or blank display names leave username as the only label`() {
        assertEquals("helper", PostIdentity.names("helper", "helper").primary)
        assertEquals("Helper", PostIdentity.names("helper", "Helper").primary)
        assertEquals("助手", PostIdentity.names("helper", "助手").primary)
        assertNull(PostIdentity.names("helper", "Helper").secondary)
        assertNull(PostIdentity.names("helper", "  ").secondary)
        assertNull(PostIdentity.names("helper", null).secondary)
    }
}

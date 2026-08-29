package moe.momokko.intellido.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemberCapabilitiesTest {
    @Test
    fun `anonymous mode can read public community content but cannot write or use account tools`() {
        val session = MemberSession.Anonymous
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_TOPIC))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_CATEGORY))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_TAG))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_GROUP))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_BADGE))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_PUBLIC_MEMBER))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.READ_COMMUNITY_ABOUT))
        assertTrue(MemberCapabilities.isAvailable(session, MemberAction.SEARCH_PUBLIC))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.LIKE))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.REPLY))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.BOOKMARK))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.MESSAGE))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.CHAT))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.CONNECT))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.NOTIFICATION))
        assertFalse(MemberCapabilities.isAvailable(session, MemberAction.DRAFT))
    }

    @Test
    fun `signed in session exposes avatar url from template`() {
        val signed = MemberSession.SignedIn(
            "helper",
            trustLevel = 2,
            id = 2,
            name = "助手",
            avatarTemplate = "/user_avatar/linux.do/helper/{size}/2.png",
        )
        assertEquals("助手", signed.displayLabel())
        assertEquals(
            "https://linux.do/user_avatar/linux.do/helper/48/2.png",
            signed.avatarUrl(),
        )
    }

    @Test
    fun `signed in members are capped at trust level 3`() {
        val signed = MemberSession.SignedIn("helper", trustLevel = 2)
        assertTrue(MemberCapabilities.isAvailable(signed, MemberAction.READ_PUBLIC_TOPIC))
        assertTrue(MemberCapabilities.isAvailable(signed, MemberAction.REPLY))
        assertFalse(
            MemberCapabilities.isAvailable(
                MemberSession.SignedIn("staff", trustLevel = 4),
                MemberAction.REPLY,
            ),
        )
    }

    @Test
    fun `server denial wins even for an otherwise readable public topic`() {
        assertFalse(
            MemberCapabilities.isAvailable(
                MemberSession.Anonymous,
                MemberAction.READ_PUBLIC_TOPIC,
                serverAllows = false,
            ),
        )
    }
}

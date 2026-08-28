package moe.momokko.intellido.domain.live

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopicPresenceStateTest {
    @Test
    fun `entering and leaving users keep a unique reply presence set`() {
        val state = TopicPresenceState()
        val system = LivePresenceUser(1, "system", "/user_avatar/linux.do/system/{size}/1.png")
        val helper = LivePresenceUser(2, "helper")
        assertEquals(listOf(system), state.apply(GuestLiveEvent.TopicPresence(9, entering = listOf(system))))
        assertEquals(
            listOf(system, helper),
            state.apply(GuestLiveEvent.TopicPresence(9, entering = listOf(helper))),
        )
        assertEquals(listOf(helper), state.apply(GuestLiveEvent.TopicPresence(9, leavingIds = listOf(1))))
    }
}

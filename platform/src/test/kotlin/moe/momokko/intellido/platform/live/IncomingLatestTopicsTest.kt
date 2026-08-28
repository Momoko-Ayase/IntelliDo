package moe.momokko.intellido.platform.live

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IncomingLatestTopicsTest {
    @Test
    fun `duplicate bumps count as one incoming topic until refresh`() {
        val incoming = IncomingLatestTopics()
        incoming.note(202)
        incoming.note(202)
        incoming.note(303)
        assertEquals(2, incoming.count())
        incoming.clear()
        assertEquals(0, incoming.count())
    }
}

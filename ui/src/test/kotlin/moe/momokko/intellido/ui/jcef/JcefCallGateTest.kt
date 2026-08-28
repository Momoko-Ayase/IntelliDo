package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JcefCallGateTest {
    @Test
    fun `media yields until json work has finished`() {
        val gate = JcefCallGate()
        val started = CountDownLatch(1)
        val released = AtomicBoolean(false)
        val mediaDone = CountDownLatch(1)
        gate.beginJson()
        Thread {
            started.countDown()
            gate.yieldToJson()
            released.set(gate.jsonWaiters() == 0)
            mediaDone.countDown()
        }.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        Thread.sleep(40)
        assertTrue(mediaDone.count == 1L, "media must wait while json is in flight")
        gate.endJson()
        assertTrue(mediaDone.await(1, TimeUnit.SECONDS))
        assertTrue(released.get())
    }
}

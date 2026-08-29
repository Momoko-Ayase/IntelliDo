package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class JcefSingleFlightTest {
    @Test
    fun `concurrent callers share one in-flight run`() {
        val flight = JcefSingleFlight(2, TimeUnit.SECONDS)
        val started = CyclicBarrier(4)
        val runs = AtomicInteger(0)
        val done = CountDownLatch(3)
        repeat(3) {
            Thread {
                started.await()
                flight.run {
                    runs.incrementAndGet()
                    Thread.sleep(80)
                }
                done.countDown()
            }.start()
        }
        started.await()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, runs.get())
    }

    @Test
    fun `a later caller starts a new run after the first finishes`() {
        val flight = JcefSingleFlight(2, TimeUnit.SECONDS)
        val runs = AtomicInteger(0)
        flight.run { runs.incrementAndGet() }
        flight.run { runs.incrementAndGet() }
        assertEquals(2, runs.get())
    }
}

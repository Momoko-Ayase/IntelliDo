package moe.momokko.intellido.ui.jcef

import java.util.concurrent.atomic.AtomicInteger

/**
 * One JCEF page can run one JS fetch at a time. JSON (Home, topic stream) goes
 * ahead of media so scrolling more posts is not blocked by emoji downloads.
 */
class JcefCallGate {
    private val json = AtomicInteger(0)

    fun beginJson() {
        json.incrementAndGet()
    }

    fun endJson() {
        json.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun jsonWaiters(): Int = json.get()

    fun yieldToJson() {
        var spins = 0
        while (json.get() > 0 && spins++ < 400) {
            Thread.sleep(10)
        }
    }
}

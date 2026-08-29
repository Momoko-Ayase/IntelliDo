package moe.momokko.intellido.ui.jcef

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs [block] once while concurrent callers wait for that attempt.
 * Used so Cloudflare challenge dialogs cannot stack via the modal EDT pump.
 */
class JcefSingleFlight(
    private val timeout: Long = 5,
    private val unit: TimeUnit = TimeUnit.MINUTES,
) {
    private val lock = Any()
    private var inFlight: CompletableFuture<Unit>? = null

    fun run(block: () -> Unit) {
        val joined: CompletableFuture<Unit>
        val owner: Boolean
        synchronized(lock) {
            val current = inFlight
            if (current != null) {
                joined = current
                owner = false
            } else {
                joined = CompletableFuture()
                inFlight = joined
                owner = true
            }
        }
        if (!owner) {
            joined.get(timeout, unit)
            return
        }
        try {
            block()
            joined.complete(Unit)
        } catch (error: Exception) {
            joined.completeExceptionally(error)
            throw error
        } finally {
            synchronized(lock) {
                if (inFlight === joined) {
                    inFlight = null
                }
            }
        }
    }
}

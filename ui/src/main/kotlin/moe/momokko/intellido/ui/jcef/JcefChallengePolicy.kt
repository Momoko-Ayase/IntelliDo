package moe.momokko.intellido.ui.jcef

/**
 * Extra Cloudflare dialogs after a recent success (or while sign-in already
 * shows linux.do) are what made verification feel like it never stopped.
 */
object JcefChallengePolicy {
    const val COOLDOWN_MS: Long = 30_000

    fun shouldOpenDialog(signInOpen: Boolean, lastSuccessAtMs: Long, nowMs: Long): Boolean {
        if (signInOpen) {
            return false
        }
        if (lastSuccessAtMs > 0 && nowMs - lastSuccessAtMs < COOLDOWN_MS) {
            return false
        }
        return true
    }
}

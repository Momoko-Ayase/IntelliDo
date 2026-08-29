package moe.momokko.intellido.platform.identity

/**
 * Remembers that a trusted LINUX DO session should be restored on the next
 * launch. The cookie jar itself stays in the isolated JCEF profile; this store
 * only holds the username so anonymous starts can still wipe leftovers.
 */
interface RememberedSessionStore {
    fun username(): String?

    /**
     * @return false when the OS-protected store is unavailable. Callers must
     * not persist reusable session secrets in that case.
     */
    fun remember(username: String): Boolean

    fun clear()
}

class InMemoryRememberedSessionStore : RememberedSessionStore {
    @Volatile
    private var value: String? = null

    override fun username(): String? = value

    override fun remember(username: String): Boolean {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        value = trimmed
        return true
    }

    override fun clear() {
        value = null
    }
}

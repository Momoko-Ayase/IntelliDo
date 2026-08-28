package moe.momokko.intellido.domain.topic

/**
 * LINUX DO post names: display name is first and bold; username follows when it differs.
 */
object PostIdentity {
    data class Names(val primary: String, val secondary: String?)

    fun names(username: String, displayName: String?): Names {
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) {
            return Names(primary = username, secondary = null)
        }
        val secondary = username.takeUnless { it.equals(name, ignoreCase = true) }
        return Names(primary = name, secondary = secondary)
    }
}

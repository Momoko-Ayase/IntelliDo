package moe.momokko.intellido.ui.session

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import moe.momokko.intellido.platform.identity.RememberedSessionStore

/**
 * Stores the remembered LINUX DO username in the OS credential store. Session
 * cookies stay in the isolated JCEF profile, never in application.properties.
 */
class PasswordSafeRememberedSessionStore : RememberedSessionStore {
    override fun username(): String? =
        runCatching { PasswordSafe.instance.getPassword(attributes()) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    override fun remember(username: String): Boolean {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        return runCatching {
            PasswordSafe.instance.set(attributes(), Credentials(SERVICE, trimmed))
            true
        }.getOrDefault(false)
    }

    override fun clear() {
        runCatching { PasswordSafe.instance.set(attributes(), null) }
    }

    private fun attributes(): CredentialAttributes = CredentialAttributes(SERVICE, "linux.do")

    companion object {
        const val SERVICE: String = "IntelliDo LINUX DO trusted session"
    }
}

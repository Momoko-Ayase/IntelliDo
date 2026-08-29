package moe.momokko.intellido.ui.jcef

import com.google.gson.JsonParser
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.transport.DiscourseJsonMapper
import moe.momokko.intellido.transport.LinuxDoUrls

/**
 * Compact payload from the visible login JCEF page. Sign-in is only finished
 * after leaving the LINUX DO auth routes with a current_user.
 */
object SignInProbe {
    private val mapper = DiscourseJsonMapper()

    fun sessionFromPayload(payload: String): MemberSession? {
        val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return null
        val href = root.get("href")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (LinuxDoUrls.isAuthLocation(href)) {
            return null
        }
        val user = root.get("user")?.takeIf { it.isJsonObject } ?: return null
        val session = mapper.currentSession("""{"current_user":$user}""")
        return session as? MemberSession.SignedIn
    }
}

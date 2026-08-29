package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.transport.LinuxDoUrls
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fluxdo treats `cf_clearance` as the pass signal and keeps it across launches.
 * Discourse `_t` / `_forum_session` are the LINUX DO session and stay ephemeral
 * until IntelliDo remembers a signed-in member.
 */
object JcefLinuxDoCookies {
    const val CLEARANCE: String = "cf_clearance"
    val DISCOURSE_SESSION: List<String> = listOf("_t", "_forum_session")

    fun hasClearance(): Boolean =
        runCatching {
            val found = AtomicBoolean(false)
            visit("https://linux.do/") { cookie, count, total ->
                if (cookie.name.equals(CLEARANCE, ignoreCase = true) && cookie.value.isNotBlank()) {
                    found.set(true)
                }
                count + 1 < total && !found.get()
            }
            found.get()
        }.getOrDefault(false)

    fun stripDiscourseSession() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        DISCOURSE_SESSION.forEach { name ->
            manager.deleteCookies(LinuxDoUrls.ORIGIN, name)
            manager.deleteCookies("https://www.linux.do", name)
        }
    }

    fun deleteClearance() {
        val manager = CefCookieManager.getGlobalManager() ?: return
        manager.deleteCookies(LinuxDoUrls.ORIGIN, CLEARANCE)
        manager.deleteCookies("https://www.linux.do", CLEARANCE)
    }

    fun flush() {
        runCatching { CefCookieManager.getGlobalManager()?.flushStore { } }
    }

    private fun visit(url: String, onCookie: (CefCookie, Int, Int) -> Boolean) {
        val manager = CefCookieManager.getGlobalManager() ?: return
        val done = CountDownLatch(1)
        val visitor = object : CefCookieVisitor {
            override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                val keepGoing = onCookie(cookie, count, total)
                if (count + 1 >= total || !keepGoing) {
                    done.countDown()
                }
                return keepGoing
            }
        }
        val ok = runCatching { manager.visitAllCookies(visitor) }.getOrDefault(false) ||
            runCatching { manager.visitUrlCookies(url, true, visitor) }.getOrDefault(false)
        if (!ok) {
            done.countDown()
        }
        done.await(400, TimeUnit.MILLISECONDS)
    }
}

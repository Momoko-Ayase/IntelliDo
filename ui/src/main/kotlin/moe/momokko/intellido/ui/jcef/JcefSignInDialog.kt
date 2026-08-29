package moe.momokko.intellido.ui.jcef

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.transport.LinuxDoUrls
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.WindowConstants

/**
 * Modal JCEF host for the real LINUX DO login page. Shares the JSON fetcher's
 * JBCefClient so Cloudflare clearance and the session cookie stay in one jar.
 */
class JcefSignInDialog(
    private val locale: Locale,
) {
    fun awaitSession(): MemberSession? {
        val result = CompletableFuture<MemberSession?>()
        val show = Runnable { open(result) }
        if (ApplicationManager.getApplication().isDispatchThread) {
            show.run()
        } else {
            ApplicationManager.getApplication().invokeAndWait(show)
        }
        return try {
            result.get(10, TimeUnit.MINUTES)
        } catch (_: Exception) {
            null
        }
    }

    private fun open(result: CompletableFuture<MemberSession?>) {
        val page = LinuxDoUrls.ORIGIN + LinuxDoUrls.login()
        val shared = runCatching { service<IntelliDoRuntime>().jcefClient() }.getOrNull()
        val builder = JBCefBrowser.createBuilder()
            .setOffScreenRendering(false)
            .setUrl(page)
        if (shared != null) {
            builder.setClient(shared)
        }
        val browser = builder.build()
        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val done = AtomicBoolean(false)
        val owner = WindowManager.getInstance().findVisibleFrame()
        val dialog = JDialog(owner, IntelliDoStrings.message("signIn.title", locale), true)
        dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        val unofficial = JBLabel(IntelliDoStrings.message("signIn.unofficial", locale))
        unofficial.border = JBUI.Borders.empty(8, 16, 0, 16)
        unofficial.setAllowAutoWrapping(true)
        val origin = JBLabel(page)
        origin.border = JBUI.Borders.empty(4, 16, 8, 16)
        origin.foreground = origin.foreground.darker()
        origin.getAccessibleContext().accessibleName = IntelliDoStrings.message("signIn.origin", locale)
        val north = JPanel(BorderLayout())
        north.add(unofficial, BorderLayout.NORTH)
        north.add(origin, BorderLayout.SOUTH)

        val root = JPanel(BorderLayout())
        root.add(north, BorderLayout.NORTH)
        root.add(browser.component, BorderLayout.CENTER)
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(8)))
        val cancel = JButton(IntelliDoStrings.message("signIn.cancel", locale))
        cancel.addActionListener { dialog.dispose() }
        actions.add(cancel)
        root.add(actions, BorderLayout.SOUTH)
        dialog.contentPane = root
        dialog.size = Dimension(JBUI.scale(720), JBUI.scale(820))
        dialog.setLocationRelativeTo(owner)

        JcefBrowserGuards.install(
            browser,
            onExternal = { BrowserUtil.browse(it) },
            onConfirm = { url ->
                val ok = javax.swing.JOptionPane.showConfirmDialog(
                    dialog,
                    url,
                    IntelliDoStrings.message("browse.openExternal", locale),
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE,
                ) == javax.swing.JOptionPane.YES_OPTION
                if (ok) {
                    BrowserUtil.browse(url)
                }
            },
            nativeStaysInCef = true,
            authFlow = true,
        )

        fun finish(session: MemberSession?) {
            if (!done.compareAndSet(false, true)) {
                return
            }
            result.complete(session)
            ApplicationManager.getApplication().invokeLater {
                if (dialog.isDisplayable) {
                    dialog.dispose()
                }
            }
        }

        fun refreshOrigin() {
            val url = browser.cefBrowser.url.orEmpty().ifBlank { page }
            origin.text = url
        }

        query.addHandler { payload ->
            val session = SignInProbe.sessionFromPayload(payload)
            if (session is MemberSession.SignedIn) {
                ApplicationManager.getApplication().invokeLater { finish(session) }
            }
            JBCefJSQuery.Response("")
        }
        val script = PROBE_JS.replace("CALLBACK", query.inject("payload"))
        fun probeSession() {
            if (done.get() || !dialog.isDisplayable) {
                return
            }
            refreshOrigin()
            browser.cefBrowser.executeJavaScript(script, LinuxDoUrls.ORIGIN + "/", 0)
        }

        val timer = Timer(700) { probeSession() }
        timer.start()
        val loadHandler = object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: org.cef.browser.CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading && cefBrowser === browser.cefBrowser) {
                    ApplicationManager.getApplication().invokeLater { probeSession() }
                }
            }
        }
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
        dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent) {
                timer.stop()
                browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser)
                query.dispose()
                browser.dispose()
                finish(null)
            }
        })
        if (!JBCefApp.isSupported()) {
            finish(null)
            return
        }
        try {
            dialog.isVisible = true
        } finally {
            if (!done.get()) {
                finish(null)
            }
        }
    }

    companion object {
        const val PROBE_JS: String =
            "(function(){" +
                "var href=location.href;" +
                "function done(user){" +
                "var payload=JSON.stringify({href:href,user:user});" +
                "CALLBACK" +
                "}" +
                "var path=location.pathname||'';" +
                "if(/^\\/(login|signup|session\\/email-login|session\\/passkey|auth\\/)/.test(path)||path.indexOf('/u/login')===0){" +
                "done(null);return;" +
                "}" +
                "fetch('/session/current.json',{credentials:'include',headers:{'Accept':'application/json'}})" +
                ".then(function(r){return r.json();})" +
                ".then(function(j){" +
                "var u=j&&j.current_user;" +
                "done(u?{username:u.username,trust_level:u.trust_level,id:u.id,name:u.name,avatar_template:u.avatar_template}:null);" +
                "})" +
                ".catch(function(){done(null);});" +
                "})();"
    }
}

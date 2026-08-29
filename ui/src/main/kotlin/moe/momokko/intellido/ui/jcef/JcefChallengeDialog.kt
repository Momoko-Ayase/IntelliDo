package moe.momokko.intellido.ui.jcef

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.browser.CloudflareChallenge
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.transport.LinuxDoUrls
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

class JcefChallengeDialog(
    private val client: JBCefClient,
    private val locale: Locale,
) {
    fun awaitPassed() {
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "Cloudflare challenge must wait off the EDT"
        }
        val result = CompletableFuture<Boolean>()
        ApplicationManager.getApplication().invokeLater { open(result) }
        try {
            result.get(5, TimeUnit.MINUTES)
        } catch (error: Exception) {
            ApplicationManager.getApplication().invokeLater {
                result.completeExceptionally(error)
            }
            throw error
        }
    }

    private fun open(result: CompletableFuture<Boolean>) {
        val page = LinuxDoUrls.ORIGIN + "/"
        val browser = JBCefBrowser.createBuilder()
            .setClient(client)
            .setOffScreenRendering(false)
            .setCreateImmediately(true)
            .setUrl(page)
            .build()
        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val done = AtomicBoolean(false)
        val shown = AtomicBoolean(false)
        val owner = WindowManager.getInstance().findVisibleFrame()
        val dialog = JDialog(owner, IntelliDoStrings.message("challenge.title", locale), true)
        dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        val root = JPanel(BorderLayout())
        val explain = JBLabel(IntelliDoStrings.message("challenge.body", locale))
        explain.border = JBUI.Borders.empty(12, 16)
        explain.setAllowAutoWrapping(true)
        root.add(explain, BorderLayout.NORTH)
        root.add(browser.component, BorderLayout.CENTER)
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(8)))
        val cancel = JButton(IntelliDoStrings.message("challenge.cancel", locale))
        cancel.addActionListener { dialog.dispose() }
        actions.add(cancel)
        root.add(actions, BorderLayout.SOUTH)
        dialog.contentPane = root
        dialog.size = Dimension(JBUI.scale(560), JBUI.scale(760))
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
        )

        val script = PROBE_JS.replace("CALLBACK", query.inject("payload"))
        fun probe() {
            if (done.get()) {
                return
            }
            browser.cefBrowser.executeJavaScript(script, page, 0)
        }
        val loadHandler = object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: org.cef.browser.CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading && cefBrowser === browser.cefBrowser) {
                    ApplicationManager.getApplication().invokeLater { probe() }
                }
            }
        }
        lateinit var timer: Timer
        lateinit var revealTimer: Timer

        fun showWindow() {
            if (done.get() || !shown.compareAndSet(false, true)) {
                return
            }
            logger.info("Showing Cloudflare challenge dialog")
            dialog.isVisible = true
        }

        fun finish(passed: Boolean) {
            if (!done.compareAndSet(false, true)) {
                return
            }
            timer.stop()
            revealTimer.stop()
            runCatching { browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser) }
            runCatching { browser.jbCefClient.removeAllHandlers(browser.cefBrowser) }
            query.dispose()
            browser.dispose()
            if (passed) {
                result.complete(true)
            } else {
                result.completeExceptionally(IllegalStateException("cancelled"))
            }
            if (dialog.isDisplayable) {
                dialog.dispose()
            }
        }

        query.addHandler { payload ->
            val pageProbe = CloudflareChallenge.parsePageProbe(payload)
            val passed = CloudflareChallenge.dialogMayClose(pageProbe)
            if (passed) {
                logger.info(
                    "Challenge passed on ${pageProbe.url} passedJson=${pageProbe.passed} " +
                        "csrf=${CloudflareChallenge.isCsrfPassPayload(pageProbe.text)} " +
                        "shown=${shown.get()} turnstile=${pageProbe.turnstile}",
                )
                JcefLinuxDoCookies.flush()
                ApplicationManager.getApplication().invokeLater { finish(true) }
            } else if (pageProbe.turnstile) {
                ApplicationManager.getApplication().invokeLater { showWindow() }
            }
            JBCefJSQuery.Response("")
        }
        timer = Timer(400) { probe() }
        timer.start()
        revealTimer = Timer(1_800) { showWindow() }
        revealTimer.isRepeats = false
        revealTimer.start()
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
        dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent) {
                finish(false)
            }
        })
        probe()
    }

    companion object {
        private val logger = Logger.getInstance(JcefChallengeDialog::class.java)
        private val flight = JcefSingleFlight()

        /**
         * One visible challenge at a time. Extra callers wait for the in-flight
         * dialog instead of stacking more modal windows on the nested EDT pump.
         */
        fun awaitPassed(client: JBCefClient, locale: Locale) {
            flight.run {
                JcefChallengeDialog(client, locale).awaitPassed()
            }
        }

        const val PROBE_JS: String =
            "(function(){" +
                "if(window.__idcfBusy){return;}" +
                "window.__idcfBusy=true;" +
                "setTimeout(function(){window.__idcfBusy=false;},1600);" +
                "var href=location.href;" +
                "function visible(el){return !!(el&&el.offsetWidth>0&&el.offsetHeight>0);}" +
                "function hasTurnstile(){" +
                "var nodes=document.querySelectorAll('.cf-turnstile,#cf-turnstile,iframe[src*=\"challenges.cloudflare.com\"]');" +
                "for(var i=0;i<nodes.length;i++){if(visible(nodes[i])){return true;}}" +
                "return false;" +
                "}" +
                "function send(flag,extra){" +
                "window.__idcfBusy=false;" +
                "var payload=flag+'::'+href+'::'+String(extra||'');" +
                "CALLBACK" +
                "}" +
                "var ac=new AbortController();" +
                "setTimeout(function(){try{ac.abort();}catch(e){}},1500);" +
                "fetch('/session/csrf',{credentials:'include',headers:{" +
                "'Accept':'application/json, text/plain, */*'," +
                "'X-Requested-With':'XMLHttpRequest'" +
                "},signal:ac.signal})" +
                ".then(function(r){" +
                "var mitigated=String(r.headers.get('cf-mitigated')||'').toLowerCase();" +
                "var ok=r.status>=200&&r.status<300;" +
                "return r.text().then(function(t){return {ok:ok,mitigated:mitigated,t:String(t||'')};});" +
                "})" +
                ".then(function(x){" +
                "if(hasTurnstile()){send('turnstile',x.t.slice(0,80));return;}" +
                "if(x.mitigated.indexOf('challenge')>=0){send('wait',x.t.slice(0,80));return;}" +
                "var body=x.t.replace(/^\\s+/,'');" +
                "if(x.ok&&body.charAt(0)==='{'&&body.indexOf('\"csrf\"')>=0){send('passed',body.slice(0,80));return;}" +
                "send('wait',x.t.slice(0,80));" +
                "})" +
                ".catch(function(){send(hasTurnstile()?'turnstile':'wait','');});" +
                "})();"
    }
}

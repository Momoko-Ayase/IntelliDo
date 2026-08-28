package moe.momokko.intellido.ui.jcef

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
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
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
        val result = CompletableFuture<Boolean>()
        val show = Runnable { open(result) }
        if (ApplicationManager.getApplication().isDispatchThread) {
            show.run()
        } else {
            ApplicationManager.getApplication().invokeLater(show)
        }
        result.get(5, TimeUnit.MINUTES)
    }

    private fun open(result: CompletableFuture<Boolean>) {
        val page = LinuxDoUrls.ORIGIN + "/"
        val browser = JBCefBrowser.createBuilder()
            .setClient(client)
            .setOffScreenRendering(false)
            .setUrl(page)
            .build()
        browser.setOpenLinksInExternalBrowser(false)
        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val done = AtomicBoolean(false)
        val sawTurnstile = AtomicBoolean(false)
        val owner = WindowManager.getInstance().findVisibleFrame()
        val dialog = JDialog(owner, IntelliDoStrings.message("challenge.title", locale), true)
        dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        // Explain why an unexpected verification window appeared, and offer an
        // explicit way out instead of only the window close button.
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
        query.addHandler { payload ->
            val probe = CloudflareChallenge.parsePageProbe(payload)
            if (probe.turnstile) {
                sawTurnstile.set(true)
            }
            val passed = CloudflareChallenge.dialogMayClose(probe, sawTurnstile.get())
            if (passed && done.compareAndSet(false, true)) {
                logger.info("Challenge passed on ${probe.url} ready=${probe.ready} sawTurnstile=${sawTurnstile.get()}")
                result.complete(true)
                dialog.dispose()
            }
            JBCefJSQuery.Response("")
        }
        val script = PROBE_JS.replace("CALLBACK", query.inject("payload"))
        fun probe() {
            if (done.get() || !dialog.isDisplayable) {
                return
            }
            browser.cefBrowser.executeJavaScript(script, page, 0)
        }
        val timer = Timer(100) { probe() }
        timer.start()
        val loadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading && cefBrowser === browser.cefBrowser) {
                    ApplicationManager.getApplication().invokeLater { probe() }
                }
            }
        }
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
        dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent) {
                timer.stop()
                browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser)
                query.dispose()
                if (done.compareAndSet(false, true)) {
                    result.completeExceptionally(IllegalStateException("cancelled"))
                }
            }
        })
        dialog.isVisible = true
    }

    companion object {
        private val logger = Logger.getInstance(JcefChallengeDialog::class.java)

        const val PROBE_JS: String =
            "(function(){" +
                "var href=location.href;" +
                "function visible(el){return !!(el&&el.offsetWidth>0&&el.offsetHeight>0);}" +
                "var nodes=document.querySelectorAll('.cf-turnstile,#cf-turnstile,iframe[src*=\"challenges.cloudflare.com\"]');" +
                "var turnstile=false;" +
                "for(var i=0;i<nodes.length;i++){if(visible(nodes[i])){turnstile=true;break;}}" +
                "var chrome=!!document.querySelector('#site-logo,.d-header,#main-outlet,#site-text-logo,.d-header-wrap,.topic-list,table.topic-list,.login-button,button.login-button,.list-controls,.alert-info');" +
                "var text=(document.body&&document.body.innerText)?document.body.innerText.slice(0,400):'';" +
                "var flag=chrome?'ready':(turnstile?'turnstile':'wait');" +
                "var payload=flag+'::'+href+'::'+text;" +
                "CALLBACK" +
                "})();"
    }
}

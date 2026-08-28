package moe.momokko.intellido.ui.browse

import com.intellij.ui.jcef.JBCefBrowser
import moe.momokko.intellido.domain.browse.TrustedOrigins
import moe.momokko.intellido.domain.topic.DiscourseLink
import moe.momokko.intellido.ui.jcef.JcefBrowserGuards
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent

class JcefBrowseView(
    private val onNative: (DiscourseLink) -> Boolean,
    private val onExternal: (String) -> Unit,
    private val onConfirm: (String) -> Unit,
    private val onDownload: (String, String) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onLocation: () -> Unit = {},
) : BrowseView {
    private val browser: JBCefBrowser = JBCefBrowser.createBuilder()
        .setOffScreenRendering(false)
        .setCreateImmediately(true)
        .build()

    init {
        JcefBrowserGuards.install(
            browser,
            onNative = onNative,
            onExternal = onExternal,
            onConfirm = onConfirm,
            onDownload = onDownload,
            onCopy = onCopy,
        )
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    cefBrowser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    if (!isLoading) {
                        onLocation()
                    }
                }
            },
            browser.cefBrowser,
        )
    }

    override fun component(): JComponent = browser.component

    override fun load(url: String) {
        browser.loadURL(url)
    }

    override fun goBack() {
        browser.cefBrowser.goBack()
    }

    override fun goForward() {
        browser.cefBrowser.goForward()
    }

    override fun reload() {
        browser.cefBrowser.reload()
    }

    override fun canGoBack(): Boolean = browser.cefBrowser.canGoBack()

    override fun canGoForward(): Boolean = browser.cefBrowser.canGoForward()

    override fun currentUrl(): String = browser.cefBrowser.url.orEmpty()

    override fun currentOrigin(): String = TrustedOrigins.originOf(currentUrl()).orEmpty()

    override fun dispose() {
        browser.jbCefClient.removeAllHandlers(browser.cefBrowser)
        browser.dispose()
    }
}

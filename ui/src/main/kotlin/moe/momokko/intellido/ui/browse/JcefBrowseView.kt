package moe.momokko.intellido.ui.browse

import com.intellij.ui.jcef.JBCefBrowser
import moe.momokko.intellido.domain.browse.BrowseDecision
import moe.momokko.intellido.domain.browse.BrowseRouter
import moe.momokko.intellido.domain.browse.TrustedOrigins
import moe.momokko.intellido.domain.content.Attachments
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.handler.CefDownloadHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import javax.swing.JComponent

class JcefBrowseView(
    private val onNative: (moe.momokko.intellido.domain.topic.DiscourseLink) -> Boolean,
    private val onExternal: (String) -> Unit,
    private val onDownload: (String, String) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onLocation: () -> Unit = {},
) : BrowseView {
    private val browser: JBCefBrowser = JBCefBrowser.createBuilder()
        .setOffScreenRendering(false)
        .setCreateImmediately(true)
        .build()

    init {
        browser.setOpenLinksInExternalBrowser(false)
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
        browser.jbCefClient.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    cefBrowser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    val url = request.url ?: return false
                    if (!frame.isMain) {
                        return !BrowseRouter.shouldLoadInCef(url, mainFrame = false)
                    }
                    if (Attachments.isAttachmentUrl(url)) {
                        onDownload(url, Attachments.suggestedName(url))
                        return true
                    }
                    return when (val decision = BrowseRouter.decide(url)) {
                        is BrowseDecision.Native -> onNative(decision.link)
                        is BrowseDecision.InApp -> false
                        is BrowseDecision.External -> {
                            onExternal(decision.url)
                            true
                        }
                        is BrowseDecision.Confirm -> {
                            onExternal(decision.url)
                            true
                        }
                        is BrowseDecision.CopyOnly -> {
                            onCopy(decision.url)
                            true
                        }
                        BrowseDecision.Blocked -> true
                    }
                }
            },
            browser.cefBrowser,
        )
        browser.jbCefClient.addDownloadHandler(
            object : CefDownloadHandlerAdapter() {
                override fun onBeforeDownload(
                    cefBrowser: CefBrowser,
                    downloadItem: org.cef.callback.CefDownloadItem,
                    suggestedName: String,
                    callback: CefBeforeDownloadCallback,
                ): Boolean {
                    // Never call callback.Continue: an empty path with no dialog
                    // makes CEF write to its default download directory without
                    // consent. Downloads only happen through the explicit Save
                    // dialog that onDownload opens (ADR 0032).
                    val url = downloadItem.url ?: return true
                    onDownload(url, suggestedName.ifBlank { Attachments.suggestedName(url) })
                    return true
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
        browser.dispose()
    }
}

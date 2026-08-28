package moe.momokko.intellido.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefBrowser
import moe.momokko.intellido.domain.topic.DiscourseLink
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.handler.CefDownloadHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest

/**
 * Request, download, and popup handlers shared by the in-app tab, the challenge
 * dialog, and the off-screen JSON fetcher. Never call CefBeforeDownloadCallback.Continue.
 */
object JcefBrowserGuards {
    fun install(
        browser: JBCefBrowser,
        onNative: (DiscourseLink) -> Boolean = { true },
        onExternal: (String) -> Unit = {},
        onConfirm: (String) -> Unit = {},
        onDownload: (String, String) -> Unit = { _, _ -> },
        onCopy: (String) -> Unit = {},
        pinLinuxDo: Boolean = false,
        nativeStaysInCef: Boolean = false,
    ) {
        browser.setOpenLinksInExternalBrowser(false)
        fun dispatch(url: String, mainFrame: Boolean, fromPopup: Boolean): Boolean {
            val action = JcefNavigation.decide(url, mainFrame, pinLinuxDo, nativeStaysInCef)
            return when (action) {
                JcefNav.Allow -> {
                    if (fromPopup) {
                        onEdt { browser.loadURL(url) }
                    }
                    fromPopup
                }
                JcefNav.Block -> true
                is JcefNav.Native -> {
                    onEdt { onNative(action.link) }
                    true
                }
                is JcefNav.External -> {
                    onEdt { onExternal(action.url) }
                    true
                }
                is JcefNav.Confirm -> {
                    onEdt { onConfirm(action.url) }
                    true
                }
                is JcefNav.Copy -> {
                    onEdt { onCopy(action.url) }
                    true
                }
                is JcefNav.Download -> {
                    onEdt { onDownload(action.url, action.name) }
                    true
                }
            }
        }
        val cef = browser.cefBrowser
        browser.jbCefClient.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    cefBrowser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    val url = request.url ?: return true
                    return dispatch(url, frame.isMain, fromPopup = false)
                }
            },
            cef,
        )
        browser.jbCefClient.addDownloadHandler(
            object : CefDownloadHandlerAdapter() {
                override fun onBeforeDownload(
                    cefBrowser: CefBrowser,
                    downloadItem: org.cef.callback.CefDownloadItem,
                    suggestedName: String,
                    callback: CefBeforeDownloadCallback,
                ): Boolean {
                    val url = downloadItem.url ?: return true
                    when (val action = JcefNavigation.download(url, suggestedName)) {
                        is JcefNav.Download -> onEdt { onDownload(action.url, action.name) }
                        else -> Unit
                    }
                    return true
                }
            },
            cef,
        )
        browser.jbCefClient.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    cefBrowser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String,
                    targetFrameName: String,
                ): Boolean {
                    dispatch(targetUrl, mainFrame = true, fromPopup = true)
                    return true
                }
            },
            cef,
        )
    }

    private fun onEdt(work: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDispatchThread) {
            work()
        } else {
            app.invokeLater(work)
        }
    }
}

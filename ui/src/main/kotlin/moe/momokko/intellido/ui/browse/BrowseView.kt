package moe.momokko.intellido.ui.browse

import javax.swing.JComponent

interface BrowseView {
    fun component(): JComponent
    fun load(url: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun currentUrl(): String
    fun currentOrigin(): String
    fun dispose()
}

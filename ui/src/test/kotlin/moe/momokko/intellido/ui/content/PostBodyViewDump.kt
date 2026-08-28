package moe.momokko.intellido.ui.content

import moe.momokko.intellido.domain.content.CookedHtml
import moe.momokko.intellido.domain.content.CookedHtmlParser
import java.awt.Dimension

fun dumpPostBodyViews(html: String, width: Int = 960): String {
    val pane = PostBodyPane(html)
    pane.size = Dimension(width, 800)
    pane.doLayout()
    pane.preferredSize
    return dumpPostBodyViews(pane)
}

fun dumpPostBodyViews(pane: PostBodyPane): String = pane.dumpLayout()

fun cooked(html: String): String = CookedHtml.toSafeHtml(CookedHtmlParser().parse(html))

package moe.momokko.intellido.ui.content

import com.intellij.ui.components.JBPanel
import moe.momokko.intellido.domain.content.CookedDocument
import java.awt.BorderLayout
import java.util.Locale

/** Compatibility wrapper around the single native cooked document renderer. */
class CookedDocumentPanel(
    document: CookedDocument,
    @Suppress("UNUSED_PARAMETER") locale: Locale,
) : JBPanel<CookedDocumentPanel>(BorderLayout()) {
    init {
        isOpaque = false
        add(PostBodyPane(document), BorderLayout.CENTER)
    }
}

package moe.momokko.intellido.ui.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import moe.momokko.intellido.domain.search.SearchHit
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

class LinuxDoSearchEverywhereContributorFactory : SearchEverywhereContributorFactory<SearchHit> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<SearchHit> =
        LinuxDoSearchEverywhereContributor(initEvent.project)
}

class LinuxDoSearchEverywhereContributor(
    private val project: Project?,
) : SearchEverywhereContributor<SearchHit> {
    private val locale = runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(java.util.Locale.getDefault())

    override fun getSearchProviderId(): String = "IntelliDo.Public"

    override fun getGroupName(): String = IntelliDoStrings.message("search.everywhere.group", locale)

    override fun getSortWeight(): Int = 400

    override fun showInFindResults(): Boolean = false

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in SearchHit>,
    ) {
        val needle = pattern.trim()
        if (project == null || needle.length < MIN_QUERY || progressIndicator.isCanceled) {
            return
        }
        // Every keystroke lands here and each search is a blocking JCEF fetch, so
        // settle first and drop the request if the member kept typing.
        if (!settle(progressIndicator)) {
            return
        }
        val hits = runCatching { service<IntelliDoRuntime>().communityClient.searchHits(needle) }
            .getOrDefault(emptyList())
        hits.forEach { hit ->
            if (progressIndicator.isCanceled || !consumer.process(hit)) {
                return
            }
        }
    }

    private fun settle(progressIndicator: ProgressIndicator): Boolean {
        var waited = 0L
        while (waited < DEBOUNCE_MS) {
            if (progressIndicator.isCanceled) {
                return false
            }
            Thread.sleep(DEBOUNCE_STEP_MS)
            waited += DEBOUNCE_STEP_MS
        }
        return !progressIndicator.isCanceled
    }

    override fun processSelectedItem(selected: SearchHit, modifiers: Int, searchText: String): Boolean {
        val current = project ?: return false
        ApplicationManager.getApplication().invokeLater {
            IntelliDoWorkspace.openTopic(current, selected.topicId)
        }
        return true
    }

    override fun getElementsRenderer(): ListCellRenderer<in SearchHit> =
        object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val hit = value as? SearchHit
                val kind = if (hit?.postNumber != null) {
                    IntelliDoStrings.message("search.everywhere.post", locale)
                } else {
                    IntelliDoStrings.message("search.everywhere.topic", locale)
                }
                val text = if (hit == null) {
                    ""
                } else {
                    "$kind  ${hit.title}" + if (hit.blurb.isNotBlank()) " — ${hit.blurb}" else ""
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }

    @Deprecated("Use getDataForItem via DataProvider instead")
    override fun getDataForItem(element: SearchHit, dataId: String): Any? = null

    companion object {
        const val MIN_QUERY: Int = 2
        const val DEBOUNCE_MS: Long = 250
        private const val DEBOUNCE_STEP_MS: Long = 25
    }
}

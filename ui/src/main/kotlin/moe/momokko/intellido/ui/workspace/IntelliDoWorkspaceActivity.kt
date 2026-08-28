package moe.momokko.intellido.ui.workspace

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class IntelliDoWorkspaceActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        IntelliDoWorkspace.configure(project)
    }
}

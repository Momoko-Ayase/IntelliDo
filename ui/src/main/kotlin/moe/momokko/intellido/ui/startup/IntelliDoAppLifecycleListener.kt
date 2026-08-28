package moe.momokko.intellido.ui.startup

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

class IntelliDoAppLifecycleListener : AppLifecycleListener {
    override fun appStarted() {
        ApplicationManager.getApplication().invokeLater {
            IntelliDoStartup.launch()
        }
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        runCatching { service<IntelliDoRuntime>().shutdown() }
    }
}

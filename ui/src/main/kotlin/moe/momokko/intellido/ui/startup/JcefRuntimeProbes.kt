package moe.momokko.intellido.ui.startup

import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.browser.JcefProbeResult
import moe.momokko.intellido.browser.JcefRuntimeProbe

object JcefRuntimeProbes {
    fun create(): JcefRuntimeProbe =
        if (jcefClassesVisible()) {
            IntelliJJcefRuntimeProbe()
        } else {
            JcefRuntimeProbe {
                JcefProbeResult.Unavailable(
                    JcefDiagnostics.capture(
                        "JCEF classes are not visible to IntelliDo. Declare a dependency on com.intellij.modules.jcef / intellij.platform.ui.jcef.",
                        jcefReportedSupported = false,
                    ),
                )
            }
        }

    fun jcefClassesVisible(): Boolean =
        try {
            Class.forName(
                "com.intellij.ui.jcef.JBCefApp",
                false,
                JcefRuntimeProbes::class.java.classLoader,
            )
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        }
}

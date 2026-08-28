package moe.momokko.intellido.ui.startup

import com.intellij.ui.jcef.JBCefApp
import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.browser.JcefProbeResult
import moe.momokko.intellido.browser.JcefRuntimeProbe

class IntelliJJcefRuntimeProbe : JcefRuntimeProbe {
    override fun probe(): JcefProbeResult {
        return try {
            val supported = JBCefApp.isSupported()
            if (!supported) {
                JcefProbeResult.Unavailable(
                    JcefDiagnostics.capture("JBCefApp.isSupported returned false", false),
                )
            } else {
                JcefProbeResult.Available
            }
        } catch (error: Throwable) {
            JcefProbeResult.Unavailable(
                JcefDiagnostics.capture(error.toString(), jcefReportedSupported = false),
            )
        }
    }
}

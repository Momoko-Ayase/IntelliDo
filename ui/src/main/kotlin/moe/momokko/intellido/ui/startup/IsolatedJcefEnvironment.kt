package moe.momokko.intellido.ui.startup

import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.cef.JCefAppConfig
import moe.momokko.intellido.browser.IsolatedBrowserProfile
import moe.momokko.intellido.browser.IsolatedBrowserProfiles
import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.browser.JcefIsolationResult

/**
 * Applies the isolated anonymous JCEF profile before CEF starts.
 * Never installs a JVM HTTP transport.
 */
object IsolatedJcefEnvironment {
    fun install(profile: IsolatedBrowserProfile): JcefIsolationResult {
        if (IsolatedBrowserProfiles.isForbiddenSystemBrowserPath(profile.userDataDirectory)) {
            return JcefIsolationResult.Failed(
                JcefDiagnostics.capture(
                    "Refusing to start JCEF with a system browser profile path",
                    jcefReportedSupported = JBCefApp.isSupported(),
                ),
            )
        }
        val settings = IsolatedBrowserProfiles.settingsFor(profile)
        IsolatedBrowserProfiles.jvmOverrides(settings).forEach { (key, value) ->
            System.setProperty(key, value)
        }
        return try {
            val cefSettings = JCefAppConfig.getInstance().cefSettings
            cefSettings.cache_path = settings.cachePath.toAbsolutePath().toString()
            cefSettings.persist_session_cookies = settings.persistSessionCookies
            cefSettings.locale = settings.locale
            if (!JBCefApp.isSupported()) {
                JcefIsolationResult.Failed(
                    JcefDiagnostics.capture("JBCefApp.isSupported returned false during isolation", false),
                )
            } else {
                JBCefApp.getInstance()
                JcefIsolationResult.Isolated
            }
        } catch (error: Throwable) {
            JcefIsolationResult.Failed(
                JcefDiagnostics.capture(error.toString(), jcefReportedSupported = false),
            )
        }
    }
}

package moe.momokko.intellido.ui.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.PathManager
import moe.momokko.intellido.browser.IsolatedBrowserProfiles
import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.browser.JcefRuntimeProbe
import moe.momokko.intellido.browser.JcefStartupDecision
import moe.momokko.intellido.browser.JcefStartupGate
import moe.momokko.intellido.platform.i18n.FileLocalPreferenceStore
import moe.momokko.intellido.platform.i18n.IntelliDoLocale
import moe.momokko.intellido.platform.identity.ProductIdentity
import moe.momokko.intellido.platform.instance.ApplicationInstanceCoordinator
import moe.momokko.intellido.platform.instance.InstanceAcquireResult
import moe.momokko.intellido.platform.instance.LaunchTargets
import moe.momokko.intellido.ui.recovery.JcefRecoveryFrame
import moe.momokko.intellido.ui.surface.IdeSurfaceApplicator
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.nio.file.Path
import java.util.Locale
import javax.swing.JOptionPane
import kotlin.io.path.Path

object IntelliDoStartup {
    private val logger = Logger.getInstance(IntelliDoStartup::class.java)

    @Volatile
    private var bootstrapped: Boolean = false

    fun launch(probe: JcefRuntimeProbe = JcefRuntimeProbes.create()) {
        logger.info("IntelliDo launch starting")
        if (!bootstrapped) {
            if (!bootstrap()) {
                return
            }
            bootstrapped = true
        }
        present(probe)
    }

    fun focusHome() {
        IntelliDoWorkspace.openOrFocus()
    }

    private fun bootstrap(): Boolean {
        val identity = ProductIdentity.fromSystem()
        val preferences = FileLocalPreferenceStore(preferenceFile())
        val locale = IntelliDoLocale(preferences).resolve(Locale.getDefault())
        Locale.setDefault(locale)

        val coordinator = ApplicationInstanceCoordinator(
            lockDirectory = Path(System.getProperty("user.home"), ".intellido", "instance"),
            channel = identity.channel,
            processId = ProcessHandle.current().pid(),
        )
        // Single-instance arbitration must never be the reason IntelliDo fails to
        // present: an unreadable lock directory degrades to "run without a lock".
        val acquire = runCatching { coordinator.tryAcquire(LaunchTargets.parse(emptyList())) }
            .onFailure { error -> logger.warn("Instance lock unavailable; continuing unlocked", error) }
            .getOrNull()
        if (acquire == null) {
            val runtime = service<IntelliDoRuntime>()
            runtime.initialize(identity, locale, null, preferences, coordinator)
            IdeSurfaceApplicator.applyApplicationSurface()
            return true
        }
        return when (acquire) {
            is InstanceAcquireResult.AwakeExisting -> {
                ApplicationManager.getApplication().exit()
                false
            }
            is InstanceAcquireResult.OtherChannelRunning,
            InstanceAcquireResult.Busy,
            -> {
                JOptionPane.showMessageDialog(
                    null,
                    "IntelliDo 同一操作系统用户只能运行一个进程（稳定版与 Nightly 互斥）。",
                    identity.visibleProductName,
                    JOptionPane.WARNING_MESSAGE,
                )
                ApplicationManager.getApplication().exit()
                false
            }
            is InstanceAcquireResult.Acquired -> {
                val runtime = service<IntelliDoRuntime>()
                runtime.initialize(identity, locale, acquire.lock, preferences, coordinator)
                runtime.startHandoffWatcher()
                IdeSurfaceApplicator.applyApplicationSurface()
                true
            }
        }
    }

    private fun present(probe: JcefRuntimeProbe) {
        val runtime = service<IntelliDoRuntime>()
        try {
            val profile = IsolatedBrowserProfiles.prepareAnonymous(
                Path(PathManager.getSystemPath()),
                runtime.identity.channel,
            )
            runtime.attachBrowserProfile(profile)
            val decision = JcefStartupGate(
                probe = probe,
                isolation = {
                    IsolatedJcefEnvironment.install(profile)
                },
            ).decide()
            when (decision) {
                JcefStartupDecision.ContinueWithJcef -> {
                    if (!runtime.preferFakeTransport()) {
                        runtime.attachLiveCommunity(moe.momokko.intellido.ui.jcef.JcefLinuxDoJsonFetcher())
                        logger.info("Guest mode using JCEF transport to https://linux.do")
                    } else {
                        logger.info("Guest mode using local Fake LINUX DO")
                    }
                    logger.info("JCEF available with isolated profile; opening IntelliJ workspace with Home tab")
                    val project = IntelliDoWorkspace.openOrFocus()
                    if (project == null) {
                        logger.warn("IntelliDo workspace did not open")
                    }
                }
                is JcefStartupDecision.ShowRecovery -> {
                    runtime.lastJcefDiagnostics = decision.diagnostics
                    JcefRecoveryFrame(
                        diagnostics = decision.diagnostics,
                        locale = runtime.locale,
                        onRetry = { present(probe) },
                    ).showRecovery()
                }
            }
        } catch (error: Exception) {
            val diagnostics = JcefDiagnostics.capture(error.toString(), jcefReportedSupported = false)
            runtime.lastJcefDiagnostics = diagnostics
            JcefRecoveryFrame(
                diagnostics = diagnostics,
                locale = runtime.locale,
                onRetry = { present(probe) },
            ).showRecovery()
        }
    }

    fun preferenceFile(): Path =
        Path(System.getProperty("user.home"), ".intellido", "application.properties")
}
